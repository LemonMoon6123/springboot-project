package com.livehouse.controller.app;

import com.livehouse.dto.app.SearchHistoryKeywordRequest;
import com.livehouse.entity.Result;
import com.livehouse.entity.StatusCode;
import com.livehouse.exception.StatusCodeException;
import com.livehouse.service.app.AppSearchHistoryService;
import com.livehouse.vo.app.search.SearchHistoryListVo;
import com.livehouse.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/app/search/history")
public class AppSearchHistoryController {

    @Resource
    private AppSearchHistoryService appSearchHistoryService;

    @GetMapping
    public Result list() {
        if (UserHolder.getUser() == null) {
            throw new StatusCodeException(StatusCode.UNAUTHORIZED);
        }
        SearchHistoryListVo data = new SearchHistoryListVo();
        data.setRecords(appSearchHistoryService.list(UserHolder.getUser().getId()));
        return Result.ok(data);
    }

    @PostMapping
    public Result add(@RequestBody SearchHistoryKeywordRequest body) {
        if (UserHolder.getUser() == null) {
            throw new StatusCodeException(StatusCode.UNAUTHORIZED);
        }
        String keyword = body == null ? null : body.getKeyword();
        return Result.ok(appSearchHistoryService.add(UserHolder.getUser().getId(), keyword));
    }

    @DeleteMapping
    public Result clear() {
        if (UserHolder.getUser() == null) {
            throw new StatusCodeException(StatusCode.UNAUTHORIZED);
        }
        appSearchHistoryService.clear(UserHolder.getUser().getId());
        return Result.ok();
    }

    @DeleteMapping("/one")
    public Result removeOne(@RequestParam String keyword) {
        if (UserHolder.getUser() == null) {
            throw new StatusCodeException(StatusCode.UNAUTHORIZED);
        }
        appSearchHistoryService.removeOne(UserHolder.getUser().getId(), keyword);
        return Result.ok();
    }
}