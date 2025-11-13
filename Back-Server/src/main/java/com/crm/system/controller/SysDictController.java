package com.crm.system.controller;

import com.crm.common.core.controller.BaseController;
import com.crm.common.core.domain.AjaxResult;
import com.crm.common.core.page.TableDataInfo;
import com.crm.system.domain.SysDict;
import com.crm.system.service.ISysDictService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 数据字典信息
 * 
 * @author CRM System
 */
@RestController
@RequestMapping("/system/dict")
public class SysDictController extends BaseController {
    @Autowired
    private ISysDictService dictService;

    /**
     * 查询字典类型列表
     */
    @GetMapping("/list")
    public TableDataInfo list(SysDict dict) {
        startPage();
        List<SysDict> list = dictService.selectDictList(dict);
        return getDataTable(list);
    }

    /**
     * 根据字典类型查询字典数据信息
     */
    @GetMapping(value = "/data/{dictKey}")
    public AjaxResult dictData(@PathVariable String dictKey) {
        List<SysDict> data = dictService.selectDictDataByType(dictKey);
        return success(data);
    }

    /**
     * 查询字典类型详细
     */
    @GetMapping(value = "/{dictId}")
    public AjaxResult getInfo(@PathVariable Long dictId) {
        return success(dictService.getById(dictId));
    }

    /**
     * 新增字典类型
     */
    @PostMapping
    public AjaxResult add(@Validated @RequestBody SysDict dict) {
        return toAjax(dictService.insertDict(dict));
    }

    /**
     * 修改字典类型
     */
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody SysDict dict) {
        return toAjax(dictService.updateDict(dict));
    }

    /**
     * 删除字典类型
     */
    @DeleteMapping("/{dictIds}")
    public AjaxResult remove(@PathVariable Long[] dictIds) {
        dictService.deleteDictByIds(dictIds);
        return success();
    }

    /**
     * 刷新字典缓存
     */
    @DeleteMapping("/refreshCache")
    public AjaxResult refreshCache() {
        dictService.resetDictCache();
        return success();
    }

    /**
     * 获取字典选择框列表
     */
    @GetMapping("/optionselect")
    public AjaxResult optionselect() {
        List<SysDict> list = dictService.selectDictOptionselect();
        return success(list);
    }
}