package com.livehouse.controller.app;

import com.livehouse.entity.Result;
import com.livehouse.entity.StatusCode;
import com.livehouse.service.app.AppRealnameService;
import com.livehouse.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Map;

@RestController
@RequestMapping("/app/realname")
public class AppRealnameController {

    @Resource
    private AppRealnameService appRealnameService;

    @GetMapping
    public Result get() {
        if (UserHolder.getUser() == null) {
            return Result.fail(StatusCode.UNAUTHORIZED);
        }
        return Result.ok(appRealnameService.get(UserHolder.getUser().getId()));
    }

    @PostMapping("/submit")
    public Result submit(@RequestBody Map<String, Object> body) {
        if (UserHolder.getUser() == null) {
            return Result.fail(StatusCode.UNAUTHORIZED);
        }
        String realName = body.get("realName") == null ? null : body.get("realName").toString();
        Integer idType = body.get("idType") == null ? 1 : Integer.parseInt(body.get("idType").toString());
        String idCard = body.get("idCard") == null ? null : body.get("idCard").toString();
        String phone = body.get("phone") == null ? null : body.get("phone").toString();
        return Result.ok(appRealnameService.submit(UserHolder.getUser().getId(), realName, idType, idCard, phone));
    }
}