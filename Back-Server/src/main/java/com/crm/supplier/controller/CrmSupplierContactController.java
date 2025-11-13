package com.crm.supplier.controller;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.crm.common.core.controller.BaseController;
import com.crm.common.core.domain.AjaxResult;
import com.crm.supplier.domain.CrmSupplierContact;
import com.crm.supplier.service.ICrmSupplierContactService;

/**
 * 供应商联系人管理Controller
 */
@RestController
@RequestMapping("/supplier/contact")
public class CrmSupplierContactController extends BaseController {
    
    @Autowired
    private ICrmSupplierContactService contactService;

    /**
     * 根据供应商ID获取联系人列表
     */
    @GetMapping("/{supplierId}")
    public AjaxResult getContactsBySupplierId(@PathVariable("supplierId") Long supplierId) {
        List<CrmSupplierContact> contacts = contactService.getContactsBySupplierId(supplierId);
        return success(contacts);
    }

    /**
     * 保存联系人信息
     */
    @PostMapping
    public AjaxResult saveContacts(@RequestBody ContactRequest request) {
        boolean success = contactService.saveContacts(request.getSupplierId(), request.getContacts());
        return toAjax(success);
    }

    /**
     * 联系人请求对象
     */
    public static class ContactRequest {
        private Long supplierId;
        private List<CrmSupplierContact> contacts;

        public Long getSupplierId() {
            return supplierId;
        }

        public void setSupplierId(Long supplierId) {
            this.supplierId = supplierId;
        }

        public List<CrmSupplierContact> getContacts() {
            return contacts;
        }

        public void setContacts(List<CrmSupplierContact> contacts) {
            this.contacts = contacts;
        }
    }
}