package com.ruoyi.web.controller.buddy;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ruoyi.buddy.common.bean.DoAchievement;
import com.ruoyi.buddy.common.service.DoAchievementService;
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
 * 成就定义接口
 */
@Api(tags = "成就定义")
@RestController
@RequestMapping("/common/do-achievement")
public class DoAchievementController extends BaseController {

    @Autowired
    private DoAchievementService doAchievementService;

    /**
     * 分页查询成就
     */
    @ApiOperation("分页查询成就")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "pageNum", value = "页码", paramType = "query"),
            @ApiImplicitParam(name = "pageSize", value = "每页数量", paramType = "query")
    })
    @GetMapping("/list")
    public TableDataInfo list(DoAchievement query) {
        startPage();
        return getDataTable(doAchievementService.list(new QueryWrapper<>(query)));
    }

    /**
     * 获取成就详情
     */
    @ApiOperation("根据ID获取成就详情")
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id) {
        return success(doAchievementService.getById(id));
    }

    /**
     * 新增成就
     */
    @ApiOperation("新增成就")
    @Log(title = "成就定义", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody DoAchievement body) {
        return toAjax(doAchievementService.save(body));
    }

    /**
     * 修改成就
     */
    @ApiOperation("修改成就")
    @Log(title = "成就定义", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody DoAchievement body) {
        return toAjax(doAchievementService.updateById(body));
    }

    /**
     * 删除成就
     */
    @ApiOperation("根据ID删除成就")
    @Log(title = "成就定义", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable("id") Long id) {
        return toAjax(doAchievementService.removeById(id));
    }

    /**
     * 批量删除成就
     */
    @ApiOperation("批量删除成就")
    @Log(title = "成就定义", businessType = BusinessType.DELETE)
    @DeleteMapping("/batch/{ids}")
    public AjaxResult removeBatch(@PathVariable("ids") Long[] ids) {
        return toAjax(doAchievementService.removeByIds(Arrays.asList(ids)));
    }
}
