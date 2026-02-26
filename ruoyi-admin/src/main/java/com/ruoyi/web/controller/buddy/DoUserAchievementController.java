package com.ruoyi.web.controller.buddy;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ruoyi.buddy.common.bean.DoUserAchievement;
import com.ruoyi.buddy.common.service.DoUserAchievementService;
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
 * 用户成就记录接口
 */
@Api(tags = "用户成就记录")
@RestController
@RequestMapping("/common/do-user-achievement")
public class DoUserAchievementController extends BaseController {

    @Autowired
    private DoUserAchievementService doUserAchievementService;

    /**
     * 分页查询用户成就
     */
    @ApiOperation("分页查询用户成就")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "pageNum", value = "页码", paramType = "query"),
            @ApiImplicitParam(name = "pageSize", value = "每页数量", paramType = "query")
    })
    @GetMapping("/list")
    public TableDataInfo list(DoUserAchievement query) {
        startPage();
        return getDataTable(doUserAchievementService.list(new QueryWrapper<>(query)));
    }

    /**
     * 获取用户成就详情
     */
    @ApiOperation("根据ID获取用户成就详情")
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id) {
        return success(doUserAchievementService.getById(id));
    }

    /**
     * 新增用户成就
     */
    @ApiOperation("新增用户成就")
    @Log(title = "用户成就记录", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody DoUserAchievement body) {
        return toAjax(doUserAchievementService.save(body));
    }

    /**
     * 修改用户成就
     */
    @ApiOperation("修改用户成就")
    @Log(title = "用户成就记录", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody DoUserAchievement body) {
        return toAjax(doUserAchievementService.updateById(body));
    }

    /**
     * 删除用户成就
     */
    @ApiOperation("根据ID删除用户成就")
    @Log(title = "用户成就记录", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable("id") Long id) {
        return toAjax(doUserAchievementService.removeById(id));
    }

    /**
     * 批量删除用户成就
     */
    @ApiOperation("批量删除用户成就")
    @Log(title = "用户成就记录", businessType = BusinessType.DELETE)
    @DeleteMapping("/batch/{ids}")
    public AjaxResult removeBatch(@PathVariable("ids") Long[] ids) {
        return toAjax(doUserAchievementService.removeByIds(Arrays.asList(ids)));
    }
}
