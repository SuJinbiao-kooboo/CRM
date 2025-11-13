package com.crm.system.controller;

import com.crm.common.core.domain.AjaxResult;
import com.crm.system.domain.SysMenu;
import com.crm.system.service.ISysMenuService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletResponse;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * 登录控制器
 * 
 * @author CRM System
 */
@Slf4j
@RestController
@CrossOrigin(origins = "*")
public class LoginController {

    @Autowired
    private ISysMenuService menuService;

    /**
     * 生成验证码
     */
    @GetMapping("/captchaImage")
    public AjaxResult getCaptchaImage() {
        // 生成验证码
        String captchaCode = generateCaptchaCode();
        String uuid = UUID.randomUUID().toString();
        
        // 这里简化处理，实际项目中应该将验证码存储到Redis或Session中
        Map<String, Object> result = new HashMap<>();
        result.put("uuid", uuid);
        result.put("img", generateBase64Image(captchaCode));
        result.put("captchaEnabled", true);
        
        return AjaxResult.success(result);
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public AjaxResult login(@RequestBody Map<String, String> loginForm) {
        String username = loginForm.get("username");
        String password = loginForm.get("password");
        
        // 简化的登录验证逻辑
        if ("admin".equals(username) && "admin123".equals(password)) {
            Map<String, Object> result = new HashMap<>();
            result.put("token", "mock-token-" + UUID.randomUUID().toString());
            return AjaxResult.success(result);
        }
        
        return AjaxResult.error("用户名或密码错误");
    }

    /**
     * 获取用户信息
     */
    @GetMapping("/getInfo")
    public AjaxResult getInfo() {
        Map<String, Object> user = new HashMap<>();
        user.put("user", createMockUser());
        user.put("roles", new String[]{"admin"});
        user.put("permissions", new String[]{"*:*:*"});
        
        return AjaxResult.success(user);
    }

    /**
     * 获取路由信息
     */
    @GetMapping("/getRouters")
    public AjaxResult getRouters() {
        log.info("1");
        // 从数据库读取菜单数据
        Long userId = 1L; // 暂时使用管理员用户ID，实际项目中应该从token中获取
        List<SysMenu> menus = menuService.selectMenuTreeByUserId(userId);
        return AjaxResult.success(menuService.buildMenus(menus));
    }

    /**
     * 用户登出
     */
    @PostMapping("/logout")
    public AjaxResult logout() {
        return AjaxResult.success("退出成功");
    }

    /**
     * 生成验证码字符串
     */
    private String generateCaptchaCode() {
        String chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        Random random = new Random();
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            code.append(chars.charAt(random.nextInt(chars.length())));
        }
        return code.toString();
    }

    /**
     * 生成验证码图片的Base64编码
     */
    private String generateBase64Image(String code) {
        try {
            int width = 120;
            int height = 40;
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            
            // 设置抗锯齿
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            // 设置背景色
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            
            // 设置字体
            g.setFont(new Font("Arial", Font.BOLD, 20));
            g.setColor(Color.BLACK);
            
            // 绘制验证码
            for (int i = 0; i < code.length(); i++) {
                g.drawString(String.valueOf(code.charAt(i)), 20 + i * 20, 25);
            }
            
            // 添加干扰线
            Random random = new Random();
            g.setColor(Color.GRAY);
            for (int i = 0; i < 5; i++) {
                int x1 = random.nextInt(width);
                int y1 = random.nextInt(height);
                int x2 = random.nextInt(width);
                int y2 = random.nextInt(height);
                g.drawLine(x1, y1, x2, y2);
            }
            
            g.dispose();
            
            // 转换为Base64，注意前端期望的是不带data:image前缀的纯Base64字符串
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            byte[] imageBytes = baos.toByteArray();
            return java.util.Base64.getEncoder().encodeToString(imageBytes);
            
        } catch (IOException e) {
            // 返回一个1x1像素的透明图片的Base64编码
            return "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==";
        }
    }

    /**
     * 创建模拟用户信息
     */
    private Map<String, Object> createMockUser() {
        Map<String, Object> user = new HashMap<>();
        user.put("userId", 1L);
        user.put("userName", "admin");
        user.put("nickName", "管理员");
        user.put("email", "admin@crm.com");
        user.put("phonenumber", "15888888888");
        user.put("sex", "1");
        user.put("avatar", null); // 修改为null，避免前端读取undefined属性
        user.put("deptId", 1L);
        user.put("deptName", "总公司");
        user.put("status", "0");
        user.put("createTime", "2024-01-01 00:00:00");
        return user;
    }

}