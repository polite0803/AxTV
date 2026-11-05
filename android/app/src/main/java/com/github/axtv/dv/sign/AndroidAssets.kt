/*
 * Android 适配层 —— sign 模块：assets 读取。
 * 规格出处: ADAPTERS.md §1 / sign/NOTES.md §6-1
 * 行为: context.assets.open(name) 直通；readText 默认实现负责 UTF-8 拼接。
 */
package com.github.axtv.dv.sign

import android.content.Context
import axtv.sign.Assets

class AndroidAssets(context: Context) : Assets {
    private val assetManager = context.applicationContext.assets

    override fun open(name: String): java.io.InputStream = assetManager.open(name)
}
