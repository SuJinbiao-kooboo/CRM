package com.ruoyi.crm.service.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.crm.domain.CrmOrder;
import com.ruoyi.crm.domain.CrmOrderItem;
import com.ruoyi.crm.domain.CrmSupplier;
import com.ruoyi.crm.domain.CrmSupplierContact;
import com.ruoyi.crm.mapper.CrmOrderItemMapper;
import com.ruoyi.crm.mapper.CrmOrderMapper;
import com.ruoyi.crm.mapper.CrmSupplierContactMapper;
import com.ruoyi.crm.mapper.CrmSupplierMapper;
import com.ruoyi.crm.service.ICrmOrderService;
import com.ruoyi.crm.service.util.OrderDocumentFiller;

@Service
public class CrmOrderServiceImpl implements ICrmOrderService {

    /** PO 模板（classpath） */
    @Value("${order.po-template:order-template/PO-Purchasing Order template.xlsx}")
    private String poTemplate;


    @Autowired
    private CrmOrderMapper orderMapper;

    @Autowired
    private CrmOrderItemMapper orderItemMapper;

    @Autowired
    private CrmSupplierMapper supplierMapper;

    @Autowired
    private CrmSupplierContactMapper supplierContactMapper;

    @Override
    public List<CrmOrder> selectOrderList(CrmOrder order) {
        return orderMapper.selectOrderList(order);
    }

    @Override
    public CrmOrder selectOrderById(Long id) {
        CrmOrder order = orderMapper.selectOrderById(id);
        if (order != null) {
            order.setItems(orderItemMapper.selectItemsByOrderId(id));
        }
        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertOrder(CrmOrder order) {
        if (StringUtils.isEmpty(order.getOrderType())) {
            order.setOrderType("PO");
        }
        if (StringUtils.isEmpty(order.getOrderNo())) {
            order.setOrderNo(generateOrderNo(order.getOrderType()));
        }
        if (StringUtils.isEmpty(order.getCurrency())) {
            order.setCurrency("USD");
        }
        if (StringUtils.isEmpty(order.getStatus())) {
            order.setStatus("0");
        }
        order.setCreateBy(SecurityUtils.getUsername());
        order.setUpdateBy(SecurityUtils.getUsername());
        order.setTotalAmount(calcTotal(order.getItems()));

        int rows = orderMapper.insertOrder(order);
        saveItems(order);
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateOrder(CrmOrder order) {
        order.setUpdateBy(SecurityUtils.getUsername());
        order.setTotalAmount(calcTotal(order.getItems()));
        orderItemMapper.deleteItemsByOrderId(order.getId());
        saveItems(order);
        return orderMapper.updateOrder(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteOrderByIds(Long[] ids) {
        orderItemMapper.deleteItemsByOrderIds(ids);
        return orderMapper.deleteOrderByIds(ids);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String generateOrderNo(String orderType) {
        String type = StringUtils.isEmpty(orderType) ? "PO" : orderType;
        String datePart = new SimpleDateFormat("yyyyMMdd").format(new Date());
        String seqKey = type + datePart;

        Integer current = orderMapper.selectSeqForUpdate(seqKey);
        int next;
        if (current == null) {
            orderMapper.insertSeq(seqKey);
            next = 1;
            orderMapper.incrementSeq(seqKey);
        } else {
            next = current + 1;
            orderMapper.incrementSeq(seqKey);
        }
        return seqKey + "-" + String.format("%03d", next);
    }

    @Override
    public Map<String, Object> resolveSeller(Long supplierId) {
        Map<String, Object> result = new HashMap<>(8);
        if (supplierId == null) {
            return result;
        }
        CrmSupplier supplier = supplierMapper.selectSupplierById(supplierId);
        if (supplier == null) {
            throw new ServiceException("供应商不存在：" + supplierId);
        }
        CrmSupplierContact contact = pickPrimaryContact(supplierId);
        result.put("sellerId", supplier.getId());
        result.put("sellerName", supplier.getSupplierName());
        result.put("sellerContactId", contact == null ? null : contact.getId());
        result.put("text", buildSellerText(supplier, contact));
        return result;
    }

    @Override
    public byte[] generateExcel(CrmOrder order) {
        if (order == null) {
            throw new ServiceException("订单数据为空");
        }
        List<CrmOrderItem> items = order.getItems();
        if ((items == null || items.isEmpty()) && order.getId() != null) {
            items = orderItemMapper.selectItemsByOrderId(order.getId());
        }
        String sellerText = order.getSellerSnapshot();
        if (StringUtils.isEmpty(sellerText) && order.getSellerId() != null) {
            sellerText = (String) resolveSeller(order.getSellerId()).get("text");
        }
        try (InputStream template = new ClassPathResource(poTemplate).getInputStream();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            OrderDocumentFiller.fillPurchaseOrder(template, out, order.getOrderNo(), order.getOrderDate(), sellerText, items);
            return out.toByteArray();
        } catch (IOException e) {
            throw new ServiceException("生成 PO 单据失败：" + e.getMessage());
        }
    }


    /** 组装 Seller 信息块文本：公司/地址/联系人/邮箱/电话 */
    private String buildSellerText(CrmSupplier supplier, CrmSupplierContact contact) {
        StringBuilder sb = new StringBuilder();
        sb.append("Seller:\n");
        sb.append(StringUtils.trimToEmpty(supplier.getSupplierName())).append("\n");
        if (StringUtils.isNotEmpty(supplier.getAddress())) {
            sb.append("Add:").append(supplier.getAddress()).append("\n");
        }
        if (contact != null) {
            if (StringUtils.isNotEmpty(contact.getContactName())) {
                sb.append("Contact:").append(contact.getContactName()).append("\n");
            }
            if (StringUtils.isNotEmpty(contact.getEmail())) {
                sb.append("Email:").append(contact.getEmail()).append("\n");
            }
            if (StringUtils.isNotEmpty(contact.getPhone())) {
                sb.append("TEL:").append(contact.getPhone()).append("\n");
            }
        }
        return sb.toString().trim();
    }

    /** 取主联系人，若未标记主联系人则取第一条 */
    private CrmSupplierContact pickPrimaryContact(Long supplierId) {
        List<CrmSupplierContact> contacts = supplierContactMapper.selectBySupplierId(supplierId);
        if (contacts == null || contacts.isEmpty()) {
            return null;
        }
        for (CrmSupplierContact contact : contacts) {
            if (contact.getIsPrimary() != null && contact.getIsPrimary() == 1) {
                return contact;
            }
        }
        return contacts.get(0);
    }

    private void saveItems(CrmOrder order) {
        List<CrmOrderItem> items = order.getItems();
        if (items == null || items.isEmpty()) {
            return;
        }
        List<CrmOrderItem> toSave = new ArrayList<>(items.size());
        int line = 1;
        for (CrmOrderItem item : items) {
            item.setOrderId(order.getId());
            if (item.getLineNo() == null) {
                item.setLineNo(line);
            }
            item.setAmount(calcAmount(item));
            item.setCreateBy(SecurityUtils.getUsername());
            item.setUpdateBy(SecurityUtils.getUsername());
            toSave.add(item);
            line++;
        }
        orderItemMapper.batchInsertItems(toSave);
    }

    private BigDecimal calcAmount(CrmOrderItem item) {
        if (item.getQty() == null || item.getUnitPrice() == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return item.getQty().multiply(item.getUnitPrice()).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calcTotal(List<CrmOrderItem> items) {
        BigDecimal total = BigDecimal.ZERO;
        if (items != null) {
            for (CrmOrderItem item : items) {
                total = total.add(calcAmount(item));
            }
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }
}
