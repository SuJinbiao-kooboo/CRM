CREATE TABLE IF NOT EXISTS `do_user` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `openid` VARCHAR(64) NOT NULL COMMENT '微信openid',
  `nickname` VARCHAR(64) NULL COMMENT '昵称',
  `avatar` VARCHAR(255) NULL COMMENT '头像URL',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_do_user_openid` (`openid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户主表';

CREATE TABLE IF NOT EXISTS `do_user_setting` (
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `profile_visibility` VARCHAR(20) NOT NULL DEFAULT 'buddy' COMMENT '资料可见范围: public/buddy/private',
  `allow_buddy_invite` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '允许被邀请为搭子(1是/0否)',
  `notification_pref` JSON NULL COMMENT '通知偏好JSON',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`user_id`),
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户设置';

CREATE TABLE IF NOT EXISTS `do_plan` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '计划创建者用户ID',
  `title` VARCHAR(100) NOT NULL COMMENT '计划标题',
  `type` VARCHAR(50) NOT NULL COMMENT '计划类型',
  `frequency` VARCHAR(50) NOT NULL COMMENT '打卡频率',
  `start_date` DATE NOT NULL COMMENT '开始日期',
  `end_date` DATE NULL COMMENT '结束日期',
  `visibility` VARCHAR(20) NOT NULL DEFAULT 'buddy' COMMENT '计划可见性: public/buddy/private',
  `status` VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT '计划状态: active/paused/archived',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_do_plan_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='计划主表';

CREATE TABLE IF NOT EXISTS `do_plan_buddy` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `plan_id` BIGINT UNSIGNED NOT NULL COMMENT '计划ID',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '计划发起人用户ID',
  `buddy_user_id` BIGINT UNSIGNED NOT NULL COMMENT '搭子用户ID',
  `status` VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT '绑定状态: active/removed',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_do_plan_buddy_plan_buddy` (`plan_id`, `buddy_user_id`),
  KEY `idx_do_plan_buddy_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='计划-搭子绑定';

CREATE TABLE IF NOT EXISTS `do_buddy_relation` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `buddy_id` BIGINT UNSIGNED NOT NULL COMMENT '搭子用户ID',
  `permission` VARCHAR(20) NOT NULL DEFAULT 'normal' COMMENT '搭子权限: normal/full',
  `status` VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT '关系状态: active/pending/removed',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_do_buddy_relation_user_buddy` (`user_id`,`buddy_id`),
  KEY `idx_do_buddy_relation_buddy_id` (`buddy_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='搭子关系';

CREATE TABLE IF NOT EXISTS `do_checkin` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `plan_id` BIGINT UNSIGNED NOT NULL COMMENT '计划ID',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `content` TEXT NULL COMMENT '文本内容',
  `media_url` VARCHAR(255) NULL COMMENT '媒体链接(COS/OSS)',
  `mood` VARCHAR(20) NULL COMMENT '心情',
  `difficulty` TINYINT UNSIGNED NULL COMMENT '难度(0-9)',
  `is_supplement` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否补卡(1是/0否)',
  `checkin_date` DATE NOT NULL COMMENT '打卡日期',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_do_checkin_plan_user_date` (`plan_id`, `user_id`, `checkin_date`),
  KEY `idx_do_checkin_user_date` (`user_id`, `checkin_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='打卡记录';

CREATE TABLE IF NOT EXISTS `do_energy_log` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `plan_id` BIGINT UNSIGNED NULL COMMENT '关联计划ID(可空)',
  `change_value` INT NOT NULL COMMENT '能量变更值(正负均可)',
  `reason` VARCHAR(100) NOT NULL COMMENT '变更原因',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_do_energy_log_user_id` (`user_id`),
  KEY `idx_do_energy_log_plan_id` (`plan_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='能量流水';

CREATE TABLE IF NOT EXISTS `do_achievement` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `code` VARCHAR(64) NOT NULL COMMENT '成就编码',
  `name` VARCHAR(100) NOT NULL COMMENT '成就名称',
  `description` TEXT NULL COMMENT '成就描述',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_do_achievement_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='成就定义';

CREATE TABLE IF NOT EXISTS `do_user_achievement` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `achievement_id` BIGINT UNSIGNED NOT NULL COMMENT '成就ID',
  `plan_id` BIGINT UNSIGNED NULL COMMENT '关联计划ID(可空)',
  `earned_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '达成时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_do_user_achievement_user_ach` (`user_id`,`achievement_id`),
  KEY `idx_do_user_achievement_plan_id` (`plan_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户成就记录';

CREATE TABLE IF NOT EXISTS `do_notification` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `type` VARCHAR(50) NOT NULL COMMENT '通知类型',
  `title` VARCHAR(100) NOT NULL COMMENT '标题',
  `content` TEXT NOT NULL COMMENT '内容',
  `status` VARCHAR(20) NOT NULL DEFAULT 'unread' COMMENT '状态: unread/read',
  `read_at` DATETIME NULL COMMENT '阅读时间(可空)',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_do_notification_user_status` (`user_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='通知';
