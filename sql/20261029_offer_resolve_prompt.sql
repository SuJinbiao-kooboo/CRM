-- 2026-10-29 Offer AI 解析提示词：AMD/WD 料号、Samsung 系列与接口规格
-- 可重复执行；已存在标记时不会重复追加
UPDATE sys_config
SET config_value = CONCAT(RTRIM(config_value), '

# 料号与接口补充规则（优先执行，覆盖前文冲突内容）
1. AMD EPYC 的 partNumber 只取 EPYC 后的数字型号：EPYC9015/100-000001553 → 9015；EPYC9535/100-000001147 → 9535。model 分别写 EPYC 9015、EPYC 9535。100-开头的 OPN 不能作为 partNumber，可保留在 productDetail（如 OPN 100-000001553）。
2. 西部数据内部编码与正式型号同时出现时只取正式型号：0F62796-WUH722424ALE6L4 → WUH722424ALE6L4。0F开头的内部编码不能作为 partNumber；无法确认正式型号时不要猜测。
3. Samsung SSD 在能确认系列时，model 写系列名（如 PM1655、PM1653、PM893、PM9A3、PM9A1），partNumber 保留完整 SKU。MZILG1T6、MZILG3T2 属于 PM1655；MZILG960、MZILG1T9、MZILG3T8、MZILG7T6、MZILG15T、MZILG30T 属于 PM1653，严禁混用。
4. HDD 的 spec 补充接口、转速、尺寸：如 24TB SATA 6Gb/s 7200rpm 3.5-inch。
5. SSD 的 spec 补充总线、NVMe 和形态：如 1.6TB SAS 24Gb/s 2.5-inch、960GB PCIe 4.0 NVMe U.2。
6. RAM 的 spec 补充代际、速率和模组类型：如 64GB DDR4 3200 ECC RDIMM。
7. 只有原文或已确认型号能够支持时才补充，无法确认的项省略，不得编造。'), update_time = NOW()
WHERE config_key = 'sys.offer.resolve.tips.base'
  AND config_value NOT LIKE '%料号与接口补充规则（优先执行%';

UPDATE sys_config
SET config_value = CONCAT(RTRIM(config_value), '

# 料号与规格补充示例（优先执行）
示例A：AMD EPYC（斜杠前是型号，斜杠后是OPN）
输入：
EPYC9015/100-000001553 , 100 pcs
EPYC9535/100-000001147 , 6 pcs
输出：
{"items":[{"brand":"AMD","partNumber":"9015","model":"EPYC 9015","spec":"8-core 3.6GHz","quantity":100,"price":null,"currency":"USD","dc":"","coo":"","deliveryTime":"","productType":"CPU","productDetail":"EPYC 9015; OPN 100-000001553"},{"brand":"AMD","partNumber":"9535","model":"EPYC 9535","spec":"","quantity":6,"price":null,"currency":"USD","dc":"","coo":"","deliveryTime":"","productType":"CPU","productDetail":"EPYC 9535; OPN 100-000001147"}]}

示例B：西部数据（0F内部编码不能当料号）
输入：
0F62796-WUH722424ALE6L4 24T 9pcs 970USD
输出：
{"items":[{"brand":"Western Digital","partNumber":"WUH722424ALE6L4","model":"WUH722424ALE6L4","spec":"24TB SATA 6Gb/s 7200rpm 3.5-inch","quantity":9,"price":970,"currency":"USD","dc":"","coo":"","deliveryTime":"","productType":"HDD","productDetail":"Ultrastar DC HC580 24TB SATA 6Gb/s 7200rpm 3.5-inch"}]}

示例C：Samsung SSD 系列名与接口（partNumber保留SKU）
输入：
MZILG1T6HCJR-V7 1.6T 2pcs 400USD
MZILG15THBLA-00A07 15.36T 4pcs 6900USD
输出：
{"items":[{"brand":"Samsung","partNumber":"MZILG1T6HCJR-V7","model":"PM1655","spec":"1.6TB SAS 24Gb/s 2.5-inch","quantity":2,"price":400,"currency":"USD","dc":"","coo":"","deliveryTime":"","productType":"SSD","productDetail":"Samsung PM1655 1.6TB SAS 24Gb/s 2.5-inch"},{"brand":"Samsung","partNumber":"MZILG15THBLA-00A07","model":"PM1653","spec":"15.36TB SAS 24Gb/s 2.5-inch","quantity":4,"price":6900,"currency":"USD","dc":"","coo":"","deliveryTime":"","productType":"SSD","productDetail":"Samsung PM1653 15.36TB SAS 24Gb/s 2.5-inch"}]}

示例D：SSD与RAM接口
输入：
MZQL2960HCJR-00A07 960G 10pcs 850USD
M393A8G40CB4-CWEGY 64GB DDR4 3200 1463pcs 1400USD
输出：
{"items":[{"brand":"Samsung","partNumber":"MZQL2960HCJR-00A07","model":"PM9A3","spec":"960GB PCIe 4.0 NVMe U.2 2.5-inch","quantity":10,"price":850,"currency":"USD","dc":"","coo":"","deliveryTime":"","productType":"SSD","productDetail":"Samsung PM9A3 960GB PCIe 4.0 NVMe U.2"},{"brand":"Samsung","partNumber":"M393A8G40CB4-CWEGY","model":"M393A8G40CB4-CWEGY","spec":"64GB DDR4 3200 ECC RDIMM","quantity":1463,"price":1400,"currency":"USD","dc":"","coo":"","deliveryTime":"","productType":"RAM","productDetail":"Samsung 64GB DDR4 3200 ECC RDIMM"}]}
'), update_time = NOW()
WHERE config_key = 'sys.offer.resolve.tips'
  AND config_value NOT LIKE '%料号与规格补充示例（优先执行%';
