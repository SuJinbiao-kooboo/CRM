package com.ruoyi.crm.service;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.web.multipart.MultipartFile;

import com.ruoyi.crm.domain.dto.DocGenerateReq;
import com.ruoyi.crm.domain.dto.DocTypeDef;

/**
 * 单据生成服务：类型定义、明细解析、Excel 生成。
 */
public interface IDocGenerateService {

    /** 单据类型定义列表（驱动前端动态表单） */
    List<DocTypeDef> listTypes();

    /** 从上传的 PO / PL 文件解析明细（仅返回数据，由页面确认后再生成） */
    List<Map<String, Object>> parseItems(DocGenerateReq req, Map<String, MultipartFile> files) throws IOException;

    /** 生成单据 Excel 字节流 */
    byte[] generateExcel(DocGenerateReq req, Map<String, MultipartFile> files) throws IOException;

    /** 生成文件名（含扩展名） */
    String fileName(DocGenerateReq req, String ext);
}