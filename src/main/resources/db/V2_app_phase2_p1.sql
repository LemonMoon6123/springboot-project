/*
 * 第 2 期 P1：核销 / 实名 / 搜索 / 想看
 * 前置：V2_app_phase1_p0.sql + seed_demo
 */
SET NAMES utf8mb4;
USE `livehouse_ticket`;

ALTER TABLE `tb_check_in_record`
  ADD COLUMN `gate` varchar(16) DEFAULT NULL COMMENT '入场口',
  ADD COLUMN `operator_name` varchar(32) DEFAULT NULL COMMENT '操作员姓名快照',
  ADD COLUMN `result` tinyint NOT NULL DEFAULT '1' COMMENT '核销结果：1成功 0失败',
  ADD COLUMN `fail_reason` varchar(64) DEFAULT NULL COMMENT '失败原因',
  ADD COLUMN `client_ip` varchar(64) DEFAULT NULL COMMENT '客户端IP',
  ADD KEY `idx_show_time` (`show_id`, `check_time`);

CREATE TABLE IF NOT EXISTS `tb_venue_staff` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `venue_id` bigint NOT NULL,
  `role` tinyint NOT NULL DEFAULT '1' COMMENT '1核销员 2场馆管理员',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1在职 0离职',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_venue` (`user_id`, `venue_id`),
  KEY `idx_venue` (`venue_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='场馆工作人员';

CREATE TABLE IF NOT EXISTS `tb_real_name_auth` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `real_name` varchar(32) NOT NULL,
  `id_type` tinyint NOT NULL DEFAULT '1',
  `id_card_enc` varchar(256) NOT NULL,
  `id_card_mask` varchar(32) NOT NULL,
  `phone` varchar(11) DEFAULT NULL,
  `auth_status` tinyint NOT NULL DEFAULT '0' COMMENT '0未认证 1审核中 2已认证 3已驳回',
  `auth_channel` varchar(16) DEFAULT NULL,
  `face_score` decimal(5,2) DEFAULT NULL,
  `reject_reason` varchar(128) DEFAULT NULL,
  `auth_time` datetime DEFAULT NULL,
  `modify_apply_status` tinyint NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户实名认证';

CREATE TABLE IF NOT EXISTS `tb_artist` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(128) NOT NULL,
  `avatar` varchar(512) DEFAULT NULL,
  `type` varchar(32) DEFAULT NULL,
  `city` varchar(32) DEFAULT NULL,
  `intro` text,
  `follow_count` int NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='艺人/乐队';

CREATE TABLE IF NOT EXISTS `tb_favorite` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `target_type` varchar(16) NOT NULL COMMENT 'show/artist/venue',
  `target_id` bigint NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_target` (`user_id`, `target_type`, `target_id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收藏';

INSERT INTO `tb_artist` (`id`, `name`, `type`, `city`, `intro`)
SELECT 301, '霓虹之下乐队', '摇滚', '上海', '联调艺人'
WHERE NOT EXISTS (SELECT 1 FROM `tb_artist` WHERE `id` = 301);

UPDATE `tb_performance` SET `artist_id` = 301 WHERE `id` = 101 AND `artist_id` IS NULL;

INSERT INTO `tb_venue_staff` (`user_id`, `venue_id`, `role`, `status`)
SELECT 1001, 4, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM `tb_venue_staff` WHERE `user_id` = 1001 AND `venue_id` = 4);