package com.example.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LoginResponse(
    val success: Boolean,
    val sheetId: String? = null,
    val schoolName: String? = null,
    val username: String? = null,
    val msg: String? = null
)

@JsonClass(generateAdapter = true)
data class ClassCountsResponse(
    val labels: List<String>? = null,
    val counts: List<Int>? = null,
    val success: Boolean? = null,
    val msg: String? = null
)

@JsonClass(generateAdapter = true)
data class ProcessResponse(
    val success: Boolean,
    val msg: String
)

@JsonClass(generateAdapter = true)
data class NoticeResponse(
    val success: Boolean,
    val isNoticeEnabled: Any? = null, // Can be Boolean, Number/Int or String in the Sheets
    val title: String? = null,
    val message: String? = null,
    val actionUrl: String? = null,
    val msg: String? = null
)

@JsonClass(generateAdapter = true)
data class NetworkStudent(
    val rowId: Int? = null,
    val stdId: Any? = null, // Can be Int or String in Google Sheets
    val admNo: Any? = null, // Can be Int or String
    val admDate: String? = null,
    val stdName: String? = null,
    val stdFname: String? = null,
    val dob: String? = null,
    val gender: String? = null
) {
    fun toDomainModel(className: String): Student {
        val parsedStdId = when (val id = stdId) {
            is Number -> id.toInt()
            is String -> id.toIntOrNull()
            else -> null
        }
        val parsedAdmNo = when (val no = admNo) {
            is Number -> no.toLong().toString()
            is String -> no
            else -> ""
        }
        return Student(
            rowId = rowId,
            stdId = parsedStdId,
            admNo = parsedAdmNo,
            admDate = admDate ?: "",
            stdName = stdName ?: "",
            stdFname = stdFname ?: "",
            dob = dob ?: "",
            gender = gender ?: "Male",
            className = className
        )
    }
}
