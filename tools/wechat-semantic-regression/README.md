# 微信语义规则回归

在项目根目录执行：

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-1.8'
& 'C:\Software\Maven-3.9.16\bin\mvn.cmd' -q -pl ruoyi-admin -am -DskipTests compile
& 'C:\Software\Maven-3.9.16\bin\mvn.cmd' -q -pl ruoyi-admin dependency:build-classpath '-Dmdep.outputFile=target/wechat-test-cp.txt' '-DincludeScope=runtime'
$cp = 'ruoyi-admin\target\classes;' + (Get-Content 'ruoyi-admin\target\wechat-test-cp.txt' -Raw).Trim()
& "$env:JAVA_HOME\bin\javac.exe" -encoding UTF-8 -cp $cp -d tools\wechat-semantic-regression\classes tools\wechat-semantic-regression\SemanticMatcherRegression.java
& "$env:JAVA_HOME\bin\java.exe" -cp "tools\wechat-semantic-regression\classes;$cp" SemanticMatcherRegression
```

退出码 0 表示全部断言通过。重点覆盖：

- `selfNames` 精确匹配，`May 星禾 SSD GPU` 不误判成我方 `May`；
- INQ 双向成立：客户可向我方询价，我方也可向供应商询价；
- OFFER 必须是不同发送人的对向带价回应：我方或供应商发出均成立，同人自答和群发广告不通过；
- TRACE 可以是疑问句，也可以是价格确认、数量调整、下单安排等陈述，但必须有业务要素和时间关联；
- 单独“可以/行”没有引用或料号/数量锚点时不算 TRACE；
- 型号+数量+完整价格的纯报价陈述不能反向误判为 INQ；
- 普通寒暄、引用报价但无业务内容的“在吗”不通过。
