package com.crm.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crm.inventory.domain.InvStock;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface InvStockMapper extends BaseMapper<InvStock> {
    /**
     * 查询库存信息列表
     * @param invStock 库存信息
     * @return 库存信息集合
     */
    List<InvStock> selectInvStockList(InvStock invStock);

    /**
     * 批量插入库存数据
     * @param stockList 库存列表
     * @return 插入数量
     */
    int batchInsert(List<InvStock> stockList);

    /**
     * 批量更新库存信息
     * @param ids 要更新的ID列表
     * @param updateEntity 更新实体
     * @return 更新数量
     */
    int batchUpdate(@Param("ids") List<Long> ids, @Param("updateEntity") InvStock updateEntity);
}