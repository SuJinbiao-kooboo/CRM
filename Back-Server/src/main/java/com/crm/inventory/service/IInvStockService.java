package com.crm.inventory.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.crm.inventory.domain.InvStock;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface IInvStockService extends IService<InvStock> {
    
    /**
     * 查询库存信息列表
     * @param invStock 库存信息
     * @return 库存信息集合
     */
    List<InvStock> selectInvStockList(InvStock invStock);

    /**
     * 导入库存数据
     * @param file Excel文件
     * @param columnMapping 列映射关系
     * @return 导入结果
     */
    String importStock(MultipartFile file, Map<String, Integer> columnMapping);

    /**
     * 批量更新库存信息
     * @param ids 要更新的库存ID列表
     * @param updateFields 更新字段映射
     * @return 更新结果
     */
    boolean batchUpdate(List<Long> ids, Map<String, Object> updateFields);
}