package com.livehouse.entity;

import lombok.Getter;


/**
 * @Auther: shaolei
 * @Date: 2026/11/10-10:49
 * @Description：业务状态码
 */
@Getter
public enum StatusCode {

    // 通用
    SUCCESS(200, "成功"),
    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "业务冲突"),
    TOO_MANY_REQUESTS(429, "操作过于频繁，请稍后再试"),
    SERVER_ERROR(500, "系统异常，请稍后再试"),

    // 登录
    PHONE_FORMAT_ERROR(1001, "手机号格式不正确"),
    SMS_CODE_ERROR(1002, "验证码错误或已过期"),
    ACCOUNT_BANNED(1003, "账号已被封禁"),
    PHONE_ALREADY_BOUND(1004, "该手机号已被其他账号绑定"),
    WX_LOGIN_FAILED(1005, "微信授权失败，请重新登录"),
    USER_NOT_FOUND(1006, "用户不存在"),

    // 演出
    SHOW_NOT_FOUND(2001, "演出不存在"),
    SHOW_OFF_SHELF(2002, "演出已下架"),
    SHOW_ENDED(2003, "演出已结束"),
    SESSION_NOT_FOUND(2004, "场次不存在"),
    TICKET_TYPE_NOT_FOUND(2005, "票档不存在"),
    SALE_NOT_STARTED(2006, "未到开售时间"),
    SALE_ENDED(2007, "已停止售票"),
    VENUE_NOT_FOUND(2008, "场馆不存在"),

    // 购票
    STOCK_NOT_ENOUGH(3001, "库存不足"),
    LIMIT_PER_USER_EXCEEDED(3002, "超出每人限购数量"),
    LIMIT_PER_ORDER_EXCEEDED(3003, "超出每单限购数量"),
    QUANTITY_ILLEGAL(3004, "购买数量不合法"),
    PENDING_ORDER_EXISTS(3005, "存在未支付订单，请先支付或取消"),
    AUDIENCE_COUNT_MISMATCH(3006, "观演人数量与购票数量不一致"),
    AUDIENCE_NOT_AUTHENTICATED(3007, "观演人未完成实名认证"),
    REAL_NAME_REQUIRED(3008, "该演出为实名制，请先完成实名认证"),
    COUPON_NOT_AVAILABLE(3009, "优惠券不可用或已过期"),
    REPEAT_SUBMIT(3010, "请勿重复提交，请求正在处理中"),
    PRIORITY_NOT_REACHED(3011, "会员可提前购票，当前未到你的开售时间"),

    // 订单
    ORDER_NOT_FOUND(4001, "订单不存在"),
    ORDER_NOT_OWNER(4002, "无权操作该订单"),
    ORDER_STATUS_ILLEGAL(4003, "当前订单状态不支持该操作"),
    ORDER_EXPIRED(4004, "订单已超时关闭"),
    ORDER_ALREADY_PAID(4005, "订单已支付，请勿重复支付"),
    CANCEL_NOT_ALLOWED(4006, "已支付订单不支持取消，请申请退票"),
    REFUND_NOT_ALLOWED(4007, "当前订单状态不支持退票"),
    ORDER_NOT_PAID(4008, "订单未支付"),
    REFUND_DUPLICATED(4009, "该订单已提交过退票申请"),
    REFUND_TICKET_USED(4010, "该订单下已有电子票核销入场，无法退票"),
    REFUND_SHOW_STARTED(4011, "演出已开始，无法退票"),
    REFUND_NOT_FOUND(4012, "该订单暂无退票记录"),
    REFUND_WINDOW_CLOSED(4013, "距开演不足 7 天，不支持退票"),

    // 支付
    PAY_FAILED(5001, "支付下单失败，请稍后重试"),
    PAY_SIGN_ERROR(5002, "支付签名校验失败"),
    PAY_AMOUNT_MISMATCH(5003, "支付金额与订单金额不一致"),
    PAY_NOTIFY_INVALID(5004, "支付回调报文非法"),
    PAY_MOCK_DISABLED(5005, "模拟支付未启用"),

    // 票种
    TICKET_NOT_FOUND(6001, "票不存在"),
    TICKET_NOT_OWNER(6002, "无权查看该票"),
    TICKET_NOT_VALID(6003, "票已失效"),
    TICKET_ALREADY_USED(6004, "该票已核销入场"),
    TICKET_REFUNDED(6005, "该票已退票"),
    TICKET_CODE_INVALID(6006, "入场码无效"),
    VERIFY_CROSS_SHOW(6007, "该票不属于当前场次"),
    VERIFY_NO_PERMISSION(6008, "非本场馆工作人员，无核销权限"),
    VERIFY_NOT_STARTED(6009, "未到入场时间"),
    VERIFY_ENDED(6010, "演出已结束，无法入场"),
    TICKET_CODE_EMPTY(6011, "电子票码不能为空"),
    TICKET_GENERATE_FAILED(6012, "生成电子票失败"),
    VERIFY_FAILED(6013, "核销失败，请稍后重试"),

    // 会员、积分、优惠券
    MEMBER_LEVEL_NOT_FOUND(7001, "会员等级配置不存在"),
    CREDITS_NOT_ENOUGH(7002, "积分不足"),
    GOODS_SOLD_OUT(7003, "该商品已兑完"),

    // 观演人、实名认证
    AUDIENCE_NOT_FOUND(8001, "观演人不存在"),
    AUDIENCE_LIMIT_EXCEEDED(8002, "常用观演人数量已达上限"),
    AUDIENCE_DUPLICATED(8003, "该证件号已添加过"),
    AUDIENCE_DELETE_FORBIDDEN(8004, "实名认证通过后 24 小时内不可删除"),
    AUDIENCE_IN_USE(8005, "该观演人已被订单引用，暂不可删除"),
    ID_CARD_INVALID(8006, "证件号格式不正确"),
    REAL_NAME_AUDITING(8007, "实名认证审核中"),
    REAL_NAME_REJECTED(8008, "实名认证未通过"),
    REALNAME_DUPLICATE(8009, "该证件号已被其他账号实名"),

    // 文件上传、运维
    FILE_EMPTY(9001, "请选择要上传的图片"),
    FILE_TYPE_NOT_ALLOWED(9002, "仅支持 jpg、jpeg、png、gif、webp 格式图片"),
    SYSTEM_BUSY(9003, "系统繁忙，请稍后再试"),
    STOCK_RESTORE_FAILED(9004, "库存恢复失败");

    private final Integer code;
    private final String msg;

    StatusCode(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}
