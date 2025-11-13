package com.crm.system.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.crm.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;

/**
 * 部门表 sys_dept
 * 
 * @author CRM System
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dept")
public class SysDept extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /** 部门ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 父部门ID */
    private Long parentId;

    /** 祖级列表 */
    private String ancestors;

    /** 部门名称 */
    @NotBlank(message = "部门名称不能为空")
    @Size(min = 0, max = 100, message = "部门名称长度不能超过100个字符")
    private String deptName;

    /** 部门编码 */
    @Size(min = 0, max = 50, message = "部门编码长度不能超过50个字符")
    private String deptCode;

    /** 显示顺序 */
    @NotNull(message = "显示顺序不能为空")
    private Integer sortOrder;

    /** 负责人 */
    private String leader;

    /** 联系电话 */
    @Size(min = 0, max = 20, message = "联系电话长度不能超过20个字符")
    private String phone;

    /** 邮箱 */
    @Email(message = "邮箱格式不正确")
    @Size(min = 0, max = 100, message = "邮箱长度不能超过100个字符")
    private String email;

    /** 部门状态:1正常,0停用 */
    private Integer status;

    /** 父部门名称 */
    @TableField(exist = false)
    private String parentName;

    /** 子部门 */
    @TableField(exist = false)
    private List<SysDept> children = new ArrayList<SysDept>();

    public String getAncestors() {
        return ancestors;
    }

    public void setAncestors(String ancestors) {
        this.ancestors = ancestors;
    }

    public String getParentName() {
        return parentName;
    }

    public void setParentName(String parentName) {
        this.parentName = parentName;
    }

    public List<SysDept> getChildren() {
        return children;
    }

    public void setChildren(List<SysDept> children) {
        this.children = children;
    }
}