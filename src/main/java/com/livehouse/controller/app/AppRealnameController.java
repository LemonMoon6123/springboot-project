package com.livehouse.controller.app;

import com.livehouse.dto.app.RealnameSubmitRequest;
import com.livehouse.entity.Result;
import com.livehouse.entity.StatusCode;
import com.livehouse.service.app.AppRealnameService;
import com.livehouse.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
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
    public Result submit(@RequestBody RealnameSubmitRequest body) {
        if (UserHolder.getUser() == null) {
            return Result.fail(StatusCode.UNAUTHORIZED);
        }
        if (body == null) {
            return Result.fail(StatusCode.PARAM_ERROR);
        }
        Integer idType = body.getIdType() == null ? 1 : body.getIdType();
        return Result.ok(appRealnameService.submit(UserHolder.getUser().getId(),
                body.getRealName(), idType, body.getIdCard(), body.getPhone()));
    }
}