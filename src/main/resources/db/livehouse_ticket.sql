/*
LiveHouse票务系统数据库脚本
基于hm-dianping项目改造
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 创建数据库
CREATE DATABASE IF NOT EXISTS `livehouse_ticket` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `livehouse_ticket`;

-- ----------------------------
-- Table structure for tb_venue (场馆表)
-- ----------------------------
DROP TABLE IF EXISTS `tb_venue`;
CREATE TABLE `tb_venue` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '场馆ID',
  `name` varchar(128) NOT NULL COMMENT '场馆名称',
  `address` varchar(256) DEFAULT NULL COMMENT '场馆地址',
  `city` varchar(32) NOT NULL COMMENT '所在城市',
  `longitude` double NOT NULL COMMENT '经度',
  `latitude` double NOT NULL COMMENT '纬度',
  `capacity` int DEFAULT NULL COMMENT '总容量',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_city` (`city`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='演出场馆表';

-- ----------------------------
-- Table structure for tb_show (演出表)
-- ----------------------------
DROP TABLE IF EXISTS `tb_show`;
CREATE TABLE `tb_show` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '演出ID',
  `venue_id` bigint NOT NULL COMMENT '场馆ID',
  `artist` varchar(128) NOT NULL COMMENT '艺人/乐队',
  `title` varchar(256) NOT NULL COMMENT '演出标题',
  `type` varchar(32) DEFAULT NULL COMMENT '演出类型',
  `start_time` datetime NOT NULL COMMENT '演出开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '演出结束时间',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1待开票 2售票中 3售罄 4已结束',
  `description` text COMMENT '演出介绍',
  `image` varchar(512) DEFAULT NULL COMMENT '演出海报',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_venue` (`venue_id`),
  KEY `idx_status` (`status`),
  KEY `idx_start_time` (`start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='单场演出表';

-- ----------------------------
-- Table structure for tb_ticket_type (票种表)
-- ----------------------------
DROP TABLE IF EXISTS `tb_ticket_type`;
CREATE TABLE `tb_ticket_type` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '票种ID',
  `show_id` bigint NOT NULL COMMENT '演出ID',
  `name` varchar(64) NOT NULL COMMENT '票种名称：早鸟票/预售票/全价票/VIP票',
  `price` decimal(10,2) NOT NULL COMMENT '票价',
  `total_stock` int NOT NULL COMMENT '总库存',
  `left_stock` int NOT NULL COMMENT '剩余库存',
  `sale_start_time` datetime NOT NULL COMMENT '开售时间',
  `sale_end_time` datetime DEFAULT NULL COMMENT '结束售票时间',
  `limit_per_user` int NOT NULL DEFAULT '2' COMMENT '每人限购数量',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_show` (`show_id`),
  KEY `idx_sale_time` (`sale_start_time`, `sale_end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='演出票种表';

-- ----------------------------
-- Table structure for tb_ticket_order (订单表)
-- ----------------------------
DROP TABLE IF EXISTS `tb_ticket_order`;
CREATE TABLE `tb_ticket_order` (
  `id` bigint NOT NULL COMMENT '订单号',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `show_id` bigint NOT NULL COMMENT '演出ID',
  `ticket_type_id` bigint NOT NULL COMMENT '票种ID',
  `quantity` int NOT NULL DEFAULT '1' COMMENT '购票数量',
  `amount` decimal(10,2) NOT NULL COMMENT '订单金额',
  `pay_status` tinyint NOT NULL DEFAULT '0' COMMENT '支付状态：0未支付 1已支付 2已取消',
  `order_status` tinyint NOT NULL DEFAULT '1' COMMENT '订单状态：1待支付 2已完成 3已取消 4已退票',
  `pay_time` datetime DEFAULT NULL COMMENT '支付时间',
  `request_id` varchar(64) DEFAULT NULL COMMENT '幂等请求ID（来自秒杀MQ消息），防止消息重复投递/重试导致重复建单',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_request_id` (`request_id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_show` (`show_id`),
  KEY `idx_status` (`pay_status`, `order_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='票务订单表';

-- ----------------------------
-- Table structure for tb_electronic_ticket (电子票表)
-- ----------------------------
DROP TABLE IF EXISTS `tb_electronic_ticket`;
CREATE TABLE `tb_electronic_ticket` (
  `id` bigint NOT NULL COMMENT '电子票号',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `show_id` bigint NOT NULL COMMENT '演出ID',
  `ticket_type_id` bigint NOT NULL COMMENT '票种ID',
  `verify_code` varchar(32) NOT NULL COMMENT '核销码',
  `verify_status` tinyint NOT NULL DEFAULT '0' COMMENT '核销状态：0未核销 1已核销 2已作废（退票）',
  `verify_time` datetime DEFAULT NULL COMMENT '核销时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_verify_code` (`verify_code`),
  KEY `idx_order` (`order_id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_show` (`show_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='电子票表';

-- ----------------------------
-- Table structure for tb_check_in_record (核销记录表)
-- ----------------------------
DROP TABLE IF EXISTS `tb_check_in_record`;
CREATE TABLE `tb_check_in_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '核销记录ID',
  `ticket_id` bigint NOT NULL COMMENT '电子票号',
  `show_id` bigint NOT NULL COMMENT '演出ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `operator_id` bigint DEFAULT NULL COMMENT '操作员ID',
  `check_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '核销时间',
  PRIMARY KEY (`id`),
  KEY `idx_show` (`show_id`),
  KEY `idx_ticket` (`ticket_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='入场核销记录表';

-- ----------------------------
-- Table structure for tb_refund_record (退票记录表，本地消息表)
-- ----------------------------
DROP TABLE IF EXISTS `tb_refund_record`;
CREATE TABLE `tb_refund_record` (
  `id` bigint NOT NULL COMMENT '退票记录ID',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `show_id` bigint NOT NULL COMMENT '演出ID',
  `ticket_type_id` bigint NOT NULL COMMENT '票种ID',
  `quantity` int NOT NULL COMMENT '退票数量',
  `amount` decimal(10,2) NOT NULL COMMENT '退款金额',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '处理状态：0处理中 1已完成 2处理失败（待重试）',
  `reason` varchar(255) DEFAULT NULL COMMENT '退票原因',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `finish_time` datetime DEFAULT NULL COMMENT '完成时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order` (`order_id`) COMMENT '同一订单只允许存在一条退票记录',
  KEY `idx_user` (`user_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退票记录表（本地消息表，保证库存回补的最终一致性）';

-- ----------------------------
-- 保留原有用户相关表（复用黑马点评的用户系统）
-- ----------------------------
-- 用户表
DROP TABLE IF EXISTS `tb_user`;
CREATE TABLE `tb_user` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `phone` varchar(11) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '手机号码',
  `password` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '密码，加密存储',
  `nick_name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '昵称，默认是用户id',
  `icon` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '人像',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uniqe_key_phone` (`phone`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=COMPACT COMMENT='用户表';

-- 用户信息表
DROP TABLE IF EXISTS `tb_user_info`;
CREATE TABLE `tb_user_info` (
  `user_id` bigint unsigned NOT NULL COMMENT '主键，用户id',
  `city` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '城市名称',
  `introduce` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '个人介绍，不要超过128个字符',
  `fans` int unsigned DEFAULT '0' COMMENT '粉丝数量',
  `followee` int unsigned DEFAULT '0' COMMENT '关注的人的数量',
  `gender` tinyint unsigned DEFAULT '0' COMMENT '性别，0：男，1：女',
  `birthday` date DEFAULT NULL COMMENT '生日',
  `credits` int unsigned DEFAULT '0' COMMENT '积分',
  `level` tinyint unsigned DEFAULT '0' COMMENT '会员级别，0~9级,0代表未开通会员',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`user_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=COMPACT COMMENT='用户信息表';

-- ----------------------------
-- 插入测试数据
-- ----------------------------
-- 插入场馆数据
INSERT INTO `tb_venue` (`name`, `address`, `city`, `longitude`, `latitude`, `capacity`) VALUES 
('LiveHouse 上海站', '上海市静安区南京西路123号', '上海', 121.4737, 31.2304, 800),
('音乐现场 北京店', '北京市朝阳区三里屯路88号', '北京', 116.4074, 39.9042, 600),
('摇滚天堂 深圳馆', '深圳市南山区深南大道999号', '深圳', 114.0579, 22.5431, 1000);

-- 插入演出数据
INSERT INTO `tb_show` (`venue_id`, `artist`, `title`, `type`, `start_time`, `end_time`, `status`, `description`, `image`) VALUES 
(1, '逃跑计划', '逃跑计划2024全国巡回演唱会-上海站', '摇滚', '2024-12-20 20:00:00', '2024-12-20 22:30:00', 2, '逃跑计划2024年度巡回演唱会，热血摇滚，青春无悔！', '/images/show1.jpg'),
(2, '朴树', '朴树 平凡之路 演唱会', '民谣', '2024-12-25 19:30:00', '2024-12-25 22:00:00', 1, '朴树经典歌曲现场演绎，感受平凡中的不凡', '/images/show2.jpg'),
(3, '新裤子乐队', '新裤子乐队 生活因你而火热 巡演', '摇滚', '2024-12-30 20:00:00', '2024-12-30 23:00:00', 2, '新裤子乐队经典作品现场演奏', '/images/show3.jpg');

-- 插入票种数据
INSERT INTO `tb_ticket_type` (`show_id`, `name`, `price`, `total_stock`, `left_stock`, `sale_start_time`, `sale_end_time`, `limit_per_user`) VALUES 
(1, '早鸟票', 280.00, 200, 150, '2024-11-01 10:00:00', '2024-12-19 23:59:59', 2),
(1, 'VIP票', 680.00, 50, 25, '2024-11-01 10:00:00', '2024-12-19 23:59:59', 1),
(2, '预售票', 320.00, 150, 120, '2024-12-01 10:00:00', '2024-12-24 23:59:59', 2),
(3, '全价票', 380.00, 300, 280, '2024-11-15 10:00:00', '2024-12-29 23:59:59', 3);

SET FOREIGN_KEY_CHECKS = 1;