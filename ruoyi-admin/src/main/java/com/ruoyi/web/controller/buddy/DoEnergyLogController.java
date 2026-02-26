package com.ruoyi.web.controller.buddy;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ruoyi.buddy.common.bean.DoEnergyLog;
import com.ruoyi.buddy.common.service.DoEnergyLogService;
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
 * 能量流水接口
 */
@Api(tags = "能量流水")
@RestController
@RequestMapping("/common/do-energy-log")
public class DoEnergyLogController extends BaseController {

    @Autowired
    private DoEnergyLogService doEnergyLogService;

    /**
     * 分页查询能量流水
     */
    @ApiOperation("分页查询能量流水")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "pageNum", value = "页码", paramType = "query"),
            @ApiImplicitParam(name = "pageSize", value = "每页数量", paramType = "query")
    })
    @GetMapping("/list")
    public TableDataInfo list(DoEnergyLog query) {
        startPage();
        return getDataTable(doEnergyLogService.list(new QueryWrapper<>(query)));
    }

    /**
     * 获取流水详情
     */
    @ApiOperation("根据ID获取流水详情")
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id) {
        return success(doEnergyLogService.getById(id));
    }

    /**
     * 新增能量流水
     */
    @ApiOperation("新增能量流水")
    @Log(title = "能量流水", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody DoEnergyLog body) {
        return toAjax(doEnergyLogService.save(body));
    }

    /**
     * 修改能量流水
     */
    @ApiOperation("修改能量流水")
    @Log(title = "能量流水", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody DoEnergyLog body) {
        return toAjax(doEnergyLogService.updateById(body));
    }

    /**
     * 删除能量流水
     */
    @ApiOperation("根据ID删除能量流水")
    @Log(title = "能量流水", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable("id") Long id) {
        return toAjax(doEnergyLogService.removeById(id));
    }

    /**
     * 批量删除能量流水
     */
    @ApiOperation("批量删除能量流水")
    @Log(title = "能量流水", businessType = BusinessType.DELETE)
    @DeleteMapping("/batch/{ids}")
    public AjaxResult removeBatch(@PathVariable("ids") Long[] ids) {
        return toAjax(doEnergyLogService.removeByIds(Arrays.asList(ids)));
    }
}
