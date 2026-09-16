package com.ruoyi.web.controller.crm;

import java.util.*;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletResponse;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.crm.domain.dto.CrmOfferExportDTO;
import com.ruoyi.crm.domain.dto.SendEmailReq;
import com.ruoyi.crm.service.ICrmSendOfferService;
import com.ruoyi.crm.service.ICrmSupplierSendOfferService;
import com.ruoyi.crm.service.util.SimpleTextParser;
import io.swagger.annotations.ApiParam;
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
import com.ruoyi.system.service.ISysDictDataService;

@RestController
@RequestMapping("/crm/offer")
public class OfferController extends BaseController {

    @Autowired
    private ICrmOfferService offerService;

    @Autowired
    private ISysDictDataService dictDataService;

    @Autowired
    private ICrmSendOfferService crmSendOfferService;

    @Autowired
    private ICrmSupplierSendOfferService crmSupplierSendOfferService;

    @PreAuthorize("@ss.hasPermi('crm:offer:list')")
    @GetMapping("/list")
    public TableDataInfo list(@ApiParam CrmOffer offer) {
        startPage();
        List<CrmOffer> list = offerService.selectOfferList(offer);
        // 供应商名称不暴露：库中supplier_name已统一存编码（数据已清洗），此处再覆盖一遍作为双保险
        for (CrmOffer o : list) {
            o.setSupplierName(o.getSupplierCode());
        }
        return getDataTable(list);
    }

    /** 复制Offer：查询最近days天内INQ/OFFER记录，按品牌排序、相同料号取成本最低（无价格也保留），返回制表符分隔文本
     *  注意：buildCopyOfferText 返回 String，不能直接传 AjaxResult.success(Object) 单参——
     *  重载解析会选中更具体的 success(String msg) 把文本塞进 msg 字段、data 为 null，前端取不到数据，
     *  必须用双参 success(String msg, Object data) 显式把文本放入 data */
    @PreAuthorize("@ss.hasPermi('crm:offer:list')")
    @GetMapping("/copyOfferText")
    public AjaxResult copyOfferText(@RequestParam("days") int days) {
        return AjaxResult.success("操作成功", offerService.buildCopyOfferText(days));
    }

    /**
     * 一键复制Offer（AI录入比价/AI查询共用）：按料号集合+最近天数查询各料号Offer价格最低的一条（同价取日期最新），
     * 默认输出 料号/数量/Offer价格，body.extraFields 勾选供应商编号/详情/交期/DC后按固定顺序追加对应列，
     * 返回制表符分隔文本（首行英文表头随勾选字段变化）
     */
    @PreAuthorize("@ss.hasPermi('crm:offer:list')")
    @PostMapping("/copyAiQueryOffers")
    public AjaxResult copyAiQueryOffers(@RequestBody Map<String, Object> body) {
        // days范围校验在service内（1-999）；非数字或缺省时默认1天；前端提供最近1/2/3天选项
        int days = NumberUtil.parseInt(strOf(body.get("days")), 1);
        List<String> partNumbers = new ArrayList<>();
        Object pns = body.get("partNumbers");
        if (pns instanceof List) {
            for (Object o : (List<?>) pns) {
                partNumbers.add(String.valueOf(o));
            }
        }
        // extraFields：勾选的额外字段（supplierCode/productDetail/deliveryTime/dc），缺省时不追加任何列
        List<String> extraFields = new ArrayList<>();
        Object efs = body.get("extraFields");
        if (efs instanceof List) {
            for (Object o : (List<?>) efs) {
                extraFields.add(String.valueOf(o));
            }
        }
        // 注意：返回String必须用双参重载，单参success(String)会把文本塞进msg导致前端取不到data
        return AjaxResult.success("操作成功", offerService.buildAiQueryCopyText(partNumbers, days, extraFields));
    }

    @PreAuthorize("@ss.hasPermi('crm:offer:export')")
    @Log(title = "Offer管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, CrmOffer offer,
                       @RequestParam(value = "ids", required = false) Long[] ids,
                       @RequestParam(value = "exportFields", required = false) String exportFields) {
        List<CrmOffer> list = offerService.selectOfferList(offer);
        if (ids != null && ids.length > 0) {
            list.removeIf(o -> !contains(ids, o.getId()));
        }
        ExcelUtil<CrmOfferExportDTO> util = new ExcelUtil<>(CrmOfferExportDTO.class);
        if (exportFields != null && !exportFields.trim().isEmpty()) {
            String[] cols = java.util.Arrays.stream(exportFields.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toArray(String[]::new);
            if (cols.length > 0) {
                util.showColumn(cols);
            }
        }
        util.exportExcel(response, BeanUtil.copyToList(list, CrmOfferExportDTO.class), "M&E_内部Offer数据"+DateUtil.today());
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
                                 @RequestParam("colMapJson") String colMapJson,
                                 @RequestParam(value = "profitRatio", required = false) Double profitRatio) {
        Map<String, String> colMap = new HashMap<>();
        if (colMapJson != null && !colMapJson.isEmpty()) {
            colMap = JSON.parseObject(colMapJson, Map.class);
        }
        if (profitRatio == null) profitRatio = 2d;
        Map<String, Object> result = offerService.importOffers(file, supplierCode, supplierName, inqOfferType, colMap, profitRatio);
        String msg = "成功导入" + result.getOrDefault("successCount", 0) + "条，失败" + result.getOrDefault("failCount", 0) + "条";
        return AjaxResult.success(msg, result);
    }

    @PreAuthorize("@ss.hasPermi('crm:offer:add')")
    @Log(title = "Offer管理", businessType = BusinessType.INSERT)
    @PostMapping("/aiEntry")
    public AjaxResult aiEntry(@RequestBody Map<String, Object> body) {
        String supplierCode = strOf(body.get("supplierCode"));
        String supplierName = strOf(body.get("supplierName"));
        String inqOfferType = strOf(body.get("inqOfferType"));
        String content = strOf(body.get("content"));
        Double profitRatio = toDouble(body.get("profitRatio"));
        if (profitRatio == null) profitRatio = 2d;
        // 录入并拿到本批成功入库的Offer（用于录入后近1个月同料号价格比较）
        List<CrmOffer> saved = offerService.aiEntryOffersReturning(supplierCode, supplierName, inqOfferType, profitRatio, content);
        List<String> partNumbers = saved.stream().map(CrmOffer::getProductCode).collect(Collectors.toList());
        // 比价数据（近1个月，按Offer日期倒排）；查询异常不影响录入结果，只记录日志
        List<Map<String, Object>> compare = new ArrayList<>();
        try {
            compare = offerService.compareRecentOffers(partNumbers);
        } catch (Exception e) {
            logger.warn("AI录入比价查询失败", e);
        }
        // 注意：data 用 Map 显式作为 Object 传入双参重载，避免与单参 success(String msg) 产生歧义
        Map<String, Object> data = new HashMap<>();
        data.put("count", saved.size());
        data.put("compare", compare);
        return AjaxResult.success("AI录入成功" + saved.size() + "条", data);
    }

    /**
     * AI料号查询：调用AI提取料号，查询最近1个月内的INQ/OFFER历史记录（组内按Offer日期倒排），按料号分组返回
     */
    @PreAuthorize("@ss.hasPermi('crm:offer:list')")
    @Log(title = "Offer管理", businessType = BusinessType.OTHER)
    @PostMapping("/aiQuery")
    public AjaxResult aiQuery(@RequestBody Map<String, Object> body) {
        String content = strOf(body.get("content"));
        List<Map<String, Object>> groups = offerService.aiQueryHistory(content);
        return AjaxResult.success(groups);
    }

    private String strOf(Object o) {
        return o == null ? "" : o.toString().trim();
    }

    private Double toDouble(Object o) {
        if (o == null) return null;
        try {
            return Double.valueOf(o.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }

    @PreAuthorize("@ss.hasPermi('crm:offer:parse')")
    @Log(title = "Offer管理", businessType = BusinessType.OTHER)
    @PostMapping("/parse")
    public AjaxResult parse(@RequestBody Map<String, Object> body) {

        SimpleTextParser simpleTextParser = new SimpleTextParser();
        List<CrmOffer> crmOffers = simpleTextParser.parseTextToCrmOffers(body);
        return AjaxResult.success(crmOffers);
    }

    /**
     * 发送Offer邮件（请求体 params 中可传：testSend=true测试发送/false正式发送，默认测试；withPrice=true带报价/false不带，默认不带）
     * 测试发送仅发到字典配置的测试邮箱，正式发送发到订阅邮箱+供应商邮箱
     */
    @Anonymous
//    @PreAuthorize("@ss.hasPermi('crm:offer:list')")
    @PostMapping("/sendOffer")
    public AjaxResult sendOffer(@RequestBody CrmOffer offer) {
        offer.setInqOfferType("Offer"); // Offer/Inq

        Collection<CrmOffer> list = doSendEmailOfferOrInq(offer);

        return AjaxResult.success("发送成功", list.size());
    }

    private Collection<CrmOffer> doSendEmailOfferOrInq(CrmOffer offer) {
        // 发送选项：来自前端发送弹窗单选（测试/正式、是否带价格）；缺省默认测试发送且不带价格，避免误发正式邮件/误下报价
        Map<String, Object> sendParams = offer.getParams();
        if (sendParams == null) {
            sendParams = new HashMap<>();
            offer.setParams(sendParams);
        }
        boolean testSend = !"false".equalsIgnoreCase(StrUtil.toStringOrNull(sendParams.get("testSend")));
        boolean withPrice = "true".equalsIgnoreCase(StrUtil.toStringOrNull(sendParams.get("withPrice")));

        String lastHours = dictDataService.selectDictLabel("crm_email_template_dict", "crm_email_last_hours");
        // 限制只能发送最近16小时录入的Offer
        sendParams.put("lastCreateTime", DateUtil.offsetHour(new Date(), -Integer.valueOf(lastHours)));

        Collection<CrmOffer> list = offerService.selectOfferList(offer);
        if("Offer".equals(offer.getInqOfferType())){
            list = list.stream()
                    .sorted(Comparator.comparing(o-> StrUtil.nullToDefault(o.getProductBrand(), "")))
                    .collect(Collectors.toList());
            LinkedHashMap<String, CrmOffer> offerMap = new LinkedHashMap<>();
            for (CrmOffer crmOffer : list) {
                // 不带价格发送时清空报价（是否带报价由发送弹窗选择）；成本字段仅内部使用永不下发
                if (!withPrice) {
                    crmOffer.setPriceOffer(null);
                }
                if(!offerMap.containsKey(crmOffer.getProductCode())){
                    offerMap.put(crmOffer.getProductCode(), crmOffer);
                    continue;
                }
                if(NumberUtil.compare(NumberUtil.nullToZero(crmOffer.getPriceCost()), NumberUtil.nullToZero(offerMap.get(crmOffer.getProductCode()).getPriceCost())) < 0){
                    offerMap.put(crmOffer.getProductCode(), crmOffer);
                }
            }

            list = offerMap.values();

        }

        if (offer.getParams() != null && offer.getParams().containsKey("ids")) {
            Object idsObj = offer.getParams().get("ids");
            if (idsObj != null) {
                String idsStr = idsObj.toString();
                if (!idsStr.isEmpty()) {
                    String[] idArray = idsStr.split(",");
                    List<Long> targetIds = new ArrayList<>();
                    for (String s : idArray) {
                        try { targetIds.add(Long.valueOf(s.trim())); } catch (Exception ignored) {}
                    }
                    if (!targetIds.isEmpty()) {
                        list.removeIf(o -> !targetIds.contains(o.getId()));
                    }
                }
            }
        }
        // 准备数据
        SendEmailReq sendEmailReq = new SendEmailReq();
        sendEmailReq.setOffers(list);

        sendEmailReq.setEmailGroups(crmSupplierSendOfferService.listToOfferEmail(testSend));

        // 发送消息
        crmSendOfferService.sendExcelEmail(sendEmailReq);
        return list;
    }

    @Anonymous
    @PostMapping("/sendInq")
    public AjaxResult sendInq(@RequestBody CrmOffer offer) {
        offer.setInqOfferType("Inq"); // Offer/Inq
        Collection<CrmOffer> list = doSendEmailOfferOrInq(offer);

        return AjaxResult.success("发送成功", list.size());
    }

    @PostMapping("/sendExcelEmail")
    public AjaxResult sendExcelEmail(@RequestBody SendEmailReq req) {
        return crmSendOfferService.sendExcelEmail(req);
    }

    @PreAuthorize("@ss.hasPermi('crm:offer:list')")
    @GetMapping("/emailResults")
    public AjaxResult emailResults() {
        return AjaxResult.success(crmSupplierSendOfferService.listEmailSendResults());
    }


}
