package com.crm.system.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.crm.common.core.domain.BaseEntity;
import java.util.ArrayList;
import java.util.List;

/**
 * 菜单权限表 sys_menu
 * 
 * @author CRM System
 */
@TableName("sys_menu")
public class SysMenu extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /** 菜单ID */
    @TableId(value = "menu_id", type = IdType.AUTO)
    private Long id;

    /** 菜单名称 */
    private String menuName;

    /** 父菜单名称 */
    private String parentName;

    /** 父菜单ID */
    private Long parentId;

    /** 显示顺序 */
    private Integer orderNum;

    /** 路由地址 */
    private String path;

    /** 组件路径 */
    private String component;

    /** 路由参数 */
    private String query;

    /** 是否为外链（0是 1否） */
    private String isFrame;

    /** 是否缓存（0缓存 1不缓存） */
    private String isCache;

    /** 菜单类型（M目录 C菜单 F按钮） */
    private String menuType;

    /** 菜单状态（0显示 1隐藏） */
    private String visible;

    /** 菜单状态（0正常 1停用） */
    private String status;

    /** 权限标识 */
    private String perms;

    /** 菜单图标 */
    private String icon;

    /** 子菜单 */
    private List<SysMenu> children = new ArrayList<SysMenu>();

    /**
     * 获取菜单ID
     * 
     * @return 菜单ID
     */
    public Long getMenuId() {
        return id;
    }

    /**
     * 设置菜单ID
     * 
     * @param menuId 菜单ID
     */
    public void setMenuId(Long menuId) {
        this.id = menuId;
    }

    /**
     * 获取菜单名称
     * 
     * @return 菜单名称
     */
    public String getMenuName() {
        return menuName;
    }

    /**
     * 设置菜单名称
     * 
     * @param menuName 菜单名称
     */
    public void setMenuName(String menuName) {
        this.menuName = menuName;
    }

    /**
     * 获取父菜单名称
     * 
     * @return 父菜单名称
     */
    public String getParentName() {
        return parentName;
    }

    /**
     * 设置父菜单名称
     * 
     * @param parentName 父菜单名称
     */
    public void setParentName(String parentName) {
        this.parentName = parentName;
    }

    /**
     * 获取父菜单ID
     * 
     * @return 父菜单ID
     */
    public Long getParentId() {
        return parentId;
    }

    /**
     * 设置父菜单ID
     * 
     * @param parentId 父菜单ID
     */
    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public Integer getOrderNum() {
        return orderNum;
    }

    public void setOrderNum(Integer orderNum) {
        this.orderNum = orderNum;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    /**
     * 获取组件路径
     * 
     * @return 组件路径
     */
    public String getComponent() {
        return component;
    }

    /**
     * 设置组件路径
     * 
     * @param component 组件路径
     */
    public void setComponent(String component) {
        this.component = component;
    }

    /**
     * 获取路由参数
     * 
     * @return 路由参数
     */
    public String getQuery() {
        return query;
    }

    /**
     * 设置路由参数
     * 
     * @param query 路由参数
     */
    public void setQuery(String query) {
        this.query = query;
    }

    /**
     * 获取是否为外链（0是 1否）
     * 
     * @return 是否为外链
     */
    public String getIsFrame() {
        return isFrame;
    }

    /**
     * 设置是否为外链（0是 1否）
     * 
     * @param isFrame 是否为外链
     */
    public void setIsFrame(String isFrame) {
        this.isFrame = isFrame;
    }

    /**
     * 获取是否缓存（0缓存 1不缓存）
     * 
     * @return 是否缓存
     */
    public String getIsCache() {
        return isCache;
    }

    /**
     * 设置是否缓存（0缓存 1不缓存）
     * 
     * @param isCache 是否缓存
     */
    public void setIsCache(String isCache) {
        this.isCache = isCache;
    }

    /**
     * 获取菜单类型（M目录 C菜单 F按钮）
     * 
     * @return 菜单类型
     */
    public String getMenuType() {
        return menuType;
    }

    /**
     * 设置菜单类型（M目录 C菜单 F按钮）
     * 
     * @param menuType 菜单类型
     */
    public void setMenuType(String menuType) {
        this.menuType = menuType;
    }

    /**
     * 获取菜单状态（0显示 1隐藏）
     * 
     * @return 菜单状态
     */
    public String getVisible() {
        return visible;
    }

    /**
     * 设置菜单状态（0显示 1隐藏）
     * 
     * @param visible 菜单状态
     */
    public void setVisible(String visible) {
        this.visible = visible;
    }

    /**
     * 获取菜单状态（0正常 1停用）
     * 
     * @return 菜单状态
     */
    public String getStatus() {
        return status;
    }

    /**
     * 设置菜单状态（0正常 1停用）
     * 
     * @param status 菜单状态
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * 获取权限标识
     * 
     * @return 权限标识
     */
    public String getPerms() {
        return perms;
    }

    /**
     * 设置权限标识
     * 
     * @param perms 权限标识
     */
    public void setPerms(String perms) {
        this.perms = perms;
    }

    /**
     * 获取菜单图标
     * 
     * @return 菜单图标
     */
    public String getIcon() {
        return icon;
    }

    /**
     * 设置菜单图标
     * 
     * @param icon 菜单图标
     */
    public void setIcon(String icon) {
        this.icon = icon;
    }

    /**
     * 获取子菜单列表
     * 
     * @return 子菜单列表
     */
    public List<SysMenu> getChildren() {
        return children;
    }

    /**
     * 设置子菜单列表
     * 
     * @param children 子菜单列表
     */
    public void setChildren(List<SysMenu> children) {
        this.children = children;
    }
}