-- =====================================================================
-- 微信排除会话存量消息迁移（MySQL 8.0+）
--
-- 作用：
--   读取 wechat_analysis_config 中的 blackNames、pollIgnoreChats，
--   按“会话名包含关键词、不区分大小写”的规则，把 wechat_message 中
--   已存在的消息复制到 wechat_message_black，再从主表删除。
--
-- 使用方式：
--   1. 先执行整个文件；末尾默认只做 DRY RUN，不修改消息数据。
--   2. 查看输出的关键词、命中会话和消息数量，确认无误。
--   3. 手工执行：CALL migrate_wechat_blacklist_messages(1);
--      正式迁移成功后才从主表删除；失败会自动回滚。
--   4. 以后新增排除关键词，保存字典后重新执行第 1、2、3 步即可。
--
-- 注意：
--   * 当前连接必须选择 crm_me 库。
--   * 只迁移原始消息；wechat_message_black 不保存分类、录入、任务状态字段。
--   * 删除前会确认黑表中已存在相同的 username + local_id，避免并发新消息未复制就被删除。
--   * 可重复执行，不会在 wechat_message_black 中产生重复消息。
-- =====================================================================

DROP PROCEDURE IF EXISTS migrate_wechat_blacklist_messages;

DELIMITER $$

CREATE PROCEDURE migrate_wechat_blacklist_messages(IN p_execute TINYINT)
BEGIN
    DECLARE v_matched BIGINT DEFAULT 0;
    DECLARE v_copied BIGINT DEFAULT 0;
    DECLARE v_deleted BIGINT DEFAULT 0;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    DROP TEMPORARY TABLE IF EXISTS tmp_wechat_blacklist_keys;
    CREATE TEMPORARY TABLE tmp_wechat_blacklist_keys (
        keyword VARCHAR(255) NOT NULL,
        PRIMARY KEY (keyword)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

    INSERT IGNORE INTO tmp_wechat_blacklist_keys (keyword)
    WITH RECURSIVE seq(n) AS (
        SELECT 1
        UNION ALL
        SELECT n + 1 FROM seq WHERE n < 512
    ),
    cfg AS (
        SELECT dict_value
        FROM sys_dict_data
        WHERE dict_type = 'wechat_analysis_config'
          AND dict_label IN ('blackNames', 'pollIgnoreChats')
          AND dict_value IS NOT NULL
          AND TRIM(dict_value) <> ''
    )
    SELECT DISTINCT
           LOWER(TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(cfg.dict_value, ',', seq.n), ',', -1)))
    FROM cfg
    JOIN seq
      ON seq.n <= 1 + CHAR_LENGTH(cfg.dict_value)
                     - CHAR_LENGTH(REPLACE(cfg.dict_value, ',', ''))
    WHERE LOWER(TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(cfg.dict_value, ',', seq.n), ',', -1))) <> '';

    SELECT keyword AS black_keyword
    FROM tmp_wechat_blacklist_keys
    ORDER BY keyword;

    SELECT m.name AS chat_name,
           COUNT(*) AS message_count,
           MIN(m.create_time) AS first_message_time,
           MAX(m.create_time) AS last_message_time
    FROM wechat_message m
    WHERE EXISTS (
        SELECT 1
        FROM tmp_wechat_blacklist_keys k
        WHERE LOWER(m.name) LIKE CONCAT('%', k.keyword, '%')
    )
    GROUP BY m.name
    ORDER BY message_count DESC, chat_name;

    SELECT COUNT(*) INTO v_matched
    FROM wechat_message m
    WHERE EXISTS (
        SELECT 1
        FROM tmp_wechat_blacklist_keys k
        WHERE LOWER(m.name) LIKE CONCAT('%', k.keyword, '%')
    );

    IF COALESCE(p_execute, 0) = 0 THEN
        SELECT 'DRY_RUN' AS mode,
               v_matched AS matched_messages,
               0 AS copied_messages,
               0 AS deleted_messages,
               '未修改数据；确认后执行 CALL migrate_wechat_blacklist_messages(1)' AS message;
    ELSE
        START TRANSACTION;

        INSERT INTO wechat_message_black
            (username, name, local_id, server_id, type, sender, content, quote, quote_kind,
             media_path, create_time, sort_seq, md5)
        SELECT m.username, m.name, m.local_id, m.server_id, m.type, m.sender, m.content,
               m.quote, m.quote_kind, m.media_path, m.create_time, m.sort_seq, m.md5
        FROM wechat_message m
        WHERE EXISTS (
            SELECT 1
            FROM tmp_wechat_blacklist_keys k
            WHERE LOWER(m.name) LIKE CONCAT('%', k.keyword, '%')
        )
        ON DUPLICATE KEY UPDATE username = VALUES(username);

        SET v_copied = ROW_COUNT();

        DELETE m
        FROM wechat_message m
        WHERE EXISTS (
            SELECT 1
            FROM tmp_wechat_blacklist_keys k
            WHERE LOWER(m.name) LIKE CONCAT('%', k.keyword, '%')
        )
          AND EXISTS (
            SELECT 1
            FROM wechat_message_black b
            WHERE b.username = m.username
              AND b.local_id = m.local_id
        );

        SET v_deleted = ROW_COUNT();
        COMMIT;

        SELECT 'MIGRATED' AS mode,
               v_matched AS matched_messages,
               v_copied AS copied_messages,
               v_deleted AS deleted_messages,
               '迁移完成；如 matched_messages 大于 deleted_messages，请再次执行本过程处理并发新消息' AS message;

        SELECT COUNT(*) AS remaining_matched_messages
        FROM wechat_message m
        WHERE EXISTS (
            SELECT 1
            FROM tmp_wechat_blacklist_keys k
            WHERE LOWER(m.name) LIKE CONCAT('%', k.keyword, '%')
        );
    END IF;

    DROP TEMPORARY TABLE IF EXISTS tmp_wechat_blacklist_keys;
END$$

DELIMITER ;

-- 默认安全预览，不修改任何消息数据。
CALL migrate_wechat_blacklist_messages(0);

-- 确认预览结果后，手工执行下面这句进行正式迁移：
-- CALL migrate_wechat_blacklist_messages(1);
