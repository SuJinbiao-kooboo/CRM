package com.crm.supplier.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crm.supplier.domain.CrmSupplierContact;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/**
 * 供应商联系人Mapper接口
 */
@Mapper
public interface CrmSupplierContactMapper extends BaseMapper<CrmSupplierContact> {
    
    /**
     * 根据供应商ID查询联系人列表
     */
    List<CrmSupplierContact> selectContactsBySupplierId(Long supplierId);
    
    /**
     * 根据供应商ID删除联系人
     */
    int deleteBySupplierId(Long supplierId);
}