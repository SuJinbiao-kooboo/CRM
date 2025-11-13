package com.crm.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crm.system.domain.SysDict;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 字典表 数据层
 * 
 * @author CRM System
 */
@Mapper
public interface SysDictMapper extends BaseMapper<SysDict> {
    
    /**
     * 查询字典列表
     * 
     * @param dict 字典信息
     * @return 字典集合
     */
    List<SysDict> selectSysDictList(SysDict dict);

    /**
     * 根据ID查询字典
     * 
     * @param id 字典ID
     * @return 字典信息
     */
    SysDict selectSysDictById(Long id);

    /**
     * 新增字典
     * 
     * @param dict 字典信息
     * @return 结果
     */
    int insertSysDict(SysDict dict);

    /**
     * 修改字典
     * 
     * @param dict 字典信息
     * @return 结果
     */
    int updateSysDict(SysDict dict);

    /**
     * 删除字典
     * 
     * @param id 字典ID
     * @return 结果
     */
    int deleteSysDictById(Long id);

    /**
     * 批量删除字典
     * 
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    int deleteSysDictByIds(Long[] ids);
}