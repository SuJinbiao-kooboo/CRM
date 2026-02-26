package com.ruoyi.web.controller.buddy;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ruoyi.buddy.common.bean.DoPlan;
import com.ruoyi.buddy.common.service.DoPlanService;
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
 * 计划主表接口
 */
@Api(tags = "计划主表")
@RestController
@RequestMapping("/common/do-plan")
public class DoPlanController extends BaseController {

    @Autowired
    private DoPlanService doPlanService;

    /**
     * 分页查询计划
     */
    @ApiOperation("分页查询计划")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "pageNum", value = "页码", paramType = "query"),
            @ApiImplicitParam(name = "pageSize", value = "每页数量", paramType = "query")
    })
    @GetMapping("/list")
    public TableDataInfo list(DoPlan query) {
        startPage();
        return getDataTable(doPlanService.list(new QueryWrapper<>(query)));
    }

    /**
     * 获取计划详情
     */
    @ApiOperation("根据ID获取计划详情")
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id) {
        return success(doPlanService.getById(id));
    }

    /**
     * 新增计划
     */
    @ApiOperation("新增计划")
    @Log(title = "计划主表", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody DoPlan body) {
        return toAjax(doPlanService.save(body));
    }

    /**
     * 修改计划
     */
    @ApiOperation("修改计划")
    @Log(title = "计划主表", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody DoPlan body) {
        return toAjax(doPlanService.updateById(body));
    }

    /**
     * 删除计划
     */
    @ApiOperation("根据ID删除计划")
    @Log(title = "计划主表", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable("id") Long id) {
        return toAjax(doPlanService.removeById(id));
    }

    /**
     * 批量删除计划
     */
    @ApiOperation("批量删除计划")
    @Log(title = "计划主表", businessType = BusinessType.DELETE)
    @DeleteMapping("/batch/{ids}")
    public AjaxResult removeBatch(@PathVariable("ids") Long[] ids) {
        return toAjax(doPlanService.removeByIds(Arrays.asList(ids)));
    }
}
