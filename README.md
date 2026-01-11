### 项目启动介绍
1. 添加防火墙端口：
    firewall-cmd --zone=public --add-port=8080/tcp --permanent
    firewall-cmd --reload
2. 修改后端端口：
    修改 vue.config.js 中的 
    const baseUrl = 'http://114.132.93.127:8080' // 后端接口
3. 修改前端端口：
    修改 vue.config.js 中的 
    const port = process.env.port || process.env.npm_config_port || 81 // 端口
4. 前端复制代码过去的时候要解压 unzip 压缩包
    还需要赋值执行权限 
    sudo chmod -R 755 /opt/soft/crm/ruoyi-ui/node_modules
5. 启动脚本：
    前端：/usr/bin/nohup npm run dev & 
    后端：/usr/bin/nohup java -jar ruoyi-admin.jar &
