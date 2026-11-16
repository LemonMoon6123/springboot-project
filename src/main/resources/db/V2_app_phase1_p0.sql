/*
 * LiveHouse App 第 1 期 P0 数据层变更
 * 前置：已执行 livehouse_ticket.sql 基线
 * 执行：mysql -u root -p livehouse_ticket < V2_app_phase1_p0.sql
 * 回滚：需按段逆向 DROP/ALTER（生产建议备份后执行）
 */
SET NAMES utf8mb4;
USE `livehouse_ticket`;

-- =============================================================================
-- §1 新建表（6 张）
-- =============================================================================

CREATE TABLE IF NOT EXISTS `tb_performance` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '演出项目ID',
  `title` varchar(256) NOT NULL COMMENT '演出标题（跨场次共用）',
  `artist_id` bigint DEFAULT NULL COMMENT '主艺人ID',
  `artist_name` varchar(128) NOT NULL COMMENT '艺人/乐队（冗余，列表直出）',
  `type` varchar(32) DEFAULT NULL COMMENT '演出类型：摇滚/民谣/电子/独立',
  `city` varchar(32) DEFAULT NULL COMMENT '主要城市（巡演取首站）',
  `poster` varchar(512) DEFAULT NULL COMMENT '主海报',
  `poster_tag` varchar(32) DEFAULT NULL COMMENT '海报角标文案',
  `description` text COMMENT '演出介绍',
  `is_real_name` tinyint NOT NULL DEFAULT '1' COMMENT '是否实名制：0否 1是',
  `min_price` decimal(10,2) DEFAULT NULL COMMENT '最低票价（冗余）',
  `session_count` int NOT NULL DEFAULT '0' COMMENT '在售场次数',
  `want_count` int NOT NULL DEFAULT '0' COMMENT '想看人数',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1草稿 2已上架 3已下架',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_artist` (`artist_id`),
  KEY `idx_city_status` (`city`, `status`),
  KEY `idx_type` (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='演出项目表（一场巡演的聚合根）';

CREATE TABLE IF NOT EXISTS `tb_audience` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '所属用户',
  `name` varchar(32) NOT NULL COMMENT '姓名',
  `id_type` tinyint NOT NULL DEFAULT '1' COMMENT '证件类型：1身份证 2护照 3港澳通行证',
  `id_card_enc` varchar(256) NOT NULL COMMENT '证件号（AES 加密存储）',
  `id_card_mask` varchar(32) NOT NULL COMMENT '证件号脱敏展示',
  `id_card_hash` char(64) NOT NULL COMMENT '证件号 SHA256',
  `phone` varchar(11) DEFAULT NULL COMMENT '联系人手机号',
  `is_default` tinyint NOT NULL DEFAULT '0' COMMENT '是否默认观演人',
  `auth_status` tinyint NOT NULL DEFAULT '0' COMMENT '实名状态：0未认证 1已认证',
  `last_used_time` datetime DEFAULT NULL COMMENT '最近使用时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0正常 1已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_card` (`user_id`, `id_card_hash`),
  KEY `idx_user` (`user_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='常用观演人';

CREATE TABLE IF NOT EXISTS `tb_order_audience` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL,
  `audience_id` bigint NOT NULL COMMENT '观演人ID',
  `audience_name` varchar(32) NOT NULL COMMENT '姓名快照',
  `id_card_mask` varchar(32) NOT NULL COMMENT '证件号脱敏快照',
  `ticket_id` bigint DEFAULT NULL COMMENT '支付后回填的电子票ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_audience` (`order_id`, `audience_id`),
  KEY `idx_order` (`order_id`),
  KEY `idx_ticket` (`ticket_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单观演人关联';

CREATE TABLE IF NOT EXISTS `tb_payment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `channel` varchar(16) NOT NULL COMMENT '支付渠道：wxpay/alipay/mock',
  `trade_no` varchar(64) DEFAULT NULL COMMENT '第三方交易号',
  `prepay_id` varchar(128) DEFAULT NULL COMMENT '微信预支付ID',
  `amount` decimal(10,2) NOT NULL COMMENT '支付金额',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0待支付 1成功 2失败 3已关闭 4已退款',
  `expire_time` datetime DEFAULT NULL COMMENT '支付单过期时间',
  `notify_time` datetime DEFAULT NULL COMMENT '回调到达时间',
  `raw_notify` text COMMENT '原始回调报文',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_channel` (`order_id`, `channel`),
  KEY `idx_trade_no` (`trade_no`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付流水表';

CREATE TABLE IF NOT EXISTS `tb_notice` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `performance_id` bigint DEFAULT NULL COMMENT '演出项目ID，NULL=全局默认须知',
  `category` varchar(16) NOT NULL COMMENT '分类：购票/退改/入场/实名',
  `sort_no` int NOT NULL DEFAULT '0',
  `content` varchar(256) NOT NULL COMMENT '单条须知文案',
  `status` tinyint NOT NULL DEFAULT '1',
  PRIMARY KEY (`id`),
  KEY `idx_perf` (`performance_id`, `sort_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购票观演须知';

CREATE TABLE IF NOT EXISTS `tb_member_level` (
  `level` tinyint NOT NULL COMMENT '0普通 1黑胶卡 2白银卡 3鎏金卡',
  `name` varchar(32) NOT NULL COMMENT '等级名',
  `min_total_credits` int NOT NULL COMMENT '升级所需累计积分',
  `discount_rate` decimal(4,2) NOT NULL DEFAULT '1.00' COMMENT '购票折扣率',
  `priority_minutes` int NOT NULL DEFAULT '0' COMMENT '优先购提前分钟数',
  `benefits_json` json DEFAULT NULL COMMENT '权益列表',
  `card_bg` varchar(256) DEFAULT NULL COMMENT '卡面渐变配置',
  `status` tinyint NOT NULL DEFAULT '1',
  PRIMARY KEY (`level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员等级与权益配置';

-- =============================================================================
-- §2 ALTER 现有表
-- =============================================================================

ALTER TABLE `tb_show`
  ADD COLUMN `performance_id` bigint DEFAULT NULL COMMENT '所属演出项目ID' AFTER `id`,
  ADD COLUMN `session_label` varchar(64) DEFAULT NULL COMMENT '场次标签' AFTER `start_time`,
  ADD COLUMN `is_real_name` tinyint NOT NULL DEFAULT '1' COMMENT '是否实名制',
  ADD COLUMN `min_price` decimal(10,2) DEFAULT NULL COMMENT '该场次最低票价',
  ADD COLUMN `sell_count` int NOT NULL DEFAULT '0' COMMENT '已售张数',
  ADD COLUMN `gate_config` varchar(128) DEFAULT NULL COMMENT '入场口配置',
  ADD KEY `idx_performance` (`performance_id`),
  ADD KEY `idx_perf_start` (`performance_id`, `start_time`);

ALTER TABLE `tb_ticket_type`
  ADD COLUMN `description` varchar(128) DEFAULT NULL COMMENT '票档说明',
  ADD COLUMN `per_order_limit` int NOT NULL DEFAULT '4' COMMENT '每单最多购买数',
  ADD COLUMN `sold_count` int NOT NULL DEFAULT '0' COMMENT '已售数量',
  ADD COLUMN `sort_no` int NOT NULL DEFAULT '0' COMMENT '排序',
  ADD COLUMN `status` tinyint NOT NULL DEFAULT '1' COMMENT '1上架 0下架';

ALTER TABLE `tb_ticket_order`
  ADD COLUMN `order_no` varchar(32) DEFAULT NULL COMMENT '业务订单号' AFTER `id`,
  ADD COLUMN `total_amount` decimal(10,2) DEFAULT NULL COMMENT '票面总额' AFTER `amount`,
  ADD COLUMN `service_fee` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '服务费',
  ADD COLUMN `discount_amount` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '优惠金额',
  ADD COLUMN `pay_amount` decimal(10,2) DEFAULT NULL COMMENT '实付金额',
  ADD COLUMN `user_coupon_id` bigint DEFAULT NULL COMMENT '历史兼容，恒为 NULL',
  ADD COLUMN `member_level` tinyint DEFAULT NULL COMMENT '下单时会员等级快照',
  ADD COLUMN `member_discount_amount` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '会员优惠金额',
  ADD COLUMN `expire_time` datetime DEFAULT NULL COMMENT '支付截止时间',
  ADD COLUMN `cancel_time` datetime DEFAULT NULL COMMENT '取消时间',
  ADD COLUMN `cancel_reason` varchar(64) DEFAULT NULL COMMENT '取消原因',
  ADD COLUMN `channel` varchar(16) DEFAULT NULL COMMENT '下单渠道',
  ADD COLUMN `idempotent_key` varchar(64) DEFAULT NULL COMMENT '客户端幂等键',
  ADD COLUMN `session_id` bigint DEFAULT NULL COMMENT '场次ID',
  ADD COLUMN `entry_status` tinyint DEFAULT NULL COMMENT '入场子状态',
  ADD COLUMN `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '列表软删除',
  ADD UNIQUE KEY `uk_order_no` (`order_no`),
  ADD UNIQUE KEY `uk_idempotent` (`idempotent_key`),
  ADD KEY `idx_user_status_time` (`user_id`, `order_status`, `create_time`),
  ADD KEY `idx_expire` (`order_status`, `expire_time`);

UPDATE `tb_ticket_order` SET `total_amount` = `amount`, `pay_amount` = `amount` WHERE `pay_amount` IS NULL;

ALTER TABLE `tb_electronic_ticket`
  MODIFY COLUMN `verify_code` varchar(128) NOT NULL COMMENT '核销码（加密）';

ALTER TABLE `tb_electronic_ticket`
  ADD COLUMN `display_code` varchar(16) DEFAULT NULL COMMENT '展示码' AFTER `verify_code`,
  ADD COLUMN `short_code` char(6) DEFAULT NULL COMMENT '6位数字入场码' AFTER `display_code`,
  ADD COLUMN `audience_id` bigint DEFAULT NULL COMMENT '绑定的观演人ID',
  ADD COLUMN `audience_name` varchar(32) DEFAULT NULL COMMENT '观演人姓名快照',
  ADD COLUMN `audience_id_card_mask` varchar(32) DEFAULT NULL COMMENT '证件号脱敏快照',
  ADD COLUMN `gate` varchar(16) DEFAULT NULL COMMENT '入场口',
  ADD COLUMN `seat_info` varchar(64) DEFAULT NULL COMMENT '座位信息',
  ADD UNIQUE KEY `uk_short_code` (`show_id`, `short_code`),
  ADD KEY `idx_audience` (`audience_id`);

ALTER TABLE `tb_refund_record`
  ADD COLUMN `channel_refund_no` varchar(64) DEFAULT NULL COMMENT '微信退款单号' AFTER `finish_time`;

ALTER TABLE `tb_user`
  ADD COLUMN `status` tinyint NOT NULL DEFAULT '1' COMMENT '账号状态：1正常 0封禁',
  ADD COLUMN `wx_openid` varchar(64) DEFAULT NULL COMMENT '微信小程序 openid',
  ADD COLUMN `wx_unionid` varchar(64) DEFAULT NULL COMMENT '微信 unionid',
  ADD COLUMN `last_login_time` datetime DEFAULT NULL COMMENT '最后登录时间',
  ADD UNIQUE KEY `uk_openid` (`wx_openid`),
  ADD KEY `idx_unionid` (`wx_unionid`);

-- =============================================================================
-- §3 存量迁移 show → performance
-- =============================================================================

INSERT INTO `tb_performance` (`title`, `artist_name`, `type`, `city`, `poster`, `description`, `is_real_name`, `status`, `want_count`)
SELECT
  MIN(s.`title`) AS title,
  s.`artist` AS artist_name,
  MIN(s.`type`) AS type,
  MIN(v.`city`) AS city,
  MIN(s.`image`) AS poster,
  MIN(s.`description`) AS description,
  1 AS is_real_name,
  2 AS status,
  0 AS want_count
FROM `tb_show` s
INNER JOIN `tb_venue` v ON v.`id` = s.`venue_id`
WHERE s.`performance_id` IS NULL
GROUP BY s.`artist`, s.`title`;

UPDATE `tb_show` s
INNER JOIN `tb_performance` p ON p.`artist_name` = s.`artist` AND p.`title` = s.`title`
SET s.`performance_id` = p.`id`
WHERE s.`performance_id` IS NULL;

UPDATE `tb_show` s
SET s.`min_price` = (
  SELECT MIN(tt.`price`) FROM `tb_ticket_type` tt WHERE tt.`show_id` = s.`id`
)
WHERE s.`min_price` IS NULL;

UPDATE `tb_performance` p
SET p.`session_count` = (
  SELECT COUNT(*) FROM `tb_show` s WHERE s.`performance_id` = p.`id`
),
p.`min_price` = (
  SELECT MIN(s.`min_price`) FROM `tb_show` s WHERE s.`performance_id` = p.`id` AND s.`min_price` IS NOT NULL
);

UPDATE `tb_electronic_ticket` t
SET
  t.`short_code` = LPAD(RIGHT(t.`id`, 6), 6, '0'),
  t.`display_code` = CONCAT(
    SUBSTRING(LPAD(t.`id`, 12, '0'), 1, 4), ' ',
    SUBSTRING(LPAD(t.`id`, 12, '0'), 5, 4), ' ',
    SUBSTRING(LPAD(t.`id`, 12, '0'), 9, 4)
  )
WHERE t.`short_code` IS NULL AND t.`id` IS NOT NULL;

-- =============================================================================
-- §4 种子：会员等级 + 全局购票须知（15 条）
-- =============================================================================

INSERT INTO `tb_member_level` (`level`, `name`, `min_total_credits`, `discount_rate`, `priority_minutes`, `status`) VALUES
(0, '普通', 0, 1.00, 0, 1),
(1, '黑胶卡', 300, 0.98, 15, 1),
(2, '白银卡', 1500, 0.95, 30, 1),
(3, '鎏金卡', 4000, 0.92, 45, 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

INSERT INTO `tb_notice` (`performance_id`, `category`, `sort_no`, `content`, `status`) VALUES
(NULL, '购票', 1, '每张票对应一名观演人，下单后不可更改观演人信息', 1),
(NULL, '购票', 2, '限购规则以票档说明为准，超限将无法提交订单', 1),
(NULL, '购票', 3, '支付成功后电子票将发放至票夹，入场出示动态码即可', 1),
(NULL, '购票', 4, '未支付订单将在 15 分钟后自动关闭并释放库存', 1),
(NULL, '退改', 5, '开演前 7 天（含）可退票，全额退款，不收手续费', 1),
(NULL, '退改', 6, '距开演不足 7 天不可退票', 1),
(NULL, '退改', 7, '演出延期可保留票或按官方公告办理退改', 1),
(NULL, '退改', 8, '演出取消将全额自动退款至原支付渠道', 1),
(NULL, '入场', 9, '请提前 30 分钟到场，凭电子票动态码核销入场', 1),
(NULL, '入场', 10, '本场多为站席观演，请勿携带专业摄影摄像设备', 1),
(NULL, '入场', 11, '禁止携带易燃易爆及违禁物品入场', 1),
(NULL, '入场', 12, '未成年人须在监护人陪同下观演', 1),
(NULL, '实名', 13, '实名制观演需登记真实姓名与有效证件', 1),
(NULL, '实名', 14, '入场人信息与登记观演人一致方可入场', 1),
(NULL, '实名', 15, '证件信息仅用于购票与入场核验，平台依法保护隐私', 1);

-- 票档展示字段示例（存量测试数据）
UPDATE `tb_ticket_type` SET `description` = '含纪念手环 · 每人限购 2 张', `per_order_limit` = 2, `sort_no` = 1 WHERE `id` = 1;
UPDATE `tb_ticket_type` SET `description` = 'VIP 专属通道', `per_order_limit` = 1, `sort_no` = 2 WHERE `id` = 2;