package com.crm.system.mapper;

import com.crm.system.domain.SysMenu;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/**
 * 菜单表 数据层
 * 
 * @author CRM System
 */
@Mapper
public interface SysMenuMapper {
    
    /**
     * 根据用户查询系统菜单列表
     * 
     * @param userId 用户ID
     * @return 菜单列表
     */
    public List<SysMenu> selectMenuTreeByUserId(Long userId);
    
    /**
     * 查询系统菜单列表
     * 
     * @param menu 菜单信息
     * @return 菜单列表
     */
    public List<SysMenu> selectMenuList(SysMenu menu);
    
    /**
     * 根据角色ID查询菜单树信息
     * 
     * @param roleId 角色ID
     * @param menuCheckStrictly 菜单树选择项是否关联显示
     * @return 选中菜单列表
     */
    public List<Long> selectMenuListByRoleId(Long roleId, boolean menuCheckStrictly);
}