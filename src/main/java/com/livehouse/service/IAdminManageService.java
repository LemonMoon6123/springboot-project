package com.livehouse.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.livehouse.admin.AdminModuleMeta;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 后台通用 CRUD 服务
 */
public interface IAdminManageService {

    List<AdminModuleMeta> listModules();

    IPage<?> page(String module, long current, long size, String keyword);

    Object getById(String module, Serializable id);

    boolean create(String module, Map<String, Object> body);

    boolean update(String module, Serializable id, Map<String, Object> body);

    boolean delete(String module, Serializable id);

    boolean updateStatus(String module, Serializable id, Object status);

    /**
     * 下拉选项（value=主键，label=展示文案）
     */
    List<Map<String, Object>> selectOptions(String module, String keyword, int limit);
}