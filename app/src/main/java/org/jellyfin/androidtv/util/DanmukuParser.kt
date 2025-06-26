@file:JvmName("DanmakuXmlParser")

package org.jellyfin.androidtv.util

import android.graphics.Color.BLACK
import android.graphics.Typeface
import androidx.annotation.Nullable
import androidx.core.graphics.toColorInt
import com.bytedance.danmaku.render.engine.data.DanmakuData
import com.bytedance.danmaku.render.engine.render.draw.text.TextData
import com.bytedance.danmaku.render.engine.utils.LAYER_TYPE_BOTTOM_CENTER
import com.bytedance.danmaku.render.engine.utils.LAYER_TYPE_SCROLL
import com.bytedance.danmaku.render.engine.utils.LAYER_TYPE_TOP_CENTER
import org.jellyfin.androidtv.preference.UserPreferences
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import timber.log.Timber
import java.io.StringReader


/**
 * 解析XML弹幕内容
 * @param content XML字符串内容
 * @return 弹幕数据列表，解析失败返回null
 */
fun parse(userPreferences: UserPreferences, content: String?): List<DanmakuData>? {
	if (content?.isBlank() != false) return null

	return try {
		val factory = XmlPullParserFactory.newInstance()
		val parser = factory.newPullParser()
		parser.setInput(StringReader(content))

		val danmakuList = mutableListOf<DanmakuData>()
		var eventType = parser.eventType

		while (eventType != XmlPullParser.END_DOCUMENT) {
			when (eventType) {
				XmlPullParser.START_TAG -> {
					if (parser.name == "d") {
						val danmaku = parseDanmakuItem(userPreferences, parser)
						danmaku?.let { danmakuList.add(it) }
					}
				}
			}
			eventType = parser.next()
		}

		danmakuList
	} catch (e: Exception) {
		Timber.e(e, "Failed to parse danmaku XML")
		null
	}
}

/**
 * 解析单个弹幕项
 * XML格式示例: <d p="时间,模式,字号,颜色,时间戳,弹幕池,用户ID,弹幕ID">弹幕内容</d>
 */
private fun parseDanmakuItem(userPreferences: UserPreferences, parser: XmlPullParser): TextData? {
	val pAttribute = parser.getAttributeValue(null, "p") ?: return null
	val text = parser.nextText()?.trim() ?: return null

	if (text.isEmpty()) return null

	// 解析p属性，格式：时间,模式,字号,颜色,时间戳,弹幕池,用户ID,弹幕ID
	val params = pAttribute.split(",")
	if (params.size < 8) return null

	return try {
		val time = params[0].toFloat() * 1000 // 转换为毫秒
		val layerType = parseDanmakuMode(params[1].toInt())
		val textSize = params[2].toFloat() * userPreferences[UserPreferences.danmakuFontScale]
		val textColor = parseColor(params[3])

		// 创建TextData对象
		val textData = TextData().apply {
			this.text = text
			this.textSize = textSize
			this.textColor = textColor
			this.includeFontPadding = true
			this.typeface = Typeface.DEFAULT
			// 设置描边效果，提高可读性
			this.textStrokeColor = BLACK
			this.textStrokeWidth = 2.0f
			this.showAtTime = time.toLong()
			this.layerType = layerType
		}

		// 设置弹幕基础属性

		textData
	} catch (e: Exception) {
		e.printStackTrace()
		null
	}
}

/**
 * 解析弹幕模式并转换为drawType
 * 1-3: 滚动弹幕, 4: 底端弹幕, 5: 顶端弹幕,
 *   6: 逆向弹幕, 7: 定位弹幕, 8: 高级弹幕
 */
private fun parseDanmakuMode(mode: Int): Int {
	return when (mode) {
		1, 2, 3 -> LAYER_TYPE_SCROLL // 滚动弹幕
		4 -> LAYER_TYPE_BOTTOM_CENTER      // 底端弹幕
		5 -> LAYER_TYPE_TOP_CENTER       // 顶端弹幕
		else -> LAYER_TYPE_SCROLL    // 默认滚动弹幕
	}
}

/**
 * 解析颜色值
 * 支持十进制和十六进制格式
 */
private fun parseColor(colorStr: String): Int {
	return try {
		when {
			colorStr.startsWith("#") -> {
				// 十六进制格式 #RRGGBB
				colorStr.toColorInt()
			}

			colorStr.startsWith("0x") || colorStr.startsWith("0X") -> {
				// 十六进制格式 0xRRGGBB
				colorStr.substring(2).toInt(16) or 0xFF000000.toInt()
			}

			else -> {
				// 十进制格式
				val color = colorStr.toInt()
				if (color and 0xFF000000.toInt() == 0) {
					// 如果没有alpha通道，添加不透明度
					color or 0xFF000000.toInt()
				} else {
					color
				}
			}
		}
	} catch (e: Exception) {
		0xFF000000.toInt() // 默认黑色
	}
}

