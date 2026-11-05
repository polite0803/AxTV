/*
 * 来源: src/app/com/github/mytv/p004dv/widget/WidgetShape.java:8-18
 *       + WidgetImages.java:168-267 (shapePath 的极坐标半径公式)
 * 原职责: 图片裁剪/纯色块的形状枚举
 * 重构说明: 常量与顺序原样保留；各形状的极坐标半径公式落地到 WidgetShapeGeometry。
 */
package axtv.widget;

/** 图片裁剪形状。 */
public enum WidgetShape {
    ROUNDED,
    COOKIE4,
    COOKIE9,
    CLOVER4
}
