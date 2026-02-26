package com.ruoyi.web.controller.buddy;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ruoyi.buddy.common.bean.DoUserSetting;
import com.ruoyi.buddy.common.service.DoUserSettingService;
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
 * 用户设置接口
 * 提供分页查询、单个查询、单个/批量删除、单个新增、单个修改
 */
@Api(tags = "用户设置")
@RestController
@RequestMapping("/common/do-user-setting")
public class DoUserSettingController extends BaseController {

    @Autowired
    private DoUserSettingService doUserSettingService;

    /**
     * 分页查询用户设置
     */
    @ApiOperation("分页查询用户设置")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "pageNum", value = "页码", paramType = "query"),
            @ApiImplicitParam(name = "pageSize", value = "每页数量", paramType = "query")
    })
    @GetMapping("/list")
    public TableDataInfo list(DoUserSetting query) {
        startPage();
        return getDataTable(doUserSettingService.list(new QueryWrapper<>(query)));
    }

    /**
     * 根据用户ID获取设置
     */
    @ApiOperation("根据用户ID获取设置")
    @GetMapping("/{userId}")
    public AjaxResult getInfo(@PathVariable("userId") Long userId) {
        return success(doUserSettingService.getById(userId));
    }

    /**
     * 新增用户设置
     */
    @ApiOperation("新增用户设置")
    @Log(title = "用户设置", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody DoUserSetting body) {
        return toAjax(doUserSettingService.save(body));
    }

    /**
     * 修改用户设置
     */
    @ApiOperation("修改用户设置")
    @Log(title = "用户设置", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody DoUserSetting body) {
        return toAjax(doUserSettingService.updateById(body));
    }

    /**
     * 根据用户ID删除设置
     */
    @ApiOperation("根据用户ID删除设置")
    @Log(title = "用户设置", businessType = BusinessType.DELETE)
    @DeleteMapping("/{userId}")
    public AjaxResult remove(@PathVariable("userId") Long userId) {
        return toAjax(doUserSettingService.removeById(userId));
    }

    /**
     * 批量删除用户设置
     */
    @ApiOperation("批量删除用户设置")
    @Log(title = "用户设置", businessType = BusinessType.DELETE)
    @DeleteMapping("/batch/{userIds}")
    public AjaxResult removeBatch(@PathVariable("userIds") Long[] userIds) {
        return toAjax(doUserSettingService.removeByIds(Arrays.asList(userIds)));
    }
}
