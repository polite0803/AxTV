/*
 * 来源: 新增（原 g13.m4377h 用 y42 表达 wire 层错误，且被 try/catch 吞掉）
 * 原职责: protobuf wire 解析失败的可识别异常
 * 重构说明: 用户要求"严格失败"：截断 / 未知 wire type / 长度越界一律抛出本异常，
 *           不再返回半截数据。非受检异常，便于 codec 递归内部直接传播。
 */
package axtv.api;

public class MobileFeedProtoException extends RuntimeException {

    public MobileFeedProtoException(String message) {
        super(message);
    }
}
