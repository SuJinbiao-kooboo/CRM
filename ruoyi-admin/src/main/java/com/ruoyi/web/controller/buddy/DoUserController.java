package com.ruoyi.web.controller.buddy;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ruoyi.buddy.common.bean.DoUser;
import com.ruoyi.buddy.common.service.DoUserService;
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
 * 用户主表接口
 * 提供基本的增删改查能力：分页查询、单个查询、单个/批量删除、单个新增、单个修改
 */
@Api(tags = "用户主表")
@RestController
@RequestMapping("/common/do-user")
public class DoUserController extends BaseController {

    @Autowired
    private DoUserService doUserService;

    /**
     * 分页查询用户列表
     */
    @ApiOperation("分页查询用户列表")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "pageNum", value = "页码", paramType = "query"),
            @ApiImplicitParam(name = "pageSize", value = "每页数量", paramType = "query")
    })
    @GetMapping("/list")
    public TableDataInfo list(DoUser query) {
        startPage();
        return getDataTable(doUserService.list(new QueryWrapper<>(query)));
    }

    /**
     * 根据ID获取用户详情
     */
    @ApiOperation("根据ID获取用户详情")
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id) {
        return success(doUserService.getById(id));
    }

    /**
     * 新增用户
     */
    @ApiOperation("新增用户")
    @Log(title = "用户主表", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody DoUser body) {
        return toAjax(doUserService.save(body));
    }

    /**
     * 修改用户
     */
    @ApiOperation("修改用户")
    @Log(title = "用户主表", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody DoUser body) {
        return toAjax(doUserService.updateById(body));
    }

    /**
     * 根据ID删除用户
     */
    @ApiOperation("根据ID删除用户")
    @Log(title = "用户主表", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable("id") Long id) {
        return toAjax(doUserService.removeById(id));
    }

    /**
     * 批量删除用户
     */
    @ApiOperation("批量删除用户")
    @Log(title = "用户主表", businessType = BusinessType.DELETE)
    @DeleteMapping("/batch/{ids}")
    public AjaxResult removeBatch(@PathVariable("ids") Long[] ids) {
        return toAjax(doUserService.removeByIds(Arrays.asList(ids)));
    }
}
