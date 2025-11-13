package com.crm.system.service.impl;

import com.crm.system.domain.SysMenu;
import com.crm.system.mapper.SysMenuMapper;
import com.crm.system.service.ISysMenuService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 菜单 业务层处理
 * 
 * @author CRM System
 */
@Service
public class SysMenuServiceImpl implements ISysMenuService {
    
    @Autowired
    private SysMenuMapper menuMapper;

    /**
     * 根据用户查询系统菜单列表
     * 
     * @param userId 用户ID
     * @return 菜单列表
     */
    @Override
    public List<SysMenu> selectMenuTreeByUserId(Long userId) {
        List<SysMenu> menus = null;
        if (userId != null && userId == 1L) {
            // 管理员显示所有菜单信息
            menus = menuMapper.selectMenuList(new SysMenu());
        } else {
            menus = menuMapper.selectMenuTreeByUserId(userId);
        }
        return getChildPerms(menus, 0);
    }

    /**
     * 构建前端路由所需要的菜单
     * 
     * @param menus 菜单列表
     * @return 路由列表
     */
    @Override
    public List<Map<String, Object>> buildMenus(List<SysMenu> menus) {
        List<Map<String, Object>> routers = new LinkedList<Map<String, Object>>();
        for (SysMenu menu : menus) {
            Map<String, Object> router = new HashMap<String, Object>();
            router.put("hidden", !"1".equals(menu.getVisible()));
            router.put("name", getRouteName(menu));
            router.put("path", getRouterPath(menu));
            router.put("component", getComponent(menu));
            router.put("query", menu.getQuery());
            router.put("meta", getMeta(menu));
            List<SysMenu> cMenus = menu.getChildren();
            if (!cMenus.isEmpty() && cMenus.size() > 0 && "M".equals(menu.getMenuType())) {
                router.put("alwaysShow", true);
                router.put("redirect", "noRedirect");
                router.put("children", buildMenus(cMenus));
            } else if (isMenuFrame(menu)) {
                router.put("meta", null);
                List<Map<String, Object>> childrenList = new ArrayList<Map<String, Object>>();
                Map<String, Object> children = new HashMap<String, Object>();
                children.put("path", menu.getPath());
                children.put("component", menu.getComponent());
                children.put("name", menu.getMenuName());
                children.put("meta", getMeta(menu));
                children.put("query", menu.getQuery());
                childrenList.add(children);
                router.put("children", childrenList);
            } else if (menu.getParentId().intValue() == 0 && isInnerLink(menu)) {
                router.put("meta", getMeta(menu));
                router.put("path", "/");
                List<Map<String, Object>> childrenList = new ArrayList<Map<String, Object>>();
                Map<String, Object> children = new HashMap<String, Object>();
                String routerPath = innerLinkReplaceEach(menu.getPath());
                children.put("path", routerPath);
                children.put("component", "InnerLink");
                children.put("name", menu.getMenuName());
                children.put("meta", getMeta(menu));
                childrenList.add(children);
                router.put("children", childrenList);
            }
            routers.add(router);
        }
        return routers;
    }

    /**
     * 构建前端所需要树结构
     * 
     * @param menus 菜单列表
     * @return 树结构列表
     */
    @Override
    public List<SysMenu> buildMenuTree(List<SysMenu> menus) {
        List<SysMenu> returnList = new ArrayList<SysMenu>();
        List<Long> tempList = new ArrayList<Long>();
        for (SysMenu dept : menus) {
            tempList.add(dept.getMenuId());
        }
        for (Iterator<SysMenu> iterator = menus.iterator(); iterator.hasNext();) {
            SysMenu menu = (SysMenu) iterator.next();
            // 如果是顶级节点, 遍历该父节点的所有子节点
            if (!tempList.contains(menu.getParentId())) {
                recursionFn(menus, menu);
                returnList.add(menu);
            }
        }
        if (returnList.isEmpty()) {
            returnList = menus;
        }
        return returnList;
    }

    /**
     * 根据父节点的ID获取所有子节点
     * 
     * @param list 分类表
     * @param parentId 传入的父节点ID
     * @return String
     */
    public List<SysMenu> getChildPerms(List<SysMenu> list, int parentId) {
        List<SysMenu> returnList = new ArrayList<SysMenu>();
        for (Iterator<SysMenu> iterator = list.iterator(); iterator.hasNext();) {
            SysMenu t = (SysMenu) iterator.next();
            // 一、根据传入的某个父节点ID,遍历该父节点的所有子节点
            if (t.getParentId() == parentId) {
                recursionFn(list, t);
                returnList.add(t);
            }
        }
        return returnList;
    }

    /**
     * 递归列表
     * 
     * @param list
     * @param t
     */
    private void recursionFn(List<SysMenu> list, SysMenu t) {
        // 得到子节点列表
        List<SysMenu> childList = getChildList(list, t);
        t.setChildren(childList);
        for (SysMenu tChild : childList) {
            if (hasChild(list, tChild)) {
                recursionFn(list, tChild);
            }
        }
    }

    /**
     * 得到子节点列表
     */
    private List<SysMenu> getChildList(List<SysMenu> list, SysMenu t) {
        List<SysMenu> tlist = new ArrayList<SysMenu>();
        Iterator<SysMenu> it = list.iterator();
        while (it.hasNext()) {
            SysMenu n = (SysMenu) it.next();
            if (n.getParentId().longValue() == t.getMenuId().longValue()) {
                tlist.add(n);
            }
        }
        return tlist;
    }

    /**
     * 判断是否有子节点
     */
    private boolean hasChild(List<SysMenu> list, SysMenu t) {
        return getChildList(list, t).size() > 0 ? true : false;
    }

    /**
     * 获取路由名称
     * 
     * @param menu 菜单信息
     * @return 路由名称
     */
    public String getRouteName(SysMenu menu) {
        String routerName = menu.getMenuName();
        // 非外链并且是一级目录（类型为目录）
        if (isMenuFrame(menu)) {
            routerName = "";
        }
        return routerName;
    }

    /**
     * 获取路由地址
     * 
     * @param menu 菜单信息
     * @return 路由地址
     */
    public String getRouterPath(SysMenu menu) {
        String routerPath = menu.getPath();
        // 内链打开外网方式
        if (menu.getParentId().intValue() != 0 && isInnerLink(menu)) {
            routerPath = innerLinkReplaceEach(routerPath);
        }
        // 非外链并且是一级目录（类型为目录）
        if (0 == menu.getParentId().intValue() && "M".equals(menu.getMenuType())
                && "1".equals(menu.getIsFrame())) {
            routerPath = "/" + menu.getPath();
        }
        // 非外链并且是一级目录（类型为菜单）
        else if (isMenuFrame(menu)) {
            routerPath = "/";
        }
        return routerPath;
    }

    /**
     * 获取组件信息
     * 
     * @param menu 菜单信息
     * @return 组件信息
     */
    public String getComponent(SysMenu menu) {
        String component = "Layout";
        if (menu.getComponent() != null && !isMenuFrame(menu)) {
            component = menu.getComponent();
        } else if (menu.getComponent() == null && menu.getParentId().intValue() != 0 && isInnerLink(menu)) {
            component = "InnerLink";
        } else if (menu.getComponent() == null && isParentView(menu)) {
            component = "ParentView";
        }
        return component;
    }

    /**
     * 获取路由元信息
     * 
     * @param menu 菜单信息
     * @return 路由元信息
     */
    public Map<String, Object> getMeta(SysMenu menu) {
        Map<String, Object> meta = new HashMap<String, Object>();
        meta.put("title", menu.getMenuName());
        meta.put("icon", menu.getIcon());
        meta.put("noCache", "1".equals(menu.getIsCache()));
        if (menu.getPath() != null && isInnerLink(menu)) {
            meta.put("link", menu.getPath());
        }
        return meta;
    }

    /**
     * 是否为菜单内部跳转
     * 
     * @param menu 菜单信息
     * @return 结果
     */
    public boolean isMenuFrame(SysMenu menu) {
        return menu.getParentId().intValue() == 0 && "C".equals(menu.getMenuType())
                && menu.getIsFrame().equals("1");
    }

    /**
     * 是否为内链组件
     * 
     * @param menu 菜单信息
     * @return 结果
     */
    public boolean isInnerLink(SysMenu menu) {
        return menu.getIsFrame().equals("1") && ishttp(menu.getPath());
    }

    /**
     * 是否为parent_view组件
     * 
     * @param menu 菜单信息
     * @return 结果
     */
    public boolean isParentView(SysMenu menu) {
        return menu.getParentId().intValue() != 0 && "M".equals(menu.getMenuType());
    }

    /**
     * 内链域名特殊字符替换
     * 
     * @return
     */
    public String innerLinkReplaceEach(String path) {
        return path.replaceAll("http://", "").replaceAll("https://", "").replaceAll("www.", "").replaceAll("\\.", "/");
    }

    /**
     * 判断是否是http(s)://开头
     * 
     * @param link 链接
     * @return 结果
     */
    public boolean ishttp(String link) {
        return link != null && (link.startsWith("http://") || link.startsWith("https://"));
    }
}