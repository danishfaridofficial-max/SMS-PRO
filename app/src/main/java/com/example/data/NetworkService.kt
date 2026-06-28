package com.example.data

import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

interface NetworkService {
    @GET
    suspend fun getNotice(
        @Url url: String,
        @Query("action") action: String = "getNotice",
        @Query("sheetId") sheetId: String
    ): NoticeResponse

    @GET
    suspend fun checkLogin(
        @Url url: String,
        @Query("action") action: String = "checkLogin",
        @Query("username") username: String,
        @Query("password") passcode: String
    ): LoginResponse

    @GET
    suspend fun getClassCounts(
        @Url url: String,
        @Query("action") action: String = "getClassCounts",
        @Query("sheetId") sheetId: String
    ): ClassCountsResponse

    @GET
    suspend fun getStudentData(
        @Url url: String,
        @Query("action") action: String = "getStudentData",
        @Query("sheetId") sheetId: String,
        @Query("className") className: String
    ): List<NetworkStudent>

    @GET
    suspend fun processStudent(
        @Url url: String,
        @Query("action") action: String = "processStudent",
        @Query("targetSheetId") targetSheetId: String,
        @Query("className") className: String,
        @Query("rowId") rowId: Int?,
        @Query("stdId") stdId: Int?,
        @Query("admNo") admNo: String,
        @Query("admDate") admDate: String,
        @Query("stdName") stdName: String,
        @Query("stdFname") stdFname: String,
        @Query("dob") dob: String,
        @Query("gender") gender: String
    ): ProcessResponse

    @GET
    suspend fun deleteStudent(
        @Url url: String,
        @Query("action") action: String = "deleteStudent",
        @Query("sheetId") sheetId: String,
        @Query("className") className: String,
        @Query("rowId") rowId: Int
    ): ProcessResponse
}
