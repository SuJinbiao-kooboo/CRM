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
- INQ 必须是客户询价，纯数量、陈述句、群发广告不通过；
- OFFER 必须是我方对客户 INQ 的带价回应，引用但无价格不通过；
- TRACE 必须是带业务要素的疑问句，普通寒暄和无关联陈述不通过。
