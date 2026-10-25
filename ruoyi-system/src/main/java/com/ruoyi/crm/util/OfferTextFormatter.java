package com.ruoyi.crm.util;

import com.ruoyi.crm.domain.OfferTextLine;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/** Offer 复制与微信发送的唯一文本生成器。 */
public final class OfferTextFormatter {

    private static final String[] EXTRA_FIELDS = {"supplierCode", "productDetail", "deliveryTime", "dc", "spec"};
    private static final String[] EXTRA_HEADERS = {"Supplier", "Detail", "Delivery", "DC", "Spec"};

    private OfferTextFormatter() {
    }

    public static String formatSendText(List<OfferTextLine> lines, boolean includePrice, List<String> extraFields) {
        return withOfferHeader(formatData(lines, includePrice, extraFields));
    }

    public static String withOfferHeader(String data) {
        return withOfferHeader(data, new Date());
    }

    public static String withOfferHeader(String data, Date date) {
        return "M&E " + new SimpleDateFormat("yyyy-MM-dd").format(date) + " Offer：\n"
                + (data == null ? "" : data);
    }

    public static String formatData(List<OfferTextLine> source, boolean includePrice, List<String> extraFields) {
        List<OfferTextLine> rows = source == null ? new ArrayList<OfferTextLine>() : new ArrayList<>(source);
        if (rows.isEmpty()) {
            return "";
        }
        StringBuilder text = new StringBuilder("Part No.\tQty");
        if (includePrice) {
            text.append("\tPrice");
        }
        for (int i = 0; i < EXTRA_FIELDS.length; i++) {
            if (extraFields != null && extraFields.contains(EXTRA_FIELDS[i])) {
                text.append('\t').append(EXTRA_HEADERS[i]);
            }
        }
        rows.sort(Comparator.comparing((OfferTextLine row) -> empty(row.getBrand()).isEmpty())
                .thenComparing(row -> empty(row.getBrand()))
                .thenComparing(row -> empty(row.getPartNumber())));
        String previousBrand = null;
        for (OfferTextLine row : rows) {
            String brand = empty(row.getBrand());
            if (!brand.equals(previousBrand)) {
                if (previousBrand == null) {
                    text.append('\n');
                } else {
                    text.append("\n\n");
                }
                previousBrand = brand;
                if (!brand.isEmpty()) {
                    text.append(brand);
                }
            }
            text.append('\n').append(formatRow(row, includePrice, extraFields));
        }
        return text.toString();
    }

    public static String formatRow(OfferTextLine row, boolean includePrice, List<String> extraFields) {
        if (row == null) {
            return "";
        }
        if (row.getRawLine() != null && !row.getRawLine().trim().isEmpty()) {
            return row.getRawLine();
        }
        StringBuilder line = new StringBuilder(empty(row.getPartNumber())).append('\t')
                .append(row.getQuantity() == null ? "" : row.getQuantity() + "pcs");
        if (includePrice) {
            line.append('\t').append(row.getPrice() == null ? "" : price(row.getPrice()) + "USD");
        }
        for (int i = 0; i < EXTRA_FIELDS.length; i++) {
            if (extraFields == null || !extraFields.contains(EXTRA_FIELDS[i])) {
                continue;
            }
            line.append('\t').append(extraValue(row, EXTRA_FIELDS[i]));
        }
        return line.toString();
    }

    private static String extraValue(OfferTextLine row, String field) {
        if ("supplierCode".equals(field)) return empty(row.getSupplierCode());
        if ("productDetail".equals(field)) return empty(row.getProductDetail());
        if ("deliveryTime".equals(field)) return empty(row.getDeliveryTime());
        if ("dc".equals(field)) return empty(row.getDc());
        if ("spec".equals(field)) return empty(row.getSpec());
        return "";
    }

    private static String price(Double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static String empty(String value) {
        return value == null ? "" : value.trim();
    }
}
