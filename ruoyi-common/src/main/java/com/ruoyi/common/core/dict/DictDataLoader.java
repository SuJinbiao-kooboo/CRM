package com.ruoyi.common.core.dict;

import java.util.List;
import com.ruoyi.common.core.domain.entity.SysDictData;

/**
 * 字典数据加载器
 *
 * @author ruoyi
 */
public interface DictDataLoader
{
    /**
     * 根据字典类型从数据源加载字典数据
     *
     * @param dictType 字典类型
     * @return 字典数据列表
     */
    List<SysDictData> load(String dictType);
}
