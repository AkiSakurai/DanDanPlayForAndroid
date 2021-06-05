package com.xyoye.common_component.utils
import android.content.Context

class LocalizedEnumValue<T: Enum<T>>(val value:T, private val localizedValue: String) {
    override fun toString(): String {
        return localizedValue
    }
}

inline fun <reified T:Enum<T>> Enum.Companion
        .localizedValues(context: Context, resID: Int): List<LocalizedEnumValue<T>> {
    val array = context.resources.getStringArray(resID)

    return enumValues<T>()
        .mapIndexed { index, value -> LocalizedEnumValue(value, array[index]) }
}