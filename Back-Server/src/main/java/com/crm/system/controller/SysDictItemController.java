package com.crm.system.controller;

import com.crm.common.core.controller.BaseController;
import com.crm.common.core.domain.AjaxResult;
import com.crm.common.core.page.TableDataInfo;
import com.crm.system.domain.SysDictItem;
import com.crm.system.service.ISysDictItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 数据字典项信息
 * 
 * @author CRM System
 */
@RestController
@RequestMapping("/system/dict/data")
public class SysDictItemController extends BaseController {
    @Autowired
    private ISysDictItemService dictItemService;

    /**
     * 查询字典项列表
     */
    @GetMapping("/list")
    public TableDataInfo list(SysDictItem dictItem) {
        List<SysDictItem> list = dictItemService.selectDictItemList(dictItem);
        return getDataTable(list);
    }

    /**
     * 根据字典ID查询字典项列表
     */
    @GetMapping("/dict/{dictId}")
    public AjaxResult listByDictId(@PathVariable Long dictId) {
        List<SysDictItem> list = dictItemService.selectDictItemByDictId(dictId);
        return success(list);
    }

    /**
     * 根据字典类型查询字典项列表
     */
    @GetMapping("/dictKey/{dictKey}")
    public AjaxResult listByDictKey(@PathVariable String dictKey) {
        List<SysDictItem> list = dictItemService.selectDictItemByDictKey(dictKey);
        return success(list);
    }

    /**
     * 查询字典项详细
     */
    @GetMapping(value = "/{dictItemId}")
    public AjaxResult getInfo(@PathVariable Long dictItemId) {
        return success(dictItemService.getById(dictItemId));
    }

    /**
     * 新增字典项
     */
    @PostMapping
    public AjaxResult add(@Validated @RequestBody SysDictItem dictItem) {
        return toAjax(dictItemService.insertDictItem(dictItem));
    }

    /**
     * 修改字典项
     */
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody SysDictItem dictItem) {
        return toAjax(dictItemService.updateDictItem(dictItem));
    }

    /**
     * 删除字典项
     */
    @DeleteMapping("/{dictItemIds}")
    public AjaxResult remove(@PathVariable Long[] dictItemIds) {
        dictItemService.deleteDictItemByIds(dictItemIds);
        return success();
    }
}