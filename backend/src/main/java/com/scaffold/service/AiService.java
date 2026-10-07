package com.scaffold.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scaffold.dto.AiGuideResponse;
import com.scaffold.entity.Category;
import com.scaffold.entity.Product;
import com.scaffold.entity.ShopOrder;
import com.scaffold.entity.SysConfig;
import com.scaffold.mapper.CategoryMapper;
import com.scaffold.mapper.ProductMapper;
import com.scaffold.mapper.SysConfigMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern PRICE_RANGE = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*[-~到至]\\s*(\\d+(?:\\.\\d+)?)");
    private static final Pattern PRICE_MAX = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:元)?\\s*(?:以内|以下|内|下)");
    private static final Pattern PRICE_AROUND = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:元)?\\s*(?:左右|上下|附近)");
    private static final Pattern PRICE_BAI = Pattern.compile("百元(?:内|以内|以下)?");
    private static final Pattern VISION_KEYWORDS_LINE = Pattern.compile("(?im)关键词[:：]\\s*[「\"“]?([^\\n」\"”]+)");

    /** 场景/品类词 → 相关关键词（用于商品名/简介/分类打分） */
    private static final Map<String, String[]> INTENT_KEYWORDS = new HashMap<>();

    static {
        INTENT_KEYWORDS.put("数码", new String[]{"数码", "电器", "耳机", "电脑", "笔记本", "手表", "无线", "蓝牙", "降噪", "智能"});
        INTENT_KEYWORDS.put("配件", new String[]{"耳机", "手表", "配件", "数码"});
        INTENT_KEYWORDS.put("耳机", new String[]{"耳机", "降噪", "蓝牙", "无线", "听歌"});
        INTENT_KEYWORDS.put("电脑", new String[]{"电脑", "笔记本", "轻薄本", "办公本"});
        INTENT_KEYWORDS.put("手表", new String[]{"手表", "运动版", "心率", "智能表"});
        INTENT_KEYWORDS.put("服饰", new String[]{"卫衣", "衣服", "服饰", "棉", "穿搭"});
        INTENT_KEYWORDS.put("卫衣", new String[]{"卫衣", "棉", "服饰"});
        INTENT_KEYWORDS.put("护肤", new String[]{"洁面", "护肤", "美妆", "氨基酸", "护理", "敏肌"});
        INTENT_KEYWORDS.put("美妆", new String[]{"洁面", "护肤", "美妆", "护理"});
        INTENT_KEYWORDS.put("洁面", new String[]{"洁面", "氨基酸", "护肤"});
        INTENT_KEYWORDS.put("咖啡", new String[]{"咖啡", "挂耳", "冲泡", "饮料", "礼盒"});
        INTENT_KEYWORDS.put("食品", new String[]{"咖啡", "食品", "饮料", "礼盒"});
        INTENT_KEYWORDS.put("家居", new String[]{"台灯", "家居", "氛围", "灯", "护眼", "居家"});
        INTENT_KEYWORDS.put("台灯", new String[]{"台灯", "氛围", "灯", "护眼", "家居"});
        INTENT_KEYWORDS.put("运动", new String[]{"跑步", "运动鞋", "运动", "缓震", "户外"});
        INTENT_KEYWORDS.put("鞋", new String[]{"鞋", "跑步", "运动鞋", "缓震"});
        INTENT_KEYWORDS.put("礼物", new String[]{"礼盒", "礼物", "护肤", "咖啡", "台灯", "卫衣", "女生"});
        INTENT_KEYWORDS.put("女生", new String[]{"护肤", "洁面", "咖啡", "礼盒", "台灯", "卫衣"});
        INTENT_KEYWORDS.put("办公", new String[]{"办公", "笔记本", "台灯", "咖啡", "耳机", "居家"});
        INTENT_KEYWORDS.put("居家", new String[]{"居家", "台灯", "家居", "咖啡", "氛围"});
        INTENT_KEYWORDS.put("热销", new String[]{});
        INTENT_KEYWORDS.put("畅销", new String[]{});
        INTENT_KEYWORDS.put("爆款", new String[]{});
    }

    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private CategoryMapper categoryMapper;
    @Autowired
    private SysConfigMapper sysConfigMapper;
    @Autowired
    private OrderService orderService;
    @Autowired
    private ProductService productService;

    @Value("${app.ai.enabled:false}")
    private boolean aiEnabled;
    @Value("${app.ai.api-url:https://api.openai.com/v1/chat/completions}")
    private String apiUrl;
    @Value("${app.ai.api-key:}")
    private String apiKey;
    @Value("${app.ai.model:gpt-3.5-turbo}")
    private String model;
    @Value("${app.upload.path:uploads}")
    private String uploadPath;

    private final RestTemplate restTemplate;

    public AiService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(15000);
        factory.setReadTimeout(90000);
        this.restTemplate = new RestTemplate(factory);
    }

    public AiGuideResponse guide(String message) {
        String msg = message == null ? "" : message.trim();
        GuideIntent intent = parseIntent(msg);
        List<Product> products = searchByIntent(intent, true);
        String reply = buildGuideReply(intent, products);

        // 有站内商品：仅润色文案，商品列表由前端卡片展示，避免重复编号清单
        if (!products.isEmpty() && isAiReady()) {
            String llm = tryLlm("你是电商导购助手。只用 2-4 句中文写推荐理由与挑选提示，不要罗列商品名、价格、编号清单。"
                    + "用户需求：" + msg
                    + "。已匹配：" + products.stream()
                    .map(p -> p.getName() + "¥" + p.getPrice())
                    .collect(Collectors.joining("、")));
            if (StringUtils.hasText(llm)) {
                reply = llm.trim();
            }
            return new AiGuideResponse(reply, products);
        }

        // 站内无匹配：用 AI 查询结果做导购建议（不瞎推热销）
        if (products.isEmpty() && StringUtils.hasText(msg) && isAiReady()) {
            String llm = tryLlm("你是电商导购助手。站内商品库暂时没有与用户需求高度匹配的在售商品。"
                    + "请根据用户需求用中文给出实用导购建议：1）你理解的需求；2）可购买的品类/关键词；"
                    + "3）挑选注意点与大致价位参考。不要编造本站具体商品名或下单链接。"
                    + "用户需求：" + msg);
            if (StringUtils.hasText(llm)) {
                return new AiGuideResponse("站内暂未找到高度匹配商品，以下为 AI 导购建议：\n\n" + llm.trim(), List.of());
            }
        }
        return new AiGuideResponse(reply, products);
    }

    /**
     * 识图导购：先站内相似检索；没有同款/相似商品时，返回 AI 识图导购结果。
     */
    public AiGuideResponse guideByImage(String imageUrl, String hint) {
        if (!StringUtils.hasText(imageUrl)) {
            return new AiGuideResponse("请先上传一张商品图片，我再帮你找相似好物。", List.of());
        }
        String tip = StringUtils.hasText(hint) ? hint.trim() : "";
        VisionResult vision = analyzeVision(imageUrl, tip);

        String query;
        if (StringUtils.hasText(vision.keywords) && StringUtils.hasText(tip)) {
            query = tip + " " + vision.keywords;
        } else if (StringUtils.hasText(vision.keywords)) {
            query = vision.keywords;
        } else {
            query = tip;
        }

        List<Product> products = List.of();
        if (StringUtils.hasText(query)) {
            // 识图必须严格按关键词命中，禁止热销/全站兜底乱推
            products = searchByVisionQuery(query, vision.keywords);
        }

        if (!products.isEmpty()) {
            String prefix = StringUtils.hasText(vision.keywords)
                    ? "根据图片识别，您可能在找「" + vision.keywords.trim() + "」。已为您匹配站内相似商品："
                    : "已根据图片为您匹配站内相似商品：";
            if (StringUtils.hasText(tip)) {
                prefix += "（已结合您的补充说明）";
            }
            GuideIntent intent = parseIntent(StringUtils.hasText(query) ? query : "识图");
            String body = buildGuideReply(intent, products);
            if (isAiReady()) {
                String llm = tryLlm("你是电商导购助手。只用 2-3 句中文写推荐理由，必须围绕已匹配商品，"
                        + "不要推荐未列出的商品，不要说「换个角度推荐其他品类」。"
                        + "识图关键词：" + (vision.keywords == null ? "" : vision.keywords)
                        + "。用户补充：" + tip
                        + "。已匹配：" + products.stream()
                        .map(p -> p.getName() + "¥" + p.getPrice())
                        .collect(Collectors.joining("、")));
                if (StringUtils.hasText(llm)) {
                    body = llm.trim();
                }
            }
            return new AiGuideResponse(prefix + "\n\n" + body, products);
        }

        // 站内无相似商品：只返回 AI 识图建议，绝不附带无关热销卡片
        String advice = StringUtils.hasText(vision.advice) ? vision.advice.trim() : "";
        if (!StringUtils.hasText(advice) && StringUtils.hasText(tip) && isAiReady()) {
            advice = tryLlm("你是电商导购助手。用户上传了商品图，站内暂无相似在售商品。"
                    + "请结合识图关键词与用户补充，用中文给出购买方向建议（品类、检索词、价位），"
                    + "不要编造本站具体商品名。识图关键词："
                    + (vision.keywords == null ? "" : vision.keywords)
                    + "。用户补充：" + tip);
        }
        if (!StringUtils.hasText(advice) && StringUtils.hasText(vision.keywords) && isAiReady()) {
            advice = tryLlm("你是电商导购助手。站内没有与图片高度相似的在售商品。"
                    + "请根据识图关键词给中文导购建议（可买什么品类、怎么搜、挑选注意），"
                    + "不要编造本站商品名。关键词：" + vision.keywords);
        }

        if (StringUtils.hasText(advice)) {
            StringBuilder sb = new StringBuilder();
            sb.append("站内暂未找到高度相似的在售商品。");
            if (StringUtils.hasText(vision.keywords)) {
                sb.append("识图关键词：").append(vision.keywords.trim()).append("。");
            }
            sb.append("\n\n以下为 AI 识图导购建议：\n\n").append(advice.trim());
            return new AiGuideResponse(sb.toString(), List.of());
        }

        if (!isAiReady()) {
            return new AiGuideResponse(
                    "已收到图片。当前 AI 识图未就绪（请在 sys_config 配置 DeepSeek 的 ai_api_key；"
                            + "文本用 deepseek-chat，识图用 deepseek-v4-flash-vision-exp）。"
                            + "您也可以直接用文字描述想找的商品。",
                    List.of());
        }
        return new AiGuideResponse(
                "已收到图片，但暂时未能完成识图分析。请稍后再试，或补充文字描述（颜色/品类/用途）。",
                List.of());
    }

    public String customerService(String message, Long userId) {
        String msg = message == null ? "" : message.trim();
        String rule = ruleBasedCustomerService(msg, userId);
        String llm = tryLlm("你是电商客服，用简洁中文回答售后/物流/退换货问题。用户：" + msg + "。参考：" + rule);
        return StringUtils.hasText(llm) ? llm : rule;
    }

    private GuideIntent parseIntent(String msg) {
        GuideIntent intent = new GuideIntent();
        intent.raw = msg;
        String lower = msg.toLowerCase(Locale.ROOT);

        Matcher range = PRICE_RANGE.matcher(msg);
        if (range.find()) {
            intent.minPrice = Double.parseDouble(range.group(1));
            intent.maxPrice = Double.parseDouble(range.group(2));
            if (intent.minPrice > intent.maxPrice) {
                double t = intent.minPrice;
                intent.minPrice = intent.maxPrice;
                intent.maxPrice = t;
            }
        } else {
            Matcher max = PRICE_MAX.matcher(msg);
            if (max.find()) {
                intent.maxPrice = Double.parseDouble(max.group(1));
            } else if (PRICE_BAI.matcher(msg).find() || msg.contains("一百以内") || msg.contains("100块内")) {
                intent.maxPrice = 100.0;
            } else {
                Matcher around = PRICE_AROUND.matcher(msg);
                if (around.find()) {
                    double center = Double.parseDouble(around.group(1));
                    intent.minPrice = Math.max(0, center * 0.7);
                    intent.maxPrice = center * 1.3;
                }
            }
        }

        if (containsAny(msg, "便宜", "低价", "实惠", "平价")) {
            intent.preferCheap = true;
            if (intent.maxPrice == null) {
                intent.maxPrice = 200.0;
            }
        }
        if (containsAny(msg, "热销", "畅销", "爆款", "好物推荐", "推荐热销")) {
            intent.preferHot = true;
        }

        Set<String> tokens = new LinkedHashSet<>();
        for (Map.Entry<String, String[]> entry : INTENT_KEYWORDS.entrySet()) {
            if (msg.contains(entry.getKey())) {
                tokens.add(entry.getKey());
                for (String k : entry.getValue()) {
                    tokens.add(k);
                }
            }
        }
        // 直接命中商品相关词
        for (String k : List.of("耳机", "笔记本", "电脑", "手表", "卫衣", "洁面", "护肤", "咖啡", "台灯", "灯",
                "跑步", "运动鞋", "礼盒", "礼物", "办公", "居家", "数码", "家居", "运动")) {
            if (msg.contains(k)) {
                tokens.add(k);
                String[] extra = INTENT_KEYWORDS.get(k);
                if (extra != null) {
                    for (String e : extra) {
                        tokens.add(e);
                    }
                }
            }
        }
        intent.keywords.addAll(tokens);

        // 自由词：按空格/标点拆开，避免整句过长被丢弃或误当空关键词
        String free = msg
                .replaceAll("(我想|我要|帮我|推荐|找一下|找个|有没有|什么|怎么|哪个|一下|看看|适合|可以|给我|来点|来个)", " ")
                .replaceAll("(便宜|实惠|低价|热销|畅销|爆款|好物|以内|以下|左右|上下|附近|元|块钱?)", " ")
                .replaceAll("\\d+(?:\\.\\d+)?", " ")
                .replaceAll("[-~到至百]", " ")
                .replaceAll("[，,。.!！？?；;：:、|/\\\\]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (StringUtils.hasText(free)) {
            for (String part : free.split("\\s+")) {
                if (part.length() >= 2 && part.length() <= 16) {
                    intent.keywords.add(part);
                }
            }
        }

        intent.hasStrongFilter = intent.minPrice != null || intent.maxPrice != null
                || !intent.keywords.isEmpty() || intent.preferHot || intent.preferCheap;
        return intent;
    }

    /**
     * 识图专用检索：必须命中关键词，绝不热销兜底。
     */
    private List<Product> searchByVisionQuery(String query, String visionKeywords) {
        GuideIntent intent = parseIntent(query == null ? "" : query);
        // 确保识图关键词被拆词加入（即使 parseIntent 未覆盖）
        if (StringUtils.hasText(visionKeywords)) {
            for (String part : visionKeywords.replaceAll("[，,。|/]+", " ").split("\\s+")) {
                String token = part.trim();
                if (token.length() >= 2) {
                    intent.keywords.add(token);
                }
            }
        }
        if (intent.keywords.isEmpty()) {
            return List.of();
        }
        intent.preferHot = false;
        intent.hasStrongFilter = true;

        List<Product> all = productMapper.selectList(new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, 1));
        if (all.isEmpty()) {
            return List.of();
        }
        Map<Long, String> categoryNames = loadCategoryNames();
        List<ScoredProduct> scored = new ArrayList<>();
        for (Product p : all) {
            int score = scoreProductStrict(p, intent, categoryNames.getOrDefault(p.getCategoryId(), ""));
            // 至少命中一个实质关键词（分数基线 20）
            if (score >= 20) {
                scored.add(new ScoredProduct(p, score));
            }
        }
        if (scored.isEmpty()) {
            return List.of();
        }
        scored.sort(Comparator
                .comparingInt((ScoredProduct s) -> s.score).reversed()
                .thenComparing((ScoredProduct s) -> s.product.getSales() == null ? 0 : s.product.getSales(),
                        Comparator.reverseOrder()));
        List<Product> result = scored.stream().limit(6).map(s -> s.product).collect(Collectors.toList());
        productService.enrich(result);
        return result;
    }

    /** 严格打分：没命中关键词直接 0，不做销量底分 */
    private int scoreProductStrict(Product p, GuideIntent intent, String categoryName) {
        double price = p.getPrice() == null ? 0 : p.getPrice();
        if (intent.minPrice != null && price < intent.minPrice) {
            return 0;
        }
        if (intent.maxPrice != null && price > intent.maxPrice) {
            return 0;
        }

        String hay = ((p.getName() == null ? "" : p.getName()) + " "
                + (p.getDescription() == null ? "" : p.getDescription()) + " "
                + categoryName).toLowerCase(Locale.ROOT);

        int score = 0;
        boolean hitKeyword = false;
        for (String kw : intent.keywords) {
            if (!StringUtils.hasText(kw) || kw.length() < 2) {
                continue;
            }
            // 过滤过于宽泛、易误伤的词
            if (Set.of("相似", "商品", "图片", "同款", "推荐", "找", "帮我", "根据").contains(kw)) {
                continue;
            }
            String k = kw.toLowerCase(Locale.ROOT);
            if (hay.contains(k)) {
                hitKeyword = true;
                score += 20;
                if (p.getName() != null && p.getName().toLowerCase(Locale.ROOT).contains(k)) {
                    score += 15;
                }
                if (categoryName.toLowerCase(Locale.ROOT).contains(k)) {
                    score += 10;
                }
            }
        }
        return hitKeyword ? score : 0;
    }

    private List<Product> searchByIntent(GuideIntent intent) {
        return searchByIntent(intent, true);
    }

    private List<Product> searchByIntent(GuideIntent intent, boolean allowHotFallback) {
        List<Product> all = productMapper.selectList(new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, 1));
        if (all.isEmpty()) {
            return List.of();
        }
        Map<Long, String> categoryNames = loadCategoryNames();

        List<ScoredProduct> scored = new ArrayList<>();
        for (Product p : all) {
            int score = scoreProduct(p, intent, categoryNames.getOrDefault(p.getCategoryId(), ""));
            if (score > 0) {
                scored.add(new ScoredProduct(p, score));
            }
        }

        // 只有“热销/随便看看”且没有品类词时，才按销量兜底
        if (scored.isEmpty()) {
            if (allowHotFallback && (intent.preferHot || !intent.hasStrongFilter || intent.keywords.isEmpty())) {
                return productService.listHot(6);
            }
            // 有明确品类/预算但没命中：不要瞎推
            return List.of();
        }

        scored.sort(Comparator
                .comparingInt((ScoredProduct s) -> s.score).reversed()
                .thenComparing((ScoredProduct s) -> s.product.getSales() == null ? 0 : s.product.getSales(),
                        Comparator.reverseOrder())
                .thenComparing(s -> s.product.getPrice() == null ? Double.MAX_VALUE : s.product.getPrice()));

        List<Product> result = scored.stream().limit(6).map(s -> s.product).collect(Collectors.toList());
        productService.enrich(result);
        return result;
    }

    private int scoreProduct(Product p, GuideIntent intent, String categoryName) {
        double price = p.getPrice() == null ? 0 : p.getPrice();
        if (intent.minPrice != null && price < intent.minPrice) {
            return 0;
        }
        if (intent.maxPrice != null && price > intent.maxPrice) {
            return 0;
        }

        String hay = ((p.getName() == null ? "" : p.getName()) + " "
                + (p.getDescription() == null ? "" : p.getDescription()) + " "
                + categoryName).toLowerCase(Locale.ROOT);

        int score = 0;
        boolean hitKeyword = false;
        for (String kw : intent.keywords) {
            if (!StringUtils.hasText(kw) || kw.length() < 2) {
                continue;
            }
            String k = kw.toLowerCase(Locale.ROOT);
            if (hay.contains(k)) {
                hitKeyword = true;
                score += 20;
                if (p.getName() != null && p.getName().toLowerCase(Locale.ROOT).contains(k)) {
                    score += 15;
                }
                if (categoryName.toLowerCase(Locale.ROOT).contains(k)) {
                    score += 10;
                }
            }
        }

        // 有关键词意图但完全没命中 → 剔除
        if (!intent.keywords.isEmpty() && !hitKeyword) {
            // 纯热销/便宜意图时 keywords 可能含空集合扩展，允许销量分
            boolean onlyHotOrCheap = intent.keywords.stream().allMatch(k ->
                    Set.of("热销", "畅销", "爆款").contains(k));
            if (!onlyHotOrCheap) {
                return 0;
            }
        }

        if (intent.preferHot) {
            score += Math.min(30, (p.getSales() == null ? 0 : p.getSales()) / 30);
        }
        if (intent.preferCheap) {
            score += (int) Math.max(0, 40 - price / 10);
        }
        // 无特殊意图时给一点销量底分，避免全 0
        if (intent.keywords.isEmpty() && !intent.preferCheap) {
            score += 1 + Math.min(20, (p.getSales() == null ? 0 : p.getSales()) / 50);
        }
        return score;
    }

    private Map<Long, String> loadCategoryNames() {
        List<Category> categories = categoryMapper.selectList(null);
        Map<Long, String> map = new HashMap<>();
        for (Category c : categories) {
            map.put(c.getId(), c.getName() == null ? "" : c.getName());
        }
        return map;
    }

    private String buildGuideReply(GuideIntent intent, List<Product> products) {
        if (products.isEmpty()) {
            StringBuilder sb = new StringBuilder("按您的需求暂时没有匹配到在售商品。");
            if (intent.maxPrice != null) {
                sb.append("（预算约 ¥").append(trimNum(intent.maxPrice)).append(" 以内）");
            }
            sb.append("\n可以试试：放宽预算、换个品类词（如耳机/护肤/咖啡/台灯），或直接说「推荐热销好物」。");
            return sb.toString();
        }

        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(intent.raw)) {
            sb.append("根据「").append(intent.raw).append("」");
        } else {
            sb.append("为您");
        }
        if (intent.maxPrice != null && intent.minPrice != null) {
            sb.append("，在 ¥").append(trimNum(intent.minPrice)).append("-").append(trimNum(intent.maxPrice));
        } else if (intent.maxPrice != null) {
            sb.append("，在 ¥").append(trimNum(intent.maxPrice)).append(" 以内");
        }
        sb.append("为您筛到 ").append(products.size()).append(" 件相关商品，可点击下方卡片查看详情。");
        return sb.toString();
    }

    private String formatProductLines(List<Product> products) {
        // 兼容保留：正常导购回复由前端卡片展示，不再拼接编号清单
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < products.size(); i++) {
            Product p = products.get(i);
            sb.append(i + 1).append(". ").append(p.getName())
                    .append("  ¥").append(p.getPrice());
            if (p.getCategoryName() != null) {
                sb.append("  · ").append(p.getCategoryName());
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String trimNum(double n) {
        if (Math.abs(n - Math.rint(n)) < 1e-6) {
            return String.valueOf((long) Math.rint(n));
        }
        return String.format(Locale.ROOT, "%.2f", n);
    }

    private String ruleBasedCustomerService(String msg, Long userId) {
        if (containsAny(msg, "物流", "快递", "发货", "配送", "到哪")) {
            List<ShopOrder> recent = userId == null ? List.of() : orderService.recentOrders(userId, 3);
            if (!recent.isEmpty()) {
                ShopOrder o = recent.get(0);
                String statusText = statusText(o.getStatus());
                return "您最近的订单 " + o.getOrderNo() + " 当前状态：" + statusText
                        + "。一般付款后1-2天发货，发货后2-5天送达。如需催发请联系商家。";
            }
            return "付款后商家通常1-2天内发货，快递约2-5天送达。登录后我可帮您查询具体订单。";
        }
        if (containsAny(msg, "退货", "退款", "换货", "售后")) {
            return "支持7天无理由退换（不影响二次销售）。请在「我的订单」中对已发货/已完成订单点击「申请售后」；"
                    + "待发货可直接取消订单并自动退回余额；售后需商家审核，同意后退款将退回账户余额。";
        }
        if (containsAny(msg, "支付", "余额", "充值", "付款")) {
            return "本商城支持余额支付。请先在个人中心充值，下单后在订单详情点击支付即可扣减余额。";
        }
        if (containsAny(msg, "取消", "不想要")) {
            return "待支付订单可直接取消并恢复库存；已支付待发货可取消并自动退回余额；已发货/已完成请在「我的订单」申请售后。";
        }
        if (containsAny(msg, "订单", "我的单")) {
            List<ShopOrder> recent = userId == null ? List.of() : orderService.recentOrders(userId, 5);
            if (recent.isEmpty()) {
                return "您暂无订单记录。去首页逛逛，选好物加入购物车即可下单。";
            }
            StringBuilder sb = new StringBuilder("您最近的订单：\n");
            for (ShopOrder o : recent) {
                sb.append("- ").append(o.getOrderNo()).append(" ¥").append(o.getTotalAmount())
                        .append(" ").append(statusText(o.getStatus())).append("\n");
            }
            return sb.toString();
        }
        if (containsAny(msg, "你好", "您好", "在吗", "客服")) {
            return "您好，我是智能客服。可咨询物流、退换货、支付余额、订单状态等问题。";
        }
        return "已收到您的问题。常见问题：物流进度、退换货政策、余额支付、取消订单。"
                + "您也可以描述具体订单号，我会尽量协助。";
    }

    private String statusText(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case 0 -> "待支付";
            case 1 -> "已支付/待发货";
            case 2 -> "已发货";
            case 3 -> "已完成";
            case 4 -> "已取消";
            default -> "未知";
        };
    }

    private boolean containsAny(String text, String... words) {
        for (String w : words) {
            if (text.contains(w)) {
                return true;
            }
        }
        return false;
    }

    private String tryLlm(String prompt) {
        if (!isAiReady()) {
            return null;
        }
        String key = resolveApiKey();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(key);
            Map<String, Object> body = new HashMap<>();
            body.put("model", resolveModel());
            List<Map<String, String>> messages = new ArrayList<>();
            Map<String, String> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", prompt);
            messages.add(userMsg);
            body.put("messages", messages);
            body.put("temperature", 0.3);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> resp = restTemplate.postForEntity(resolveApiUrl(), entity, String.class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                log.warn("LLM 调用失败 status={}", resp.getStatusCode());
                return null;
            }
            JsonNode root = MAPPER.readTree(resp.getBody());
            JsonNode message = root.path("choices").path(0).path("message");
            return extractMessageText(message);
        } catch (Exception e) {
            log.warn("LLM 调用异常: {}", e.getMessage());
            return null;
        }
    }

    /** 视觉识图：返回关键词 + 导购建议 */
    private VisionResult analyzeVision(String imageUrl, String hint) {
        VisionResult result = new VisionResult();
        if (!isAiReady()) {
            return result;
        }
        String key = resolveApiKey();
        String imageRef = toVisionImageRef(imageUrl);
        if (!StringUtils.hasText(imageRef)) {
            log.warn("识图失败：无法读取图片 {}", imageUrl);
            return result;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(key);

            String prompt = "你是电商识图导购助手。请直接给出最终答案（不要长篇思考过程），严格按下面两行格式回复：\n"
                    + "关键词: （2-8 个中文检索词，空格分隔）\n"
                    + "导购建议: （3-6 句中文：图中是什么、可买什么品类、挑选要点、大致价位。"
                    + "若是插画/表情包/风景，也请说明并给出可关联的周边或主题商品方向。"
                    + "不要编造某电商平台的具体在售商品名。）";
            if (StringUtils.hasText(hint)) {
                prompt += "\n用户补充说明：" + hint;
            }

            List<Map<String, Object>> contentParts = new ArrayList<>();
            Map<String, Object> textPart = new HashMap<>();
            textPart.put("type", "text");
            textPart.put("text", prompt);
            contentParts.add(textPart);

            Map<String, Object> imagePart = new HashMap<>();
            imagePart.put("type", "image_url");
            Map<String, String> imageUrlObj = new HashMap<>();
            imageUrlObj.put("url", imageRef);
            imagePart.put("image_url", imageUrlObj);
            contentParts.add(imagePart);

            Map<String, Object> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", contentParts);

            Map<String, Object> body = new HashMap<>();
            body.put("model", resolveVisionModel());
            body.put("messages", List.of(userMsg));
            body.put("temperature", 0.3);
            // DeepSeek 视觉模型会先占 reasoning_tokens，需要更大额度才能写出 content
            body.put("max_tokens", 1600);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> resp = restTemplate.postForEntity(resolveApiUrl(), entity, String.class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                log.warn("视觉模型调用失败 status={} body={}", resp.getStatusCode(),
                        resp.getBody() == null ? "" : resp.getBody().substring(0, Math.min(200, resp.getBody().length())));
                return result;
            }
            JsonNode root = MAPPER.readTree(resp.getBody());
            JsonNode message = root.path("choices").path(0).path("message");
            String raw = extractMessageText(message);
            if (!StringUtils.hasText(raw)) {
                log.warn("视觉模型返回空内容 finish={} bodySnippet={}",
                        root.path("choices").path(0).path("finish_reason").asText(""),
                        resp.getBody().substring(0, Math.min(300, resp.getBody().length())));
                return result;
            }
            raw = raw.trim();
            Matcher km = VISION_KEYWORDS_LINE.matcher(raw);
            if (km.find()) {
                result.keywords = cleanKeywords(km.group(1));
            }
            String advice = raw.replaceFirst("(?im)^\\s*关键词[:：].+$\\n?", "").trim();
            advice = advice.replaceFirst("(?im)^\\s*导购建议[:：]\\s*", "").trim();
            // 若整段落在 reasoning 里，尽量截取导购相关段落
            if (!StringUtils.hasText(advice) || advice.length() < 8) {
                advice = raw;
            }
            result.advice = advice;
            if (!StringUtils.hasText(result.keywords)) {
                result.keywords = cleanKeywords(raw.replaceAll("[\\r\\n]+", " "));
                if (result.keywords != null && result.keywords.length() > 40) {
                    String[] parts = result.keywords.split("\\s+");
                    result.keywords = String.join(" ", java.util.Arrays.copyOf(parts, Math.min(parts.length, 6)));
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("视觉模型调用异常: {}", e.getMessage());
            return result;
        }
    }

    /** DeepSeek 视觉/推理模型可能把正文放在 reasoning_content */
    private String extractMessageText(JsonNode message) {
        if (message == null || message.isMissingNode()) {
            return null;
        }
        JsonNode content = message.path("content");
        if (!content.isMissingNode() && !content.isNull()) {
            String text = content.isTextual() ? content.asText() : content.toString();
            if (StringUtils.hasText(text) && !"null".equals(text)) {
                return text.trim();
            }
        }
        JsonNode reasoning = message.path("reasoning_content");
        if (!reasoning.isMissingNode() && !reasoning.isNull()) {
            String text = reasoning.asText();
            if (StringUtils.hasText(text)) {
                return text.trim();
            }
        }
        return null;
    }

    private String cleanKeywords(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String text = raw.replaceAll("[，,。.!！？?；;：:、|/\\\\]+", " ").replaceAll("\\s+", " ").trim();
        return StringUtils.hasText(text) ? text : null;
    }

    private String toVisionImageRef(String imageUrl) {
        String url = imageUrl == null ? "" : imageUrl.trim();
        if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("data:")) {
            return url;
        }
        try {
            String relative = url;
            if (relative.startsWith("/api/uploads/")) {
                relative = relative.substring("/api".length());
            }
            if (relative.startsWith("/uploads/")) {
                relative = relative.substring("/uploads/".length());
            } else if (relative.startsWith("uploads/")) {
                relative = relative.substring("uploads/".length());
            }
            Path file = Paths.get(uploadPath).toAbsolutePath().normalize().resolve(relative).normalize();
            Path root = Paths.get(uploadPath).toAbsolutePath().normalize();
            if (!file.startsWith(root) || !Files.exists(file) || !Files.isRegularFile(file)) {
                return null;
            }
            String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
            String mime = "image/jpeg";
            if (name.endsWith(".png")) {
                mime = "image/png";
            } else if (name.endsWith(".webp")) {
                mime = "image/webp";
            } else if (name.endsWith(".gif")) {
                mime = "image/gif";
            }
            byte[] bytes = Files.readAllBytes(file);
            if (bytes.length == 0 || bytes.length > 4 * 1024 * 1024) {
                return null;
            }
            return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            return null;
        }
    }

    /** 有 API Key 即认为可调用；yml enabled=false 时仍允许 sys_config 密钥生效 */
    private boolean isAiReady() {
        if (!StringUtils.hasText(resolveApiKey())) {
            return false;
        }
        SysConfig cfg = sysConfigMapper.selectById("ai_enabled");
        if (cfg != null && StringUtils.hasText(cfg.getConfigValue())) {
            String v = cfg.getConfigValue().trim();
            return "1".equals(v) || "true".equalsIgnoreCase(v) || "yes".equalsIgnoreCase(v);
        }
        return aiEnabled || StringUtils.hasText(resolveApiKey());
    }

    private String resolveVisionModel() {
        SysConfig cfg = sysConfigMapper.selectById("ai_vision_model");
        if (cfg != null && StringUtils.hasText(cfg.getConfigValue())) {
            return cfg.getConfigValue().trim();
        }
        String url = resolveApiUrl();
        if (url != null) {
            String lower = url.toLowerCase(Locale.ROOT);
            // DeepSeek 识图需用视觉模型，普通 deepseek-chat 不支持图片
            if (lower.contains("deepseek")) {
                return "deepseek-v4-flash-vision-exp";
            }
            // 通义兼容模式
            if (lower.contains("dashscope")) {
                return "qwen-vl-plus";
            }
        }
        return resolveModel();
    }

    private String resolveApiKey() {
        SysConfig cfg = sysConfigMapper.selectById("ai_api_key");
        if (cfg != null && StringUtils.hasText(cfg.getConfigValue())) {
            return cfg.getConfigValue().trim();
        }
        return apiKey;
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

    private static class VisionResult {
        String keywords;
        String advice;
    }

    private static class GuideIntent {
        String raw = "";
        Double minPrice;
        Double maxPrice;
        boolean preferHot;
        boolean preferCheap;
        boolean hasStrongFilter;
        Set<String> keywords = new HashSet<>();
    }

    private static class ScoredProduct {
        final Product product;
        final int score;

        ScoredProduct(Product product, int score) {
            this.product = product;
            this.score = score;
        }
    }
}
