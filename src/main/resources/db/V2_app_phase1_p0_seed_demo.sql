/*
 * 第 1 期联调种子数据（幂等，可重复执行）
 * 前置：livehouse_ticket.sql + V2_app_phase1_p0.sql
 *
 * 联调 ID（附录 I.3）：
 *   userId=1001（白银卡 9.5 折）phone=13800006621
 *   performanceId=101  showId=2001  ticketTypeId=501
 *   audienceIds=9001,9002
 */
SET NAMES utf8mb4;
USE `livehouse_ticket`;

-- 场馆补足 5 个
INSERT INTO `tb_venue` (`id`, `name`, `address`, `city`, `longitude`, `latitude`, `capacity`)
SELECT 4, 'MAO Livehouse 重庆南路店', '上海市黄浦区重庆南路308号', '上海', 121.4737, 31.2304, 500
WHERE NOT EXISTS (SELECT 1 FROM `tb_venue` WHERE `id` = 4);

INSERT INTO `tb_venue` (`id`, `name`, `address`, `city`, `longitude`, `latitude`, `capacity`)
SELECT 5, '号线音乐空间 成都店', '成都市锦江区东大街99号', '成都', 104.0665, 30.5723, 650
WHERE NOT EXISTS (SELECT 1 FROM `tb_venue` WHERE `id` = 5);

-- 主联调演出项目 101（上海）
INSERT INTO `tb_performance` (`id`, `title`, `artist_id`, `artist_name`, `type`, `city`, `poster`, `poster_tag`,
                              `description`, `is_real_name`, `min_price`, `session_count`, `want_count`, `status`)
SELECT 101, '霓虹之下 · 城市摇滚夜', 301, '霓虹之下乐队', '摇滚', '上海', '/images/demo/neon.jpg', '摇滚 · 现场',
       '来自上海的后朋与独立摇滚现场。', 1, 280.00, 3, 7700, 2
WHERE NOT EXISTS (SELECT 1 FROM `tb_performance` WHERE `id` = 101);

-- 追加演出项目 102-110
INSERT INTO `tb_performance` (`id`, `title`, `artist_id`, `artist_name`, `type`, `city`, `poster`, `is_real_name`, `min_price`, `session_count`, `want_count`, `status`)
SELECT n.id, CONCAT('联调演出项目-', n.id), NULL, CONCAT('艺人-', n.id), '独立', n.city, '/images/demo/show.jpg', 1, 180.00 + n.id, 3, 100 + n.id, 2
FROM (
  SELECT 102 AS id, '上海' AS city UNION SELECT 103,'北京' UNION SELECT 104,'深圳'
  UNION SELECT 105,'成都' UNION SELECT 106,'上海' UNION SELECT 107,'北京'
  UNION SELECT 108,'深圳' UNION SELECT 109,'成都' UNION SELECT 110,'上海'
) n
WHERE NOT EXISTS (SELECT 1 FROM `tb_performance` p WHERE p.id = n.id);

-- 场次 2001-2027（每场 3 票档）；2001-2003 归属 performance 101
INSERT INTO `tb_show` (`id`, `performance_id`, `venue_id`, `artist`, `title`, `type`, `start_time`, `end_time`, `session_label`,
                       `is_real_name`, `min_price`, `sell_count`, `gate_config`, `status`, `description`, `image`)
SELECT 2001, 101, 4, '霓虹之下乐队', '霓虹之下 · 城市摇滚夜', '摇滚',
       DATE_ADD(NOW(), INTERVAL 30 DAY), DATE_ADD(NOW(), INTERVAL 30 DAY) + INTERVAL 150 MINUTE, '周六 20:00',
       1, 280.00, 72, 'A,B', 2, '上海站', '/images/demo/neon.jpg'
WHERE NOT EXISTS (SELECT 1 FROM `tb_show` WHERE `id` = 2001);

INSERT INTO `tb_show` (`id`, `performance_id`, `venue_id`, `artist`, `title`, `type`, `start_time`, `end_time`, `session_label`, `status`, `min_price`, `gate_config`)
SELECT 2002, 101, 4, '霓虹之下乐队', '霓虹之下 · 城市摇滚夜', '摇滚',
       DATE_ADD(NOW(), INTERVAL 31 DAY), DATE_ADD(NOW(), INTERVAL 31 DAY) + INTERVAL 150 MINUTE, '周日 19:30', 2, 280.00, 'A'
WHERE NOT EXISTS (SELECT 1 FROM `tb_show` WHERE `id` = 2002);

INSERT INTO `tb_show` (`id`, `performance_id`, `venue_id`, `artist`, `title`, `type`, `start_time`, `end_time`, `session_label`, `status`, `min_price`, `gate_config`)
SELECT 2003, 101, 4, '霓虹之下乐队', '霓虹之下 · 城市摇滚夜', '摇滚',
       DATE_ADD(NOW(), INTERVAL 45 DAY), DATE_ADD(NOW(), INTERVAL 45 DAY) + INTERVAL 150 MINUTE, '加场', 2, 320.00, 'B'
WHERE NOT EXISTS (SELECT 1 FROM `tb_show` WHERE `id` = 2003);

-- 其余场次：102-110 各 3 场（2004-2026）
INSERT INTO `tb_show` (`id`, `performance_id`, `venue_id`, `artist`, `title`, `type`, `start_time`, `end_time`, `status`, `min_price`)
SELECT seq.show_id, seq.performance_id,
       CASE MOD(seq.performance_id, 5) WHEN 0 THEN 5 WHEN 1 THEN 1 WHEN 2 THEN 2 WHEN 3 THEN 3 ELSE 4 END,
       CONCAT('艺人-', seq.performance_id), CONCAT('联调演出-', seq.performance_id), '独立',
       DATE_ADD(NOW(), INTERVAL (10 + seq.show_id) DAY),
       DATE_ADD(NOW(), INTERVAL (10 + seq.show_id) DAY) + INTERVAL 120 MINUTE,
       2, 180.00 + seq.performance_id
FROM (
  SELECT 2004 AS show_id, 102 AS performance_id UNION ALL SELECT 2005,102 UNION ALL SELECT 2006,102
  UNION ALL SELECT 2007,103 UNION ALL SELECT 2008,103 UNION ALL SELECT 2009,103
  UNION ALL SELECT 2010,104 UNION ALL SELECT 2011,104 UNION ALL SELECT 2012,104
  UNION ALL SELECT 2013,105 UNION ALL SELECT 2014,105 UNION ALL SELECT 2015,105
  UNION ALL SELECT 2016,106 UNION ALL SELECT 2017,106 UNION ALL SELECT 2018,106
  UNION ALL SELECT 2019,107 UNION ALL SELECT 2020,107 UNION ALL SELECT 2021,107
  UNION ALL SELECT 2022,108 UNION ALL SELECT 2023,108 UNION ALL SELECT 2024,108
  UNION ALL SELECT 2025,109 UNION ALL SELECT 2026,109 UNION ALL SELECT 2027,110
) seq
WHERE NOT EXISTS (SELECT 1 FROM `tb_show` s WHERE s.id = seq.show_id);

-- 票档：2001 三场各 3 档（501-509）；其余场次按 show_id*10+档位
INSERT INTO `tb_ticket_type` (`id`, `show_id`, `name`, `price`, `total_stock`, `left_stock`, `sale_start_time`, `sale_end_time`,
                              `limit_per_user`, `description`, `per_order_limit`, `sold_count`, `sort_no`, `status`)
SELECT 501, 2001, '预售早鸟票', 280.00, 200, 128, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 29 DAY),
       2, '含纪念手环', 2, 72, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM `tb_ticket_type` WHERE `id` = 501);

INSERT INTO `tb_ticket_type` (`id`, `show_id`, `name`, `price`, `total_stock`, `left_stock`, `sale_start_time`, `sale_end_time`,
                              `limit_per_user`, `description`, `per_order_limit`, `sort_no`, `status`)
SELECT 502, 2001, '全价票', 380.00, 300, 210, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 29 DAY),
       4, '站席观演', 4, 2, 1
WHERE NOT EXISTS (SELECT 1 FROM `tb_ticket_type` WHERE `id` = 502);

INSERT INTO `tb_ticket_type` (`id`, `show_id`, `name`, `price`, `total_stock`, `left_stock`, `sale_start_time`, `sale_end_time`,
                              `limit_per_user`, `description`, `per_order_limit`, `sort_no`, `status`)
SELECT 503, 2001, 'VIP票', 680.00, 50, 35, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 29 DAY),
       1, 'VIP 专属通道', 1, 2, 1
WHERE NOT EXISTS (SELECT 1 FROM `tb_ticket_type` WHERE `id` = 503);

-- 为无票档场次批量补 3 档（幂等：按 show 判断）
INSERT INTO `tb_ticket_type` (`show_id`, `name`, `price`, `total_stock`, `left_stock`, `sale_start_time`, `sale_end_time`,
                              `limit_per_user`, `description`, `per_order_limit`, `sort_no`, `status`)
SELECT s.id, '早鸟票', 200.00, 100, 80, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(s.start_time, INTERVAL -1 HOUR),
       2, '联调早鸟', 2, 1, 1
FROM `tb_show` s
WHERE s.id >= 2002
  AND NOT EXISTS (SELECT 1 FROM `tb_ticket_type` t WHERE t.show_id = s.id AND t.name = '早鸟票');

INSERT INTO `tb_ticket_type` (`show_id`, `name`, `price`, `total_stock`, `left_stock`, `sale_start_time`, `sale_end_time`,
                              `limit_per_user`, `description`, `per_order_limit`, `sort_no`, `status`)
SELECT s.id, '全价票', 280.00, 120, 100, DATE_SUB(NOW(), INTERVAL 1 DAY), s.start_time,
       4, '联调全价', 4, 2, 1
FROM `tb_show` s
WHERE s.id >= 2002
  AND NOT EXISTS (SELECT 1 FROM `tb_ticket_type` t WHERE t.show_id = s.id AND t.name = '全价票');

INSERT INTO `tb_ticket_type` (`show_id`, `name`, `price`, `total_stock`, `left_stock`, `sale_start_time`, `sale_end_time`,
                              `limit_per_user`, `description`, `per_order_limit`, `sort_no`, `status`)
SELECT s.id, 'VIP票', 480.00, 40, 30, DATE_SUB(NOW(), INTERVAL 1 DAY), s.start_time,
       1, '联调 VIP', 1, 3, 1
FROM `tb_show` s
WHERE s.id >= 2002
  AND NOT EXISTS (SELECT 1 FROM `tb_ticket_type` t WHERE t.show_id = s.id AND t.name = 'VIP票');

UPDATE `tb_show` s SET `min_price` = (
  SELECT MIN(t.price) FROM `tb_ticket_type` t WHERE t.show_id = s.id
) WHERE s.id >= 2001;

UPDATE `tb_performance` p SET
  `session_count` = (SELECT COUNT(*) FROM `tb_show` s WHERE s.performance_id = p.id),
  `min_price` = (SELECT MIN(s.min_price) FROM `tb_show` s WHERE s.performance_id = p.id AND s.min_price IS NOT NULL)
WHERE p.id BETWEEN 101 AND 110;

-- 白银卡联调用户
INSERT INTO `tb_user` (`id`, `phone`, `password`, `nick_name`, `icon`, `status`, `wx_openid`)
SELECT 1001, '13800006621', '', 'LH_zhijiao', '', 1, 'dev_wx_openid_1001'
WHERE NOT EXISTS (SELECT 1 FROM `tb_user` WHERE `id` = 1001);

INSERT INTO `tb_user_info` (`user_id`, `city`, `introduce`, `fans`, `followee`, `gender`, `credits`, `level`)
SELECT 1001, '上海', '摇滚是城市的背景音', 0, 0, 1, 1600, 2
WHERE NOT EXISTS (SELECT 1 FROM `tb_user_info` WHERE `user_id` = 1001);

UPDATE `tb_user_info` SET `level` = 2, `credits` = 1600 WHERE `user_id` = 1001;

-- 观演人（证件号为演示数据；真实加密在接口创建时写入）
INSERT INTO `tb_audience` (`id`, `user_id`, `name`, `id_type`, `id_card_enc`, `id_card_mask`, `id_card_hash`, `phone`, `is_default`, `auth_status`, `deleted`)
SELECT 9001, 1001, '李知遥', 1, 'demo_enc_9001', '3101********1234',
       SHA2('310101199806011234', 256), '13800006621', 1, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `tb_audience` WHERE `id` = 9001);

INSERT INTO `tb_audience` (`id`, `user_id`, `name`, `id_type`, `id_card_enc`, `id_card_mask`, `id_card_hash`, `phone`, `is_default`, `auth_status`, `deleted`)
SELECT 9002, 1001, '王小明', 1, 'demo_enc_9002', '3101********5678',
       SHA2('310101199001015678', 256), '13800006622', 0, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `tb_audience` WHERE `id` = 9002);