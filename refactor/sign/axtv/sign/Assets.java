/*
 * 来源: src/app/p000/C1400n.java:93,135 与 src/app/p000/zg0.java:146 的
 *       Context.getAssets().open("abogus_dy.js" / "sign.js") 调用
 * 原职责: 从 APK 的 assets 目录读取 JS 脚本
 * 重构说明: 核心层不依赖 android.content.Context，抽象成接口；Android 侧实现见 NOTES.md
 */
package axtv.sign;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** assets 读取抽象。{@code open} 语义与 {@code AssetManager.open} 一致（找不到抛 IOException）。 */
public interface Assets {

    InputStream open(String name) throws IOException;

    /**
     * 读取全部文本（UTF-8）。
     *
     * <p>对应原实现 {@code new BufferedReader(new InputStreamReader(open(name), UTF_8), 8192)}
     * + {@code wi4.m11582u(bufferedReader)}（C1400n:96-99、C1400n:137-141、zg0:148-151）。
     */
    default String readText(String name) throws IOException {
        try (InputStream in = open(name)) {
            Objects.requireNonNull(in, "assets stream");
            ByteArrayOutputStream buffer = new ByteArrayOutputStream(Math.max(8192, in.available()));
            byte[] chunk = new byte[8192];
            int read;
            while ((read = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            // ByteArrayOutputStream.toString(Charset) 是 Java 10 / API 39 才有的重载，
            // desugaring 也不覆盖它：低版本真机会抛 NoSuchMethodError。改用 new String(byte[], Charset)。
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
