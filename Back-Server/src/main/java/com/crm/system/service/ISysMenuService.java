package com.crm.system.service;

import com.crm.system.domain.SysMenu;
import java.util.List;
import java.util.Map;

/**
 * 菜单 业务层
 * 
 * @author CRM System
 */
public interface ISysMenuService {
    
    /**
     * 根据用户查询系统菜单列表
     * 
     * @param userId 用户ID
     * @return 菜单列表
     */
    public List<SysMenu> selectMenuTreeByUserId(Long userId);
    
    /**
     * 构建前端路由所需要的菜单
     * 
     * @param menus 菜单列表
     * @return 路由列表
     */
    public List<Map<String, Object>> buildMenus(List<SysMenu> menus);
    
    /**
     * 构建前端所需要树结构
     * 
     * @param menus 菜单列表
     * @return 树结构列表
     */
    public List<SysMenu> buildMenuTree(List<SysMenu> menus);
}