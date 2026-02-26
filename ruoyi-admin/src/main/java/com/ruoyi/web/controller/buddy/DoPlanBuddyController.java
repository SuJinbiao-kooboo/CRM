package com.ruoyi.web.controller.buddy;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ruoyi.buddy.common.bean.DoPlanBuddy;
import com.ruoyi.buddy.common.service.DoPlanBuddyService;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;

/**
 * 计划-搭子绑定接口
 */
@Api(tags = "计划-搭子绑定")
@RestController
@RequestMapping("/common/do-plan-buddy")
public class DoPlanBuddyController extends BaseController {

    @Autowired
    private DoPlanBuddyService doPlanBuddyService;

    /**
     * 分页查询绑定列表
     */
    @ApiOperation("分页查询绑定列表")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "pageNum", value = "页码", paramType = "query"),
            @ApiImplicitParam(name = "pageSize", value = "每页数量", paramType = "query")
    })
    @GetMapping("/list")
    public TableDataInfo list(DoPlanBuddy query) {
        startPage();
        return getDataTable(doPlanBuddyService.list(new QueryWrapper<>(query)));
    }

    /**
     * 获取绑定详情
     */
    @ApiOperation("根据ID获取绑定详情")
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id) {
        return success(doPlanBuddyService.getById(id));
    }

    /**
     * 新增绑定
     */
    @ApiOperation("新增绑定")
    @Log(title = "计划-搭子绑定", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody DoPlanBuddy body) {
        return toAjax(doPlanBuddyService.save(body));
    }

    /**
     * 修改绑定
     */
    @ApiOperation("修改绑定")
    @Log(title = "计划-搭子绑定", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody DoPlanBuddy body) {
        return toAjax(doPlanBuddyService.updateById(body));
    }

    /**
     * 删除绑定
     */
    @ApiOperation("根据ID删除绑定")
    @Log(title = "计划-搭子绑定", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable("id") Long id) {
        return toAjax(doPlanBuddyService.removeById(id));
    }

    /**
     * 批量删除绑定
     */
    @ApiOperation("批量删除绑定")
    @Log(title = "计划-搭子绑定", businessType = BusinessType.DELETE)
    @DeleteMapping("/batch/{ids}")
    public AjaxResult removeBatch(@PathVariable("ids") Long[] ids) {
        return toAjax(doPlanBuddyService.removeByIds(Arrays.asList(ids)));
    }
}
