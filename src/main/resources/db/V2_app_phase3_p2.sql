/*
 * LiveHouse App 第 3 期 · 会员/积分/评论/消息
 * 前置：livehouse_ticket.sql + V2_app_phase1_p0.sql（+ phase2 若已跑）
 * 幂等：CREATE IF NOT EXISTS；种子 INSERT IGNORE / WHERE NOT EXISTS
 */
SET NAMES utf8mb4;
USE `livehouse_ticket`;

CREATE TABLE IF NOT EXISTS `tb_credit_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `change_amount` int NOT NULL COMMENT '变动值，正为增负为减',
  `balance` int NOT NULL COMMENT '变动后可用积分余额',
  `type` varchar(16) NOT NULL COMMENT 'purchase/signin/exchange/expire/adjust',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '0待发放 1已发放 2已过期',
  `release_time` datetime DEFAULT NULL,
  `expire_time` datetime DEFAULT NULL,
  `biz_id` varchar(64) DEFAULT NULL,
  `remark` varchar(128) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_status_time` (`user_id`, `status`, `create_time`),
  KEY `idx_biz` (`biz_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分流水';

CREATE TABLE IF NOT EXISTS `tb_credit_goods` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(128) NOT NULL,
  `icon` varchar(32) DEFAULT NULL,
  `image` varchar(512) DEFAULT NULL,
  `credit_price` int NOT NULL,
  `goods_type` varchar(16) NOT NULL DEFAULT 'goods',
  `stock` int NOT NULL DEFAULT '0',
  `per_user_limit` int NOT NULL DEFAULT '1',
  `status` tinyint NOT NULL DEFAULT '1',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分兑换商品';

CREATE TABLE IF NOT EXISTS `tb_credit_exchange` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `goods_id` bigint NOT NULL,
  `credit_cost` int NOT NULL,
  `redeem_code` varchar(16) NOT NULL,
  `status` tinyint NOT NULL DEFAULT '0',
  `idempotent_key` varchar(64) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `redeem_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_redeem_code` (`redeem_code`),
  UNIQUE KEY `uk_exchange_idem` (`idempotent_key`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分兑换记录';

CREATE TABLE IF NOT EXISTS `tb_show_comment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `performance_id` bigint NOT NULL,
  `order_id` bigint DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `user_name` varchar(32) DEFAULT NULL,
  `rating` tinyint NOT NULL DEFAULT '5',
  `content` varchar(500) NOT NULL,
  `images` json DEFAULT NULL,
  `like_count` int NOT NULL DEFAULT '0',
  `is_featured` tinyint NOT NULL DEFAULT '0',
  `status` tinyint NOT NULL DEFAULT '1',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_comment` (`order_id`),
  KEY `idx_perf_featured` (`performance_id`, `is_featured`, `create_time`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='演出评论';

CREATE TABLE IF NOT EXISTS `tb_message` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `type` varchar(16) NOT NULL,
  `title` varchar(64) NOT NULL,
  `content` varchar(256) NOT NULL,
  `biz_type` varchar(16) DEFAULT NULL,
  `biz_id` varchar(64) DEFAULT NULL,
  `is_read` tinyint NOT NULL DEFAULT '0',
  `read_time` datetime DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_read` (`user_id`, `is_read`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内消息';

-- user_info 观演场次（若 phase2 未加）
SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tb_user_info' AND COLUMN_NAME = 'watch_count');
SET @sql := IF(@col = 0,
  'ALTER TABLE `tb_user_info` ADD COLUMN `watch_count` int NOT NULL DEFAULT 0 COMMENT ''累计观演场次''',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 四档 benefits_json
UPDATE `tb_member_level` SET `benefits_json` = '[
  {"name":"在线购票","desc":"浏览全部演出、实时余票与场次"},
  {"name":"积分获取","desc":"观演核销后获得积分"}
]' WHERE `level` = 0;

UPDATE `tb_member_level` SET `benefits_json` = '[
  {"name":"优先购票","desc":"热门演出提前 15 分钟开售"},
  {"name":"专属折扣","desc":"购票自动 9.8 折"},
  {"name":"积分获取","desc":"观演核销后获得积分"}
]' WHERE `level` = 1;

UPDATE `tb_member_level` SET `benefits_json` = '[
  {"name":"优先购票","desc":"热门演出提前 30 分钟开售"},
  {"name":"专属折扣","desc":"购票自动 9.5 折"},
  {"name":"生日礼遇","desc":"生日月专属福利"}
]' WHERE `level` = 2;

UPDATE `tb_member_level` SET `benefits_json` = '[
  {"name":"优先购票","desc":"热门演出提前 45 分钟开售"},
  {"name":"专属折扣","desc":"购票自动 9.2 折"},
  {"name":"优先入场","desc":"部分场次优先入场名额"}
]' WHERE `level` = 3;

INSERT INTO `tb_credit_goods` (`id`, `name`, `icon`, `credit_price`, `goods_type`, `stock`, `per_user_limit`, `status`)
SELECT 1, '演出纪念徽章', 'star', 120, 'goods', 50, 2, 1
WHERE NOT EXISTS (SELECT 1 FROM `tb_credit_goods` WHERE `id` = 1);

INSERT INTO `tb_credit_goods` (`id`, `name`, `icon`, `credit_price`, `goods_type`, `stock`, `per_user_limit`, `status`)
SELECT 2, '优先入场名额', 'bolt', 300, 'privilege', 20, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM `tb_credit_goods` WHERE `id` = 2);

INSERT INTO `tb_show_comment` (`id`, `performance_id`, `user_id`, `user_name`, `rating`, `content`, `like_count`, `is_featured`, `status`)
SELECT 201, 101, 1001, '摇滚万岁', 5, '现场氛围绝了，鼓点一出全场沸腾。', 132, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM `tb_show_comment` WHERE `id` = 201);

-- 联调消息种子（用户 1001）
INSERT INTO `tb_message` (`user_id`, `type`, `title`, `content`, `biz_type`, `biz_id`, `is_read`)
SELECT 1001, 'remind', '你关注的「霓虹之下」开售了', '11/15 城市摇滚夜已开售，余票有限。', 'show', '101', 0
WHERE NOT EXISTS (SELECT 1 FROM `tb_message` WHERE `user_id` = 1001 AND `title` LIKE '你关注的%开售%');

INSERT INTO `tb_message` (`user_id`, `type`, `title`, `content`, `biz_type`, `biz_id`, `is_read`)
SELECT 1001, 'order', '订单支付成功', '你的订单已支付成功，电子票已发放至票夹。', 'order', '0', 0
WHERE NOT EXISTS (SELECT 1 FROM `tb_message` WHERE `user_id` = 1001 AND `type` = 'order' AND `title` = '订单支付成功');