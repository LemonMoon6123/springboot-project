package com.scaffold.controller.user;

import com.scaffold.common.AuthContext;
import com.scaffold.common.Result;
import com.scaffold.entity.UserAddress;
import com.scaffold.service.AddressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user/addresses")
@Tag(name = "U04-用户端-收货地址", description = "收货地址增删改查、设置默认地址")
public class UserAddressController {

    @Autowired
    private AddressService addressService;

    @GetMapping
    @Operation(summary = "地址列表", description = "查询当前用户全部收货地址;需登录")
    public Result<List<UserAddress>> list(@Parameter(hidden = true) HttpServletRequest request) {
        return Result.ok(addressService.list(AuthContext.requireUser(request)));
    }

    @PostMapping
    @Operation(summary = "新增地址", description = "新增收货地址;需登录")
    public Result<UserAddress> create(@Parameter(hidden = true) HttpServletRequest request, @RequestBody UserAddress address) {
        return Result.ok(addressService.create(AuthContext.requireUser(request), address));
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改地址", description = "修改指定收货地址;需登录")
    public Result<UserAddress> update(@Parameter(hidden = true) HttpServletRequest request, @PathVariable Long id,
                                      @RequestBody UserAddress address) {
        return Result.ok(addressService.update(AuthContext.requireUser(request), id, address));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除地址", description = "删除指定收货地址;需登录")
    public Result<Void> delete(@Parameter(hidden = true) HttpServletRequest request, @PathVariable Long id) {
        addressService.delete(AuthContext.requireUser(request), id);
        return Result.ok();
    }

    @PutMapping("/{id}/default")
    @Operation(summary = "设为默认地址", description = "将指定地址设为默认;需登录")
    public Result<UserAddress> setDefault(@Parameter(hidden = true) HttpServletRequest request, @PathVariable Long id) {
        return Result.ok(addressService.setDefault(AuthContext.requireUser(request), id));
    }
}
