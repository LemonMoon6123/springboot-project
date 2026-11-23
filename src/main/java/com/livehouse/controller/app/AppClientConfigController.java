package com.livehouse.controller.app;

import com.livehouse.entity.Result;
import com.livehouse.vo.app.config.ClientConfigVo;
import com.livehouse.vo.app.config.ClientMapConfigVo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客户端运行配置（地图 Key 等，非敏感业务机密；高德 Web Key 需在高德控制台配置域名白名单）
 */
@RestController
@RequestMapping("/app/config")
public class AppClientConfigController {

    @Value("${livehouse.map.amap-js-key:}")
    private String amapJsKey;

    @GetMapping("/client")
    public Result client() {
        ClientMapConfigVo map = new ClientMapConfigVo();
        map.setProvider("amap");
        map.setJsKey(amapJsKey == null ? "" : amapJsKey.trim());
        ClientConfigVo data = new ClientConfigVo();
        data.setMap(map);
        return Result.ok(data);
    }
}