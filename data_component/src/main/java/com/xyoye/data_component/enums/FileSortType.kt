package com.xyoye.data_component.enums

enum class FileSortType(val value:Int) {
    NAME(1),
    DATE(2);

    companion object {
        fun valueOf(value: Int): FileSortType {
            return when (value) {
                1 -> FileSortType.NAME
                2 -> FileSortType.DATE
                else -> FileSortType.NAME
            }
        }
    }
}