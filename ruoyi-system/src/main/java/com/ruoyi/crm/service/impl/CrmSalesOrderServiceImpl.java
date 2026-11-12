package com.ruoyi.crm.service.impl;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.crm.domain.CrmSalesOrder;
import com.ruoyi.crm.domain.CrmSupplierContact;
import com.ruoyi.crm.mapper.CrmOrderMapper;
import com.ruoyi.crm.mapper.CrmSalesOrderFileMapper;
import com.ruoyi.crm.mapper.CrmSalesOrderMapper;
import com.ruoyi.crm.mapper.CrmSalesOrderSnMapper;
import com.ruoyi.crm.mapper.CrmSupplierContactMapper;
import com.ruoyi.crm.service.ICrmSalesOrderService;

@Service
public class CrmSalesOrderServiceImpl implements ICrmSalesOrderService {

    @Autowired
    private CrmSalesOrderMapper salesOrderMapper;

    @Autowired
    private CrmSalesOrderSnMapper salesOrderSnMapper;

    @Autowired
    private CrmSalesOrderFileMapper salesOrderFileMapper;

    @Autowired
    private CrmOrderMapper orderSequenceMapper;

    @Autowired
    private CrmSupplierContactMapper supplierContactMapper;

    @Override
    public List<CrmSalesOrder> selectSalesOrderList(CrmSalesOrder order) {
        return salesOrderMapper.selectSalesOrderList(order);
    }

    @Override
    public CrmSalesOrder selectSalesOrderById(Long id) {
        return salesOrderMapper.selectSalesOrderById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CrmSalesOrder createSalesOrder(CrmSalesOrder order) {
        if (order == null) {
            order = new CrmSalesOrder();
        }
        if (StringUtils.isEmpty(order.getOrderNo())) {
            order.setOrderNo(generateOrderNo());
        } else if (salesOrderMapper.countOrderNo(order.getOrderNo(), null) > 0) {
            throw new ServiceException("订单编号已存在：" + order.getOrderNo());
        }
        if (StringUtils.isEmpty(order.getStatus())) {
            order.setStatus("UNPAID");
        }
        fillBookingMonth(order);
        order.setCreateBy(SecurityUtils.getUsername());
        order.setUpdateBy(SecurityUtils.getUsername());
        salesOrderMapper.insertSalesOrder(order);
        return salesOrderMapper.selectSalesOrderById(order.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateSalesOrder(CrmSalesOrder order) {
        if (order == null || order.getId() == null) {
            throw new ServiceException("订单ID不能为空");
        }
        if (StringUtils.isEmpty(order.getOrderNo())) {
            throw new ServiceException("订单编号不能为空");
        }
        if (salesOrderMapper.countOrderNo(order.getOrderNo(), order.getId()) > 0) {
            throw new ServiceException("订单编号已存在：" + order.getOrderNo());
        }
        CrmSalesOrder current = salesOrderMapper.selectSalesOrderById(order.getId());
        if (current == null) {
            throw new ServiceException("订单不存在或已删除");
        }
        if (StringUtils.isEmpty(order.getStatus())) {
            order.setStatus(StringUtils.isEmpty(current.getStatus()) ? "UNPAID" : current.getStatus());
        }
        fillBookingMonth(order);
        order.setUpdateBy(SecurityUtils.getUsername());
        return salesOrderMapper.updateSalesOrder(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteSalesOrderByIds(Long[] ids) {
        if (ids == null || ids.length == 0) {
            return 0;
        }
        salesOrderSnMapper.deleteSnByOrderIds(ids);
        salesOrderFileMapper.softDeleteByOrderIds(ids, SecurityUtils.getUsername());
        return salesOrderMapper.deleteSalesOrderByIds(ids);
    }

    @Override
    public List<CrmSupplierContact> selectContactOptions(Long partnerId) {
        if (partnerId == null) {
            return Collections.emptyList();
        }
        return supplierContactMapper.selectBySupplierId(partnerId);
    }

    @Override
    public List<CrmSalesOrder> selectOrderOptions(String keyword) {
        return salesOrderMapper.selectSalesOrderOptions(keyword);
    }

    private void fillBookingMonth(CrmSalesOrder order) {
        if (order.getBookingDate() != null && StringUtils.isEmpty(order.getBookingMonth())) {
            order.setBookingMonth(new SimpleDateFormat("yyyy-MM").format(order.getBookingDate()));
        }
    }

    private String generateOrderNo() {
        String datePart = new SimpleDateFormat("yyyyMMdd").format(new Date());
        String seqKey = "SO" + datePart;
        Integer current = orderSequenceMapper.selectSeqForUpdate(seqKey);
        int next;
        if (current == null) {
            orderSequenceMapper.insertSeq(seqKey);
            next = 1;
            orderSequenceMapper.incrementSeq(seqKey);
        } else {
            next = current + 1;
            orderSequenceMapper.incrementSeq(seqKey);
        }
        return seqKey + "-" + String.format("%03d", next);
    }
}
