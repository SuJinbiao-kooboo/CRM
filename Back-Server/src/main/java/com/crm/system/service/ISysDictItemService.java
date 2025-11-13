package com.crm.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.crm.system.domain.SysDictItem;

import java.util.List;

/**
 * 字典项 业务层
 * 
 * @author CRM System
 */
public interface ISysDictItemService extends IService<SysDictItem> {
    /**
     * 根据条件分页查询字典项数据
     * 
     * @param dictItem 字典项信息
     * @return 字典项数据集合信息
     */
    List<SysDictItem> selectDictItemList(SysDictItem dictItem);

    /**
     * 根据字典ID查询字典项
     * 
     * @param dictId 字典ID
     * @return 字典项列表
     */
    List<SysDictItem> selectDictItemByDictId(Long dictId);

    /**
     * 根据字典类型查询字典项
     * 
     * @param dictKey 字典类型
     * @return 字典项列表
     */
    List<SysDictItem> selectDictItemByDictKey(String dictKey);

    /**
     * 新增保存字典项信息
     * 
     * @param dictItem 字典项信息
     * @return 结果
     */
    int insertDictItem(SysDictItem dictItem);

    /**
     * 修改保存字典项信息
     * 
     * @param dictItem 字典项信息
     * @return 结果
     */
    int updateDictItem(SysDictItem dictItem);

    /**
     * 批量删除字典项信息
     * 
     * @param dictItemIds 需要删除的字典项ID
     * @return 结果
     */
    void deleteDictItemByIds(Long[] dictItemIds);

    /**
     * 根据字典ID删除字典项信息
     * 
     * @param dictId 字典ID
     * @return 结果
     */
    void deleteDictItemByDictId(Long dictId);
}