package com.crm.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.hutool.core.util.StrUtil;
import com.crm.system.domain.SysDict;
import com.crm.system.domain.SysDictItem;
import com.crm.system.mapper.SysDictItemMapper;
import com.crm.system.mapper.SysDictMapper;
import com.crm.system.service.ISysDictItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * 字典项 业务层处理
 * 
 * @author CRM System
 */
@Service
public class SysDictItemServiceImpl extends ServiceImpl<SysDictItemMapper, SysDictItem> implements ISysDictItemService {
    
    @Autowired
    private SysDictItemMapper dictItemMapper;
    
    @Autowired
    private SysDictMapper dictMapper;

    /**
     * 根据条件分页查询字典项数据
     * 
     * @param dictItem 字典项信息
     * @return 字典项数据集合信息
     */
    @Override
    public List<SysDictItem> selectDictItemList(SysDictItem dictItem) {
        LambdaQueryWrapper<SysDictItem> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(dictItem.getDictId() != null, SysDictItem::getDictId, dictItem.getDictId())
                   .like(StrUtil.isNotEmpty(dictItem.getItemKey()), SysDictItem::getItemKey, dictItem.getItemKey())
                   .like(StrUtil.isNotEmpty(dictItem.getItemLabel()), SysDictItem::getItemLabel, dictItem.getItemLabel())
                   .eq(dictItem.getStatus() != null, SysDictItem::getStatus, dictItem.getStatus())
                   .orderByAsc(SysDictItem::getSortOrder)
                   .orderByAsc(SysDictItem::getId);
        return dictItemMapper.selectList(queryWrapper);
    }

    /**
     * 根据字典ID查询字典项
     * 
     * @param dictId 字典ID
     * @return 字典项列表
     */
    @Override
    public List<SysDictItem> selectDictItemByDictId(Long dictId) {
        LambdaQueryWrapper<SysDictItem> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysDictItem::getDictId, dictId)
                   .eq(SysDictItem::getStatus, 1)
                   .orderByAsc(SysDictItem::getSortOrder)
                   .orderByAsc(SysDictItem::getId);
        return dictItemMapper.selectList(queryWrapper);
    }

    /**
     * 根据字典类型查询字典项
     * 
     * @param dictKey 字典类型
     * @return 字典项列表
     */
    @Override
    public List<SysDictItem> selectDictItemByDictKey(String dictKey) {
        // 先根据dictKey查询字典主表获取dictId
        LambdaQueryWrapper<SysDict> dictQueryWrapper = new LambdaQueryWrapper<>();
        dictQueryWrapper.eq(SysDict::getDictKey, dictKey)
                       .eq(SysDict::getStatus, 1);
        SysDict dict = dictMapper.selectOne(dictQueryWrapper);
        
        if (dict == null) {
            return null;
        }
        
        // 根据dictId查询字典项
        return selectDictItemByDictId(dict.getId());
    }

    /**
     * 新增保存字典项信息
     * 
     * @param dictItem 字典项信息
     * @return 结果
     */
    @Override
    public int insertDictItem(SysDictItem dictItem) {
        return dictItemMapper.insert(dictItem);
    }

    /**
     * 修改保存字典项信息
     * 
     * @param dictItem 字典项信息
     * @return 结果
     */
    @Override
    public int updateDictItem(SysDictItem dictItem) {
        return dictItemMapper.updateById(dictItem);
    }

    /**
     * 批量删除字典项信息
     * 
     * @param dictItemIds 需要删除的字典项ID
     * @return 结果
     */
    @Override
    public void deleteDictItemByIds(Long[] dictItemIds) {
        dictItemMapper.deleteBatchIds(Arrays.asList(dictItemIds));
    }

    /**
     * 根据字典ID删除字典项信息
     * 
     * @param dictId 字典ID
     * @return 结果
     */
    @Override
    public void deleteDictItemByDictId(Long dictId) {
        LambdaQueryWrapper<SysDictItem> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysDictItem::getDictId, dictId);
        dictItemMapper.delete(queryWrapper);
    }
}