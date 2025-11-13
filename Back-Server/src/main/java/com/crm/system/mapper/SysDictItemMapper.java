package com.crm.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crm.system.domain.SysDictItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 字典项表 数据层
 * 
 * @author CRM System
 */
@Mapper
public interface SysDictItemMapper extends BaseMapper<SysDictItem> {
    
}