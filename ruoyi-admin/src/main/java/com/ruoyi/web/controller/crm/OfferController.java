package com.ruoyi.web.controller.crm;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletResponse;

import com.ruoyi.crm.service.util.SimpleTextParser;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.crm.domain.CrmOffer;
import com.ruoyi.crm.service.ICrmOfferService;

@RestController
@RequestMapping("/crm/offer")
public class OfferController extends BaseController {

    @Autowired
    private ICrmOfferService offerService;


    @PreAuthorize("@ss.hasPermi('crm:offer:list')")
    @GetMapping("/list")
    public TableDataInfo list(@ApiParam CrmOffer offer) {
        startPage();
        List<CrmOffer> list = offerService.selectOfferList(offer);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('crm:offer:export')")
    @Log(title = "Offer管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, CrmOffer offer, @RequestParam(value = "ids", required = false) Long[] ids) {
        List<CrmOffer> list = offerService.selectOfferList(offer);
        if (ids != null && ids.length > 0) {
            list.removeIf(o -> !contains(ids, o.getId()));
        }
        ExcelUtil<CrmOffer> util = new ExcelUtil<>(CrmOffer.class);
        util.exportExcel(response, list, "Offer数据");
    }

    private boolean contains(Long[] ids, Long id) {
        for (Long v : ids) { if (v != null && v.equals(id)) return true; }
        return false;
    }

    @PreAuthorize("@ss.hasPermi('crm:offer:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable Long id) {
        return AjaxResult.success(offerService.selectOfferById(id));
    }
    @PreAuthorize("@ss.hasPermi('crm:offer:add')")
    @Log(title = "Offer管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody CrmOffer offer) {
        offer.setCreateBy(SecurityUtils.getUsername());
        return toAjax(offerService.insertOffer(offer));
    }

    @PreAuthorize("@ss.hasPermi('crm:offer:edit')")
    @Log(title = "Offer管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody CrmOffer offer) {
        offer.setUpdateBy(SecurityUtils.getUsername());
        return toAjax(offerService.updateOffer(offer));
    }

    @PreAuthorize("@ss.hasPermi('crm:offer:remove')")
    @Log(title = "Offer管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids) {
        return toAjax(offerService.deleteOfferByIds(ids));
    }

    @PreAuthorize("@ss.hasPermi('crm:offer:batchEdit')")
    @Log(title = "Offer管理", businessType = BusinessType.UPDATE)
    @PostMapping("/batchEdit")
    public AjaxResult batchEdit(@RequestParam("ids") String ids, @RequestBody CrmOffer offer) {
        offer.setUpdateBy(SecurityUtils.getUsername());
        java.util.List<Long> idList = new java.util.ArrayList<>();
        if (ids != null && !ids.isEmpty()) {
            for (String s : ids.split(",")) {
                try { idList.add(Long.valueOf(s.trim())); } catch (Exception ignored) {}
            }
        }
        return toAjax(offerService.batchUpdate(idList, offer));
    }

    @PreAuthorize("@ss.hasPermi('crm:offer:import')")
    @Log(title = "Offer管理", businessType = BusinessType.IMPORT)
    @PostMapping("/import")
    public AjaxResult importData(@RequestParam("file") MultipartFile file,
                                 @RequestParam("supplierCode") String supplierCode,
                                 @RequestParam("supplierName") String supplierName,
                                 @RequestParam("inqOfferType") String inqOfferType,
                                 @RequestParam("colMapJson") String colMapJson) {
        Map<String, String> colMap = new HashMap<>();
        if (colMapJson != null && !colMapJson.isEmpty()) {
            colMap = JSON.parseObject(colMapJson, Map.class);
        }
        Map<String, Object> result = offerService.importOffers(file, supplierCode, supplierName, inqOfferType, colMap);
        String msg = "成功导入" + result.getOrDefault("successCount", 0) + "条，失败" + result.getOrDefault("failCount", 0) + "条";
        return AjaxResult.success(msg, result);
    }

    @PreAuthorize("@ss.hasPermi('crm:offer:parse')")
    @Log(title = "Offer管理", businessType = BusinessType.OTHER)
    @PostMapping("/parse")
    public AjaxResult parse(@RequestBody Map<String, Object> body) {

        SimpleTextParser simpleTextParser = new SimpleTextParser();
        List<CrmOffer> crmOffers = simpleTextParser.parseTextToCrmOffers(body);
        return AjaxResult.success(crmOffers);
    }

    @PreAuthorize("@ss.hasPermi('crm:offer:list')")
    @PostMapping("/sendOffer")
    public AjaxResult sendOffer(@RequestBody CrmOffer offer) {
        List<CrmOffer> list = offerService.selectOfferList(offer);
        if (offer.getParams() != null && offer.getParams().containsKey("ids")) {
            Object idsObj = offer.getParams().get("ids");
            if (idsObj != null) {
                String idsStr = idsObj.toString();
                if (!idsStr.isEmpty()) {
                    String[] idArray = idsStr.split(",");
                    List<Long> targetIds = new java.util.ArrayList<>();
                    for (String s : idArray) {
                        try { targetIds.add(Long.valueOf(s.trim())); } catch (Exception ignored) {}
                    }
                    if (!targetIds.isEmpty()) {
                        list.removeIf(o -> !targetIds.contains(o.getId()));
                    }
                }
            }
        }
        // TODO: Implement send offer logic
        return AjaxResult.success("发送成功", list.size());
    }


}
