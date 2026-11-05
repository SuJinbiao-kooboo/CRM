package com.ruoyi.system.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.common.core.dict.DictDataLoader;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.system.mapper.SysDictDataMapper;

/**
 * 数据字典缓存回源加载器
 *
 * @author ruoyi
 */
@Component
public class SysDictDataLoader implements DictDataLoader
{
    @Autowired
    private SysDictDataMapper dictDataMapper;

    @Override
    public List<SysDictData> load(String dictType)
    {
        return dictDataMapper.selectDictDataByType(dictType);
    }
}
