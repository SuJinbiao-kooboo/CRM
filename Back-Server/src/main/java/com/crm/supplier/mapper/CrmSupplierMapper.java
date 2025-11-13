package com.crm.supplier.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crm.supplier.domain.CrmSupplier;
import com.crm.supplier.domain.SupplierVO;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/**
 * 供应商信息Mapper接口
 * 
 * @author crm
 * @date 2024-01-01
 */
@Mapper
public interface CrmSupplierMapper extends BaseMapper<CrmSupplier> {
    
    /**
     * 查询供应商列表（包含联系人信息）
     */
    List<SupplierVO> selectSupplierListWithContact(SupplierVO supplierVO);
}