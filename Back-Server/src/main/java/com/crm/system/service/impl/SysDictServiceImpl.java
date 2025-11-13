package com.crm.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.hutool.core.util.StrUtil;
import com.crm.system.domain.SysDict;
import com.crm.system.mapper.SysDictMapper;
import com.crm.system.service.ISysDictService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * 字典 业务层处理
 * 
 * @author CRM System
 */
@Service
public class SysDictServiceImpl extends ServiceImpl<SysDictMapper, SysDict> implements ISysDictService {
    
    @Autowired
    private SysDictMapper dictMapper;

    /**
     * 根据条件分页查询字典数据
     * 
     * @param dict 字典信息
     * @return 字典数据集合信息
     */
    @Override
    public List<SysDict> selectDictList(SysDict dict) {
        return dictMapper.selectSysDictList(dict);
    }

    /**
     * 根据字典类型查询字典数据
     * 
     * @param dictKey 字典类型
     * @return 字典数据集合信息
     */
    @Override
    public List<SysDict> selectDictDataByType(String dictKey) {
        LambdaQueryWrapper<SysDict> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysDict::getDictKey, dictKey)
                   .eq(SysDict::getStatus, 1)
                   .orderByAsc(SysDict::getId);
        return dictMapper.selectList(queryWrapper);
    }

    /**
     * 根据字典类型和字典键值查询字典数据信息
     * 
     * @param dictKey 字典类型
     * @param dictValue 字典键值
     * @return 字典标签
     */
    @Override
    public String selectDictLabel(String dictKey, String dictValue) {
        // 这里需要关联查询字典项表，暂时返回空
        // 实际实现需要查询sys_dict_item表
        return "";
    }

    /**
     * 新增保存字典信息
     * 
     * @param dict 字典信息
     * @return 结果
     */
    @Override
    public int insertDict(SysDict dict) {
        return dictMapper.insertSysDict(dict);
    }

    /**
     * 修改保存字典信息
     * 
     * @param dict 字典信息
     * @return 结果
     */
    @Override
    public int updateDict(SysDict dict) {
        return dictMapper.updateSysDict(dict);
    }

    /**
     * 批量删除字典信息
     * 
     * @param dictIds 需要删除的字典ID
     * @return 结果
     */
    @Override
    public void deleteDictByIds(Long[] dictIds) {
        dictMapper.deleteSysDictByIds(dictIds);
    }

    /**
     * 清空缓存数据
     */
    @Override
    public void clearDictCache() {
        // 清空字典缓存，这里可以集成Redis缓存
        // 暂时不实现缓存功能
    }

    /**
     * 重置字典缓存数据
     */
    @Override
    public void resetDictCache() {
        // 重置字典缓存，这里可以集成Redis缓存
        // 暂时不实现缓存功能
    }

    /**
     * 获取字典选择框列表
     * 
     * @return 字典列表
     */
    @Override
    public List<SysDict> selectDictOptionselect() {
        LambdaQueryWrapper<SysDict> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.select(SysDict::getId, SysDict::getDictName, SysDict::getDictKey)
                   .eq(SysDict::getStatus, 1)
                   .orderByAsc(SysDict::getId);
        return dictMapper.selectList(queryWrapper);
    }
}