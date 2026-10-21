package com.ruoyi.crm.service;

import java.io.IOException;
import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.ruoyi.crm.domain.dto.DocGenerateReq;
import com.ruoyi.crm.domain.dto.DocTypeDef;

public interface IDocGenerateService {

    /** 全部单据类型定义（驱动前端动态表单） */
    List<DocTypeDef> listTypes();

    /** 生成单据 Excel */
    byte[] generateExcel(DocGenerateReq req, MultipartFile file) throws IOException;

    /** 生成单据 PDF */
    byte[] generatePdf(DocGenerateReq req, MultipartFile file) throws IOException;

    /** 输出文件名（含扩展名） */
    String fileName(DocGenerateReq req, String ext);
}
