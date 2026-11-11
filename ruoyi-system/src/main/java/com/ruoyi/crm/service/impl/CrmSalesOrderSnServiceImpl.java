package com.ruoyi.crm.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.crm.domain.CrmSalesOrder;
import com.ruoyi.crm.domain.CrmSalesOrderSn;
import com.ruoyi.crm.domain.dto.CrmSalesOrderSnBatchReq;
import com.ruoyi.crm.mapper.CrmSalesOrderMapper;
import com.ruoyi.crm.mapper.CrmSalesOrderSnMapper;
import com.ruoyi.crm.service.ICrmSalesOrderSnService;

@Service
public class CrmSalesOrderSnServiceImpl implements ICrmSalesOrderSnService {

    private static final int MAX_BATCH_SIZE = 10000;

    @Autowired
    private CrmSalesOrderSnMapper snMapper;

    @Autowired
    private CrmSalesOrderMapper salesOrderMapper;

    @Override
    public List<CrmSalesOrderSn> selectSnList(CrmSalesOrderSn query) {
        return snMapper.selectSnList(query == null ? new CrmSalesOrderSn() : query);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> batchInsert(CrmSalesOrderSnBatchReq req) {
        if (req == null || req.getOrderId() == null) {
            throw new ServiceException("请选择订单");
        }
        CrmSalesOrder order = salesOrderMapper.selectSalesOrderById(req.getOrderId());
        if (order == null) {
            throw new ServiceException("订单不存在或已删除");
        }
        Set<String> uniqueSns = new LinkedHashSet<>();
        if (req.getSns() != null) {
            for (String value : req.getSns()) {
                String sn = StringUtils.trimToEmpty(value);
                if (!sn.isEmpty()) {
                    uniqueSns.add(sn);
                }
            }
        }
        if (uniqueSns.isEmpty()) {
            throw new ServiceException("请粘贴至少一个SN编号");
        }
        if (uniqueSns.size() > MAX_BATCH_SIZE) {
            throw new ServiceException("单次最多录入 " + MAX_BATCH_SIZE + " 个SN编号");
        }

        String username = SecurityUtils.getUsername();
        String cartonNo = StringUtils.trimToNull(req.getCartonNo());
        List<CrmSalesOrderSn> rows = new ArrayList<>(uniqueSns.size());
        for (String snNo : uniqueSns) {
            if (snNo.length() > 128) {
                throw new ServiceException("SN编号过长：" + snNo);
            }
            CrmSalesOrderSn row = new CrmSalesOrderSn();
            row.setOrderId(req.getOrderId());
            row.setSnNo(snNo);
            row.setCartonNo(cartonNo);
            row.setCreateBy(username);
            row.setUpdateBy(username);
            rows.add(row);
        }
        int inserted = snMapper.insertBatchIgnore(rows);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderNo", order.getOrderNo());
        result.put("total", uniqueSns.size());
        result.put("inserted", inserted);
        result.put("skipped", Math.max(0, uniqueSns.size() - inserted));
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateSn(CrmSalesOrderSn sn) {
        if (sn == null || sn.getId() == null) {
            throw new ServiceException("SN记录ID不能为空");
        }
        if (StringUtils.isEmpty(sn.getSnNo())) {
            throw new ServiceException("SN编号不能为空");
        }
        String snNo = sn.getSnNo().trim();
        if (snNo.length() > 128) {
            throw new ServiceException("SN编号过长");
        }
        if (snMapper.countSnNo(snNo, sn.getId()) > 0) {
            throw new ServiceException("SN编号已存在：" + snNo);
        }
        sn.setSnNo(snNo);
        sn.setCartonNo(StringUtils.trimToNull(sn.getCartonNo()));
        sn.setUpdateBy(SecurityUtils.getUsername());
        return snMapper.updateSn(sn);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteSnByIds(Long[] ids) {
        if (ids == null || ids.length == 0) {
            return 0;
        }
        return snMapper.deleteSnByIds(ids);
    }
}
