package com.ruoyi.web.controller.buddy;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ruoyi.buddy.common.bean.DoCheckin;
import com.ruoyi.buddy.common.service.DoCheckinService;
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
 * 打卡记录接口
 */
@Api(tags = "打卡记录")
@RestController
@RequestMapping("/common/do-checkin")
public class DoCheckinController extends BaseController {

    @Autowired
    private DoCheckinService doCheckinService;

    /**
     * 分页查询打卡记录
     */
    @ApiOperation("分页查询打卡记录")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "pageNum", value = "页码", paramType = "query"),
            @ApiImplicitParam(name = "pageSize", value = "每页数量", paramType = "query")
    })
    @GetMapping("/list")
    public TableDataInfo list(DoCheckin query) {
        startPage();
        return getDataTable(doCheckinService.list(new QueryWrapper<>(query)));
    }

    /**
     * 获取打卡详情
     */
    @ApiOperation("根据ID获取打卡详情")
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id) {
        return success(doCheckinService.getById(id));
    }

    /**
     * 新增打卡记录
     */
    @ApiOperation("新增打卡记录")
    @Log(title = "打卡记录", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody DoCheckin body) {
        return toAjax(doCheckinService.save(body));
    }

    /**
     * 修改打卡记录
     */
    @ApiOperation("修改打卡记录")
    @Log(title = "打卡记录", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody DoCheckin body) {
        return toAjax(doCheckinService.updateById(body));
    }

    /**
     * 删除打卡记录
     */
    @ApiOperation("根据ID删除打卡记录")
    @Log(title = "打卡记录", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable("id") Long id) {
        return toAjax(doCheckinService.removeById(id));
    }

    /**
     * 批量删除打卡记录
     */
    @ApiOperation("批量删除打卡记录")
    @Log(title = "打卡记录", businessType = BusinessType.DELETE)
    @DeleteMapping("/batch/{ids}")
    public AjaxResult removeBatch(@PathVariable("ids") Long[] ids) {
        return toAjax(doCheckinService.removeByIds(Arrays.asList(ids)));
    }
}
