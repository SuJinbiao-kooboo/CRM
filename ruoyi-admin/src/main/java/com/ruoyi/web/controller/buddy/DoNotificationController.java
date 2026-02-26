package com.ruoyi.web.controller.buddy;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ruoyi.buddy.common.bean.DoNotification;
import com.ruoyi.buddy.common.service.DoNotificationService;
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
 * 通知接口
 */
@Api(tags = "通知")
@RestController
@RequestMapping("/common/do-notification")
public class DoNotificationController extends BaseController {

    @Autowired
    private DoNotificationService doNotificationService;

    /**
     * 分页查询通知
     */
    @ApiOperation("分页查询通知")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "pageNum", value = "页码", paramType = "query"),
            @ApiImplicitParam(name = "pageSize", value = "每页数量", paramType = "query")
    })
    @GetMapping("/list")
    public TableDataInfo list(DoNotification query) {
        startPage();
        return getDataTable(doNotificationService.list(new QueryWrapper<>(query)));
    }

    /**
     * 获取通知详情
     */
    @ApiOperation("根据ID获取通知详情")
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id) {
        return success(doNotificationService.getById(id));
    }

    /**
     * 新增通知
     */
    @ApiOperation("新增通知")
    @Log(title = "通知", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody DoNotification body) {
        return toAjax(doNotificationService.save(body));
    }

    /**
     * 修改通知
     */
    @ApiOperation("修改通知")
    @Log(title = "通知", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody DoNotification body) {
        return toAjax(doNotificationService.updateById(body));
    }

    /**
     * 删除通知
     */
    @ApiOperation("根据ID删除通知")
    @Log(title = "通知", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable("id") Long id) {
        return toAjax(doNotificationService.removeById(id));
    }

    /**
     * 批量删除通知
     */
    @ApiOperation("批量删除通知")
    @Log(title = "通知", businessType = BusinessType.DELETE)
    @DeleteMapping("/batch/{ids}")
    public AjaxResult removeBatch(@PathVariable("ids") Long[] ids) {
        return toAjax(doNotificationService.removeByIds(Arrays.asList(ids)));
    }
}
