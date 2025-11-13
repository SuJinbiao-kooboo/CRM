package com.crm.system.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.crm.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * 字典子表对象 sys_dict_item
 * 
 * @author CRM System
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dict_item")
public class SysDictItem extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /** 字典项主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 字典主表ID */
    @NotNull(message = "字典主表ID不能为空")
    private Long dictId;

    /** 字典项键值 */
    @NotBlank(message = "字典项键值不能为空")
    @Size(min = 0, max = 100, message = "字典项键值长度不能超过100个字符")
    private String itemKey;

    /** 字典项标签 */
    @NotBlank(message = "字典项标签不能为空")
    @Size(min = 0, max = 200, message = "字典项标签长度不能超过200个字符")
    private String itemLabel;

    /** 排序 */
    private Integer sortOrder;

    /** 备注 */
    @Size(min = 0, max = 500, message = "备注长度不能超过500个字符")
    private String remark;

    /** 状态（1正常 0停用） */
    private Integer status;
}