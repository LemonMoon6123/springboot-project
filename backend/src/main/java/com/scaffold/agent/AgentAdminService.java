package com.scaffold.agent;

import com.scaffold.dto.AgentAdminOverview;
import com.scaffold.dto.AgentToolView;
import com.scaffold.dto.RagOverview;
import com.scaffold.entity.SysConfig;
import com.scaffold.mapper.SysConfigMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AgentAdminService {

    private static final Map<String, String> TOOL_GROUPS = new LinkedHashMap<>();

    static {
        TOOL_GROUPS.put("searchProducts", "导购推荐");
        TOOL_GROUPS.put("recommendProducts", "导购推荐");
        TOOL_GROUPS.put("viewCart", "购物车");
        TOOL_GROUPS.put("addToCart", "购物车");
        TOOL_GROUPS.put("listAddresses", "结算下单");
        TOOL_GROUPS.put("previewCheckout", "结算下单");
        TOOL_GROUPS.put("checkoutCart", "结算下单");
        TOOL_GROUPS.put("createOrder", "结算下单");
        TOOL_GROUPS.put("payOrder", "支付履约");
        TOOL_GROUPS.put("listOrders", "支付履约");
    }

    @Autowired
    private MallAgentTools mallAgentTools;
    @Autowired
    private RagKnowledgeService ragKnowledgeService;
    @Autowired
    private SysConfigMapper sysConfigMapper;

    @Value("${app.ai.enabled:false}")
    private boolean aiEnabled;
    @Value("${app.ai.api-url:https://api.deepseek.com/chat/completions}")
    private String apiUrl;
    @Value("${app.ai.api-key:}")
    private String apiKey;
    @Value("${app.ai.model:deepseek-chat}")
    private String model;
    @Value("${app.ai.vision-model:deepseek-v4-flash-vision-exp}")
    private String visionModel;

    public AgentAdminOverview overview() {
        List<AgentToolView> tools = listTools();
        RagOverview rag = ragKnowledgeService.overview();

        AgentAdminOverview view = new AgentAdminOverview();
        view.setEnabled(resolveEnabled());
        view.setReady(StringUtils.hasText(resolveApiKey()) && view.isEnabled());
        view.setProvider(detectProvider(resolveApiUrl()));
        view.setApiUrl(resolveApiUrl());
        view.setModel(resolveConfig("ai_model", model));
        view.setVisionModel(resolveConfig("ai_vision_model", visionModel));
        view.setApiKeyMasked(maskKey(resolveApiKey()));
        view.setChatEndpoint("/ai/agent");
        view.setVisionEndpoint("/ai/guide/vision");
        view.setStoreType(rag.getStoreType());
        view.setEmbeddingType(rag.getEmbeddingType());
        view.setToolCount(tools.size());
        view.setRagDocCount(rag.getDocCount());
        view.setRagChunkCount(rag.getChunkCount());
        view.setTools(tools);
        view.setRagDocs(rag.getDocs());
        return view;
    }

    public List<AgentToolView> listTools() {
        List<AgentToolView> list = new ArrayList<>();
        DefaultParameterNameDiscoverer names = new DefaultParameterNameDiscoverer();
        for (Method method : MallAgentTools.class.getDeclaredMethods()) {
            Tool tool = method.getAnnotation(Tool.class);
            if (tool == null) {
                continue;
            }
            AgentToolView view = new AgentToolView();
            view.setName(method.getName());
            view.setGroupName(TOOL_GROUPS.getOrDefault(method.getName(), "其它"));
            view.setDescription(tool.description());
            view.setNeedLogin(tool.description().contains("需要登录")
                    || TOOL_GROUPS.getOrDefault(method.getName(), "").matches("购物车|结算下单|支付履约"));
            view.setConfirmRequired(tool.description().contains("confirmed=true")
                    || "checkoutCart".equals(method.getName())
                    || "createOrder".equals(method.getName())
                    || "payOrder".equals(method.getName()));

            String[] paramNames = names.getParameterNames(method);
            Parameter[] parameters = method.getParameters();
            List<AgentToolView.ParamView> params = new ArrayList<>();
            for (int i = 0; i < parameters.length; i++) {
                Parameter p = parameters[i];
                ToolParam meta = p.getAnnotation(ToolParam.class);
                String pname = paramNames != null && i < paramNames.length ? paramNames[i] : p.getName();
                String desc = meta == null ? "" : meta.description();
                boolean required = meta != null && meta.required();
                params.add(new AgentToolView.ParamView(
                        pname,
                        p.getType().getSimpleName(),
                        desc,
                        required
                ));
            }
            view.setParams(params);
            list.add(view);
        }
        list.sort((a, b) -> {
            List<String> order = List.of("导购推荐", "购物车", "结算下单", "支付履约", "其它");
            int ga = order.indexOf(a.getGroupName());
            int gb = order.indexOf(b.getGroupName());
            if (ga != gb) {
                return Integer.compare(ga, gb);
            }
            return a.getName().compareTo(b.getName());
        });
        return list;
    }

    private boolean resolveEnabled() {
        SysConfig cfg = sysConfigMapper.selectById("ai_enabled");
        if (cfg != null && StringUtils.hasText(cfg.getConfigValue())) {
            String v = cfg.getConfigValue().trim();
            return "1".equals(v) || "true".equalsIgnoreCase(v) || "yes".equalsIgnoreCase(v);
        }
        return aiEnabled || StringUtils.hasText(resolveApiKey());
    }

    private String resolveApiKey() {
        return resolveConfig("ai_api_key", apiKey);
    }

    private String resolveApiUrl() {
        return resolveConfig("ai_api_url", apiUrl);
    }

    private String resolveConfig(String key, String fallback) {
        SysConfig cfg = sysConfigMapper.selectById(key);
        if (cfg != null && StringUtils.hasText(cfg.getConfigValue())) {
            return cfg.getConfigValue().trim();
        }
        return fallback == null ? "" : fallback;
    }

    private static String detectProvider(String url) {
        String u = url == null ? "" : url.toLowerCase(Locale.ROOT);
        if (u.contains("deepseek")) {
            return "DeepSeek（OpenAI 兼容）";
        }
        if (u.contains("dashscope")) {
            return "通义千问";
        }
        if (u.contains("openai")) {
            return "OpenAI";
        }
        return "OpenAI 兼容接口";
    }

    private static String maskKey(String key) {
        if (!StringUtils.hasText(key)) {
            return "未配置";
        }
        String k = key.trim();
        if (k.length() <= 8) {
            return k.charAt(0) + "****";
        }
        return k.substring(0, 4) + "••••" + k.substring(k.length() - 4);
    }
}
