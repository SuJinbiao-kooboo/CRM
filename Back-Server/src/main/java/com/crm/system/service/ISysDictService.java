package com.crm.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.crm.system.domain.SysDict;

import java.util.List;

/**
 * 字典 业务层
 * 
 * @author CRM System
 */
public interface ISysDictService extends IService<SysDict> {
    /**
     * 根据条件分页查询字典数据
     * 
     * @param dict 字典信息
     * @return 字典数据集合信息
     */
    List<SysDict> selectDictList(SysDict dict);

    /**
     * 根据字典类型查询字典数据
     * 
     * @param dictKey 字典类型
     * @return 字典数据集合信息
     */
    List<SysDict> selectDictDataByType(String dictKey);

    /**
     * 根据字典类型和字典键值查询字典数据信息
     * 
     * @param dictKey 字典类型
     * @param dictValue 字典键值
     * @return 字典标签
     */
    String selectDictLabel(String dictKey, String dictValue);

    /**
     * 新增保存字典信息
     * 
     * @param dict 字典信息
     * @return 结果
     */
    int insertDict(SysDict dict);

    /**
     * 修改保存字典信息
     * 
     * @param dict 字典信息
     * @return 结果
     */
    int updateDict(SysDict dict);

    /**
     * 批量删除字典信息
     * 
     * @param dictIds 需要删除的字典ID
     * @return 结果
     */
    void deleteDictByIds(Long[] dictIds);

    /**
     * 清空缓存数据
     */
    void clearDictCache();

    /**
     * 重置字典缓存数据
     */
    void resetDictCache();

    /**
     * 获取字典选择框列表
     * 
     * @return 字典列表
     */
    List<SysDict> selectDictOptionselect();
}