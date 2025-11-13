package com.crm.system.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.crm.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * 角色表 sys_role
 * 
 * @author CRM System
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
public class SysRole extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /** 角色ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 角色名称 */
    @NotBlank(message = "角色名称不能为空")
    @Size(min = 0, max = 100, message = "角色名称长度不能超过100个字符")
    private String roleName;

    /** 角色权限 */
    @NotBlank(message = "权限字符不能为空")
    @Size(min = 0, max = 100, message = "权限字符长度不能超过100个字符")
    private String roleKey;

    /** 角色排序 */
    @NotNull(message = "显示顺序不能为空")
    private Integer roleSort;

    /** 数据范围（1：全部数据权限；2：自定数据权限；3：本部门数据权限；4：本部门及以下数据权限；5：仅本人数据权限） */
    private Integer dataScope;

    /** 角色状态（1正常 0停用） */
    private Integer status;

    /** 用户是否存在此角色标识 默认不存在 */
    @TableField(exist = false)
    private boolean flag = false;

    /** 菜单组 */
    @TableField(exist = false)
    private Long[] menuIds;

    /** 部门组（数据权限） */
    @TableField(exist = false)
    private Long[] deptIds;

    /** 角色菜单权限 */
    @TableField(exist = false)
    private boolean menuCheckStrictly;

    /** 部门树选择项是否关联显示 */
    @TableField(exist = false)
    private boolean deptCheckStrictly;

    public SysRole() {

    }

    public SysRole(Long id) {
        this.id = id;
    }

    public boolean isAdmin() {
        return isAdmin(this.id);
    }

    public static boolean isAdmin(Long roleId) {
        return roleId != null && 1L == roleId;
    }
}