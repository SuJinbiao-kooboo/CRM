package com.crm.inventory.service.impl;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.crm.inventory.domain.ImportStockDTO;
import com.crm.inventory.domain.InvStock;
import com.crm.inventory.mapper.InvStockMapper;
import com.crm.inventory.service.IInvStockService;
import com.crm.inventory.service.ImportStockListener;
import com.crm.lead.domain.CrmLead;
import com.crm.lead.mapper.CrmLeadMapper;
import com.crm.lead.service.ICrmLeadService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
public class InvStockServiceImpl  extends ServiceImpl<InvStockMapper, InvStock> implements IInvStockService {


    @Override
    public List<InvStock> selectInvStockList(InvStock invStock) {
        return baseMapper.selectInvStockList(invStock);
    }

    @Override
    public String importStock(MultipartFile file, Map<String, Integer> columnMapping) {
        try {
            EasyExcel.read(file.getInputStream(), ImportStockDTO.class, 
                new ImportStockListener(baseMapper, columnMapping))
                .sheet()
                .doRead();
            return "导入成功";
        } catch (IOException e) {
            e.printStackTrace();
            return "导入失败: " + e.getMessage();
        }
    }
}