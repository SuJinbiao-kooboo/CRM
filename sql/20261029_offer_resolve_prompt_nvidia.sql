-- 2026-10-29 Offer AI 解析提示词：NVIDIA RTX 5090 / RTX PRO 6000 料号规范
-- 可重复执行；已存在标记时不会重复追加

UPDATE sys_config
SET config_value = CONCAT(RTRIM(config_value), '

# NVIDIA GPU 料号补充规则（优先执行，覆盖前文冲突内容）
1. NVIDIA GeForce RTX 5090（包括5090、RTX5090、RTX 5090及风扇/涡轮/品牌/OC等描述）统一 partNumber=RTX509032G、model=RTX 5090、productType=GPU，spec补32GB GDDR7；5090D、RTX 5090D、5090D V2是不同型号，禁止套用。
2. NVIDIA RTX PRO 6000 Server Edition/服务器版统一 partNumber=PRO6000SE96G、model=RTX PRO 6000 SE；Workstation Edition/工作站版统一 partNumber=PRO6000WS96G、model=RTX PRO 6000 WS；两者 productType=GPU，spec补96GB GDDR7。
3. 只写PRO 6000且无法确认SE/WS时 partNumber=PRO6000，不得猜测版本；PRO6000D、PRO 6000D、MAX-Q/Max~Q是其他型号，保持原样；AMD Radeon Pro 6000不是NVIDIA，禁止套用。'), update_time = NOW()
WHERE config_key = 'sys.offer.resolve.tips.base'
  AND config_value NOT LIKE '%NVIDIA GPU 料号补充规则（优先执行%';

UPDATE sys_config
SET config_value = CONCAT(RTRIM(config_value), '

# NVIDIA GPU 示例（优先执行）
示例E：RTX 5090 与 RTX PRO 6000
输入：
RTX 5090 32G 涡轮卡 5pcs 2300USD
Pro 6000 Server Edition 96G 2pcs 8000USD
Pro 6000 Workstation Edition 96G 1pcs 8500USD
输出：
{"items":[{"brand":"NVIDIA","partNumber":"RTX509032G","model":"RTX 5090","spec":"32GB GDDR7","quantity":5,"price":2300,"currency":"USD","dc":"","coo":"","deliveryTime":"","productType":"GPU","productDetail":"RTX 5090 32G 涡轮卡"},{"brand":"NVIDIA","partNumber":"PRO6000SE96G","model":"RTX PRO 6000 SE","spec":"96GB GDDR7","quantity":2,"price":8000,"currency":"USD","dc":"","coo":"","deliveryTime":"","productType":"GPU","productDetail":"RTX PRO 6000 Server Edition 96GB"},{"brand":"NVIDIA","partNumber":"PRO6000WS96G","model":"RTX PRO 6000 WS","spec":"96GB GDDR7","quantity":1,"price":8500,"currency":"USD","dc":"","coo":"","deliveryTime":"","productType":"GPU","productDetail":"RTX PRO 6000 Workstation Edition 96GB"}]}

示例F：以下情况禁止误归一化
RTX 5090D V2 24G → partNumber保持RTX 5090D V2 24G，禁止改成RTX509032G。
Pro 6000 96G（未写SE/WS）→ partNumber=PRO6000，禁止猜成PRO6000SE96G或PRO6000WS96G。
AMD Radeon Pro 6000 → 不是NVIDIA，保持原样。'), update_time = NOW()
WHERE config_key = 'sys.offer.resolve.tips'
  AND config_value NOT LIKE '%NVIDIA GPU 示例（优先执行%';