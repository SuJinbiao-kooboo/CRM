package com.ruoyi.crm.service.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.ruoyi.crm.domain.dto.DocField;
import com.ruoyi.crm.domain.dto.DocItemColumn;
import com.ruoyi.crm.domain.dto.DocTypeDef;

/**
 * 单据类型注册表：集中声明每种单据的字段与明细列，驱动前端动态表单 + 后端填充。
 */
public class DocTypeRegistry {

    public static final String PO = "PO";
    public static final String PI = "PI";
    public static final String CI = "CI";
    public static final String PL = "PL";
    public static final String SN = "SN";
    public static final String ENTRUST = "ENTRUST";

    /** PO/PI/CI 共用模板路径 */
    public static final String TPL_PO = "order-template/PO-Purchasing Order template.xlsx";
    public static final String TPL_PI = "order-template/PI-Proforma Invoice template.xlsx";
    public static final String TPL_CI = "order-template/CI-template.xlsx";
    public static final String TPL_PL = "order-template/PL-Packing List template.xlsx";
    public static final String TPL_ENTRUST = "order-template/香港仓库交货委托单.xlsx";

    private static final List<String> YES_NO = Arrays.asList("是", "否");
    private static final List<String> DELIVERY = Arrays.asList("送货", "自提", "快递");
    private static final List<String> ATTACH = Arrays.asList("附发票", "附装箱单");
    private static final List<String> PAY_TYPE = Arrays.asList("预付", "到付");

    private DocTypeRegistry() {
    }

    /** PO/PI/CI 明细列（模板 9 列中的可填项，AMOUNT 自动算） */
    private static List<DocItemColumn> invoiceItemColumns() {
        List<DocItemColumn> cols = new ArrayList<>();
        cols.add(DocItemColumn.of("brand", "Brand"));
        cols.add(DocItemColumn.of("itemName", "ITEM"));
        cols.add(DocItemColumn.of("spec", "Spec."));
        cols.add(DocItemColumn.of("qty", "QTY(pcs)"));
        cols.add(DocItemColumn.of("warranty", "Warranty"));
        cols.add(DocItemColumn.of("unitPrice", "U/P(USD)"));
        cols.add(DocItemColumn.of("amount", "AMOUNT(USD)").computed());
        cols.add(DocItemColumn.of("leadTime", "Lead time"));
        return cols;
    }

    public static List<DocTypeDef> list() {
        List<DocTypeDef> list = new ArrayList<>();

        DocTypeDef po = new DocTypeDef();
        po.setKey(PO);
        po.setName("采购订单 PO");
        po.setItems(true);
        po.setFileNameHint("PO 单号.xlsx / .pdf");
        po.setFields(Arrays.asList(
                DocField.of("orderNo", "PO No", "text").req(),
                DocField.of("dateText", "Order Date", "date").req(),
                DocField.of("sellerId", "供应商(Seller)", "supplier").req(),
                DocField.of("sellerText", "Seller 信息块", "textarea")));
        po.setItemColumns(invoiceItemColumns());
        list.add(po);

        DocTypeDef pi = new DocTypeDef();
        pi.setKey(PI);
        pi.setName("形式发票 PI");
        pi.setItems(true);
        pi.setFileNameHint("PI 单号.xlsx / .pdf");
        pi.setFields(Arrays.asList(
                DocField.of("orderNo", "PI No", "text").req(),
                DocField.of("dateText", "Order Date", "date").req(),
                DocField.of("buyerId", "买方(BUYER)", "supplier").req(),
                DocField.of("buyerText", "BUYER 信息块", "textarea")));
        pi.setItemColumns(invoiceItemColumns());
        list.add(pi);

        DocTypeDef ci = new DocTypeDef();
        ci.setKey(CI);
        ci.setName("商业发票 CI");
        ci.setItems(true);
        ci.setFileNameHint("CI 单号.xlsx / .pdf");
        ci.setFields(Arrays.asList(
                DocField.of("orderNo", "CI No", "text").req(),
                DocField.of("dateText", "Order Date", "date").req(),
                DocField.of("buyerId", "买方(BUYER)", "supplier").req(),
                DocField.of("buyerText", "BUYER 信息块", "textarea")));
        ci.setItemColumns(invoiceItemColumns());
        list.add(ci);

        DocTypeDef pl = new DocTypeDef();
        pl.setKey(PL);
        pl.setName("装箱单 PL");
        pl.setItems(false);
        pl.setUpload(true);
        pl.setUploadHint("上传供应商提供的 PL-Packing List 文件(.xlsx)，其物料信息将原样抄入模板");
        pl.setFileNameHint("PL-Packing List + 客户PO号");
        pl.setFields(Arrays.asList(
                DocField.of("customerPoNo", "客户 PO 号", "text").req().ph("用于生成文件名，如 POHK-027716"),
                DocField.of("dateText", "Date", "date").req(),
                DocField.of("shipTo", "SHIP TO 信息", "textarea").req()));
        pl.setItemColumns(Collections.emptyList());
        list.add(pl);

        DocTypeDef sn = new DocTypeDef();
        sn.setKey(SN);
        sn.setName("SN List");
        sn.setItems(false);
        sn.setFileNameHint("SN List.xlsx / .pdf");
        sn.setFields(Arrays.asList(
                DocField.of("snText", "SN 内容（每行一个）", "textarea").req()
                        .ph("一行一个 SN，生成的表格为 N 行单列")));
        sn.setItemColumns(Collections.emptyList());
        list.add(sn);

        DocTypeDef entrust = new DocTypeDef();
        entrust.setKey(ENTRUST);
        entrust.setName("香港仓库交货委托单");
        entrust.setItems(true);
        entrust.setFileNameHint("交货委托单.xlsx / .pdf");
        entrust.setFields(Arrays.asList(
                DocField.of("dateText", "日期", "date").req(),
                DocField.of("receiverInfo", "收货公司信息", "textarea").req()
                        .ph("公司名称/地址/联系人/电话等"),
                DocField.of("deliveryMethod", "交货方式", "select").req().options(DELIVERY),
                DocField.of("attachDoc", "附发票/装箱单", "select").req().options(ATTACH).def("附发票"),
                DocField.of("expressCompany", "快递公司名称", "text"),
                DocField.of("freight", "运费", "select").options(PAY_TYPE).def("预付"),
                DocField.of("customsDuty", "进口关税", "select").options(PAY_TYPE).def("预付"),
                DocField.of("remark", "备注/其他", "textarea")
                        .def("1.此票供应商送货到我司仓库，收到供应商的货之后请帮检查箱子是否完好，是否有多余标签（体现供应商信息）。若有供应商信息，请帮忙去掉。此外，需拆箱拍产品是否料号一致。\n"
                                + "2.检查完好，需拍照留底，以防问题出现扯皮。若有发现破损，需及时反馈给亦钟人。\n"
                                + "3.确认OK之后，随货附上装箱单，亦钟人叫快递上门即可。")));
        List<DocItemColumn> entrustCols = new ArrayList<>();
        entrustCols.add(DocItemColumn.of("itemName", "品名"));
        entrustCols.add(DocItemColumn.of("spec", "型号"));
        entrustCols.add(DocItemColumn.of("qty", "数量"));
        entrustCols.add(DocItemColumn.of("unit", "单位"));
        entrustCols.add(DocItemColumn.of("currency", "币种"));
        entrustCols.add(DocItemColumn.of("unitPrice", "单价"));
        entrustCols.add(DocItemColumn.of("amount", "合计金额").computed());
        entrustCols.add(DocItemColumn.of("grossWeight", "毛重(KGS)"));
        entrustCols.add(DocItemColumn.of("reachNo", "通达到货编号"));
        entrustCols.add(DocItemColumn.of("needUnpack", "是否需要拆箱"));
        entrust.setItemColumns(entrustCols);
        list.add(entrust);

        return list;
    }

    public static DocTypeDef get(String key) {
        for (DocTypeDef def : list()) {
            if (def.getKey().equalsIgnoreCase(key)) {
                return def;
            }
        }
        return null;
    }
}
