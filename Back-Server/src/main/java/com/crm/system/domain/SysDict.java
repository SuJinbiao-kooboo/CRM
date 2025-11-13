package com.crm.system.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.crm.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.List;

/**
 * 字典主表对象 sys_dict
 * 
 * @author CRM System
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dict")
public class SysDict extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /** 字典主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 字典名称 */
    @NotBlank(message = "字典名称不能为空")
    @Size(min = 0, max = 100, message = "字典名称长度不能超过100个字符")
    private String dictName;

    /** 字典键值 */
    @NotBlank(message = "字典键值不能为空")
    @Size(min = 0, max = 100, message = "字典键值长度不能超过100个字符")
    private String dictKey;

    /** 描述 */
    @Size(min = 0, max = 500, message = "描述长度不能超过500个字符")
    private String description;

    /** 状态（1正常 0停用） */
    private Integer status;

    /** 字典项列表 */
    @TableField(exist = false)
    private List<SysDictItem> dictItems;
}