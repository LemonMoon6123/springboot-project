package com.scaffold.agent;

import com.scaffold.dto.AiChatRequest;
import com.scaffold.dto.AiGuideResponse;
import com.scaffold.entity.SysConfig;
import com.scaffold.mapper.SysConfigMapper;
import com.scaffold.service.AiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MallAgentService {

    private static final Logger log = LoggerFactory.getLogger(MallAgentService.class);

    private static final String SYSTEM_PROMPT =
            """
            你是智购商城的 AI 导购 Agent，结合工具与知识库帮助用户选购、加购、下单与答疑。
                规则：
                1. 导购时必须先调用 searchProducts（keyword 用商品核心词，如卫衣、耳机、台灯），再用 2-4 句中文说明推荐理由，不要长篇罗列商品清单（前端会展示卡片）。
                2. 若消息中已附带「站内检索结果」，必须基于这些真实商品推荐，禁止说「搜不到」「没有卫衣/耳机」。
                3. 加购、查购物车、查地址、下单、支付必须调用对应工具；未登录时引导用户登录。
                4. 结算优先用 checkoutCart：先 confirmed=false 预览；用户说「确认」「确认下单」「可以」后，再 checkoutCart(confirmed=true)。
                5. 支付：用户确认后 payOrder(confirmed=true)，orderId 可省略。
                6. 必须结合对话历史理解短回复；金额、订单号原样保留工具返回值。
                7. 售后/物流优先依据知识库；不要编造商品ID、订单号或价格。
            """;

    @Autowired
    private MallAgentTools mallAgentTools;
    @Autowired
    private VectorStore vectorStore;
    @Autowired
    private SysConfigMapper sysConfigMapper;
    @Autowired
    private AiService aiService;

    // 读取 app.ai.enabled，有就用配置值；没有就用 false。
    // 现在 yml 里写了 true，所以最终 aiEnabled 就是 true。
    // 后面的 :false 只是防止配置缺失时启动报错，并给一个默认值。
    @Value("${app.ai.enabled:false}")
    private boolean aiEnabled;
    @Value("${app.ai.api-url:https://api.deepseek.com/chat/completions}")
    private String apiUrl;
    @Value("${app.ai.api-key:}")
    private String apiKey;
    @Value("${app.ai.model:deepseek-chat}")
    private String model;

    private final ConcurrentHashMap<String, ChatModel> chatModelCache = new ConcurrentHashMap<>();

    public AiGuideResponse chat(String message, Long userId) {
        return chat(message, userId, null);
    }

    public AiGuideResponse chat(String message, Long userId, List<AiChatRequest.AiChatTurn> history) {
        String msg = message == null ? "" : message.trim();
        if (!StringUtils.hasText(msg)) {
            return AiGuideResponse.of("请告诉我你想买什么，或直接说「查看购物车」「帮我下单」。", null);
        }

        AgentContext ctx = AgentContext.open(userId);
        try {
            // 先尝试获取api_key,现在这里因为数据库中没有api_key对应的config_value值，所以会采用默认的api_key，
            // 而默认的api_key会从环境变量中取。
            if (!isAiReady()) {
                // 降级:AiService.guide() 关键词规则导购
                AiGuideResponse fallback = aiService.guide(msg);

                if (looksLikeCartOrOrder(msg)) {
                    fallback.setReply((fallback.getReply() == null ? "" : fallback.getReply() + "\n\n")
                            + "购物车/下单需要配置 AI 密钥后由 Agent 代办；你也可以打开「购物车」「我的订单」手动操作。");
                    fallback.addAction("cart", "购物车", "/user/cart");
                    fallback.addAction("orders", "我的订单", "/user/orders");
                }
                return fallback;
            }

            // 用户明确确认时，直接走工具，避免模型丢上下文只回预览不落单
            if (userId != null && isConfirmPay(msg)) {
                String toolReply = mallAgentTools.payOrder(null, true);
                return toResponse(ctx, toolReply);
            }
            if (userId != null && isConfirmOrder(msg)) {
                String toolReply = mallAgentTools.checkoutCart(null, null, true);
                if (toolReply != null && (toolReply.contains("购物车是空的") || toolReply.contains("结算失败"))) {
                    // 购物车已空时，尝试支付最近待支付订单
                    String payReply = mallAgentTools.payOrder(null, true);
                    if (payReply != null && !payReply.contains("没有待支付") && !payReply.contains("支付失败")) {
                        return toResponse(ctx, payReply);
                    }
                }
                return toResponse(ctx, toolReply);
            }
            if (userId != null && isCheckoutIntent(msg)) {
                String toolReply = mallAgentTools.checkoutCart(null, null, false);
                return toResponse(ctx, "好的，先给你结算预览：\n\n" + toolReply);
            }

            // 导购意图：先本地搜品；命中则直接返回卡片，避免模型漏调工具或胡说「搜不到」
            if (isProductGuideIntent(msg)) {
                Double maxPrice = extractMaxPrice(msg);
                String found = mallAgentTools.searchProducts(msg, maxPrice, 6);
                if (!ctx.getProducts().isEmpty()) {
                    String reply = polishGuideReply(msg, ctx.getProducts());
                    return toResponse(ctx, reply);
                }
            }

            ChatClient client = ChatClient.builder(resolveChatModel())
                    .defaultSystem(SYSTEM_PROMPT)
                    .defaultTools(mallAgentTools)
                    .defaultAdvisors(QuestionAnswerAdvisor.builder(vectorStore)
                            .searchRequest(SearchRequest.builder().topK(4).similarityThreshold(0.35).build())
                            .build())
                    .build();

            List<Message> messages = buildMessages(history, msg);
            String reply = client.prompt()
                    .messages(messages)
                    .call()
                    .content();

            if (!StringUtils.hasText(reply)) {
                reply = "我已处理你的请求，请查看下方推荐或快捷入口。";
            }

            return toResponse(ctx, reply.trim());
        } catch (Exception e) {
            log.warn("Agent 调用失败，回退普通导购: {}", e.getMessage());
            AiGuideResponse fallback = aiService.guide(msg);
            fallback.setReply((fallback.getReply() == null ? "" : fallback.getReply() + "\n\n")
                    + "（Agent 暂时繁忙，已切换为普通导购。）");
            return fallback;
        } finally {
            AgentContext.clear();
        }
    }

    private AiGuideResponse toResponse(AgentContext ctx, String reply) {
        AiGuideResponse response = new AiGuideResponse();
        response.setReply(reply);
        response.setProducts(ctx.getProducts());
        response.setCartItems(ctx.getCartItems());
        response.setOrders(ctx.getOrders());
        response.setActions(ctx.getActions());
        response.setNeedLogin(ctx.isNeedLogin());
        return response;
    }

    private List<Message> buildMessages(List<AiChatRequest.AiChatTurn> history, String currentUserMessage) {
        List<Message> messages = new ArrayList<>();
        if (history != null) {
            int from = Math.max(0, history.size() - 12);
            for (int i = from; i < history.size(); i++) {
                AiChatRequest.AiChatTurn turn = history.get(i);
                if (turn == null || !StringUtils.hasText(turn.getContent())) {
                    continue;
                }
                String role = turn.getRole() == null ? "" : turn.getRole().trim().toLowerCase(Locale.ROOT);
                String content = turn.getContent().trim();
                if (content.length() > 1200) {
                    content = content.substring(0, 1200);
                }
                if ("user".equals(role)) {
                    messages.add(new UserMessage(content));
                } else if ("assistant".equals(role)) {
                    messages.add(new AssistantMessage(content));
                }
            }
        }
        messages.add(new UserMessage(currentUserMessage));
        return messages;
    }

    private boolean isConfirmOrder(String msg) {
        String m = msg.replaceAll("\\s+", "");
        return m.equals("确认") || m.equals("确认下单") || m.equals("就这个") || m.equals("好的确认")
                || m.equals("确认购买") || m.contains("确认下单") || m.contains("确认购买")
                || (m.contains("确认") && (m.contains("下单") || m.contains("结算") || m.contains("购买")));
    }

    private boolean isConfirmPay(String msg) {
        String m = msg.replaceAll("\\s+", "");
        return m.equals("确认支付") || m.contains("确认支付") || m.equals("去支付") || m.equals("立即支付")
                || (m.contains("确认") && m.contains("支付"));
    }

    private boolean isCheckoutIntent(String msg) {
        String m = msg.replaceAll("\\s+", "");
        return m.contains("结算购物车") || m.contains("帮我结算") || m.contains("帮我下单")
                || m.contains("一键下单") || m.contains("提交订单") || m.contains("现在下单")
                || m.equals("结算") || m.equals("下单") || m.equals("结账") || m.contains("帮我结账");
    }

    private boolean isProductGuideIntent(String msg) {
        if (!StringUtils.hasText(msg) || looksLikeCartOrOrder(msg)) {
            return false;
        }
        String m = msg.replaceAll("\\s+", "");
        if (m.contains("售后") || m.contains("退货") || m.contains("退款") || m.contains("物流")
                || m.contains("发货") || m.contains("怎么退") || m.contains("积分") || m.contains("优惠券")) {
            // 政策问答为主时不强行搜品；但「推荐耳机」类仍要搜
            if (!(m.contains("推荐") || m.contains("找") || m.contains("买") || m.contains("有没有"))) {
                return false;
            }
        }
        return m.contains("推荐") || m.contains("找") || m.contains("买") || m.contains("有没有")
                || m.contains("看看") || m.contains("想要") || m.contains("想买")
                || !MallAgentTools.expandSearchTokens(msg).isEmpty();
    }

    private Double extractMaxPrice(String msg) {
        if (!StringUtils.hasText(msg)) {
            return null;
        }
        java.util.regex.Matcher max = java.util.regex.Pattern
                .compile("(\\d+(?:\\.\\d+)?)\\s*(?:元)?\\s*(?:以内|以下|内|下)")
                .matcher(msg);
        if (max.find()) {
            try {
                return Double.parseDouble(max.group(1));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String polishGuideReply(String userMsg,
                                    java.util.List<com.scaffold.entity.Product> products) {
        StringBuilder sb = new StringBuilder();
        sb.append("按你的需求，我在站内为你找到了 ").append(products.size()).append(" 件相关在售商品");
        if (userMsg != null && (userMsg.contains("卫衣") || userMsg.contains("耳机") || userMsg.contains("台灯"))) {
            sb.append("，可以直接看下方卡片");
        }
        sb.append("：\n");
        int i = 1;
        for (com.scaffold.entity.Product p : products) {
            if (i > 4) {
                break;
            }
            sb.append(i++).append(". ")
                    .append(p.getName())
                    .append("，¥").append(p.getPrice() == null ? "-" : p.getPrice());
            if (p.getSales() != null) {
                sb.append("，已售 ").append(p.getSales());
            }
            sb.append("\n");
        }
        sb.append("喜欢的话可以说「加入购物车」，或点卡片进详情。");
        return sb.toString().trim();
    }

    private ChatModel resolveChatModel() {
        String key = resolveApiKey();
        String base = normalizeBaseUrl(resolveApiUrl());
        String chatModel = resolveModel();
        String cacheKey = key + "|" + base + "|" + chatModel;
        return chatModelCache.computeIfAbsent(cacheKey, k -> {
            OpenAiApi openAiApi = OpenAiApi.builder()
                    .baseUrl(base)
                    .apiKey(key)
                    .build();
            return OpenAiChatModel.builder()
                    .openAiApi(openAiApi)
                    .defaultOptions(OpenAiChatOptions.builder()
                            .model(chatModel)
                            .temperature(0.3)
                            .build())
                    .build();
        });
    }

    private String normalizeBaseUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return "https://api.deepseek.com";
        }
        String u = url.trim();
        u = u.replaceAll("(?i)/v1/chat/completions/?$", "");
        u = u.replaceAll("(?i)/chat/completions/?$", "");
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }

    private boolean looksLikeCartOrOrder(String msg) {
        String m = msg.toLowerCase(Locale.ROOT);
        return m.contains("购物车") || m.contains("下单") || m.contains("结算")
                || m.contains("支付") || m.contains("订单") || m.contains("加购")
                || m.contains("加入购物车");
    }

    private boolean isAiReady() {
        // 如果没api_key值，就返回false
        if (!StringUtils.hasText(resolveApiKey())) {
            return false;
        }
        // 有的话，就再尝试获取ai_enabled字段，看看是否设置能用
        SysConfig cfg = sysConfigMapper.selectById("ai_enabled");
        if (cfg != null && StringUtils.hasText(cfg.getConfigValue())) {
            String v = cfg.getConfigValue().trim();
            return "1".equals(v) || "true".equalsIgnoreCase(v) || "yes".equalsIgnoreCase(v); // 有api_key,且ai_enbaled的值也意思是能用，返回true
        }
        return aiEnabled || StringUtils.hasText(resolveApiKey());
    }

    private String resolveApiKey() {
        SysConfig cfg = sysConfigMapper.selectById("ai_api_key"); // 尝试从数据库表sys_config中获取指定配置key的记录对象
        if (cfg != null && StringUtils.hasText(cfg.getConfigValue())) {
            return cfg.getConfigValue().trim(); // 发现如果配置了对应的api_key值，那么就取出来返回
        }
        return apiKey; // 否则用application.yml配置的apikey值，现在是什么都没配，取到的是空字符串 ""。
    }

    private String resolveApiUrl() {
        SysConfig cfg = sysConfigMapper.selectById("ai_api_url");
        if (cfg != null && StringUtils.hasText(cfg.getConfigValue())) {
            return cfg.getConfigValue().trim();
        }
        return apiUrl;
    }

    private String resolveModel() {
        SysConfig cfg = sysConfigMapper.selectById("ai_model");
        if (cfg != null && StringUtils.hasText(cfg.getConfigValue())) {
            return cfg.getConfigValue().trim();
        }
        return model;
    }
}
