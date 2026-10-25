-- 微信指定聊天对象存量消息迁移
-- 新增聊天对象时，同时修改下面两处 IN 列表。
-- 执行前请选择 crm_me 库。

START TRANSACTION;

INSERT INTO wechat_message_black
    (username, name, local_id, server_id, type, sender, content, quote, quote_kind,
     media_path, create_time, sort_seq, md5)
SELECT username, name, local_id, server_id, type, sender, content, quote, quote_kind,
       media_path, create_time, sort_seq, md5
FROM wechat_message
WHERE name IN ('比特群', '灵犀智算5', '资源对接7群')
ON DUPLICATE KEY UPDATE username = VALUES(username);

DELETE FROM wechat_message
WHERE name IN ('比特群', '灵犀智算5', '资源对接7群');

COMMIT;
