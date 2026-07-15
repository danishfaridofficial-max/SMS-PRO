package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class SchoolManagerRepository(
    private val context: Context,
    private val studentDao: StudentDao
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("sms_pro_prefs", Context.MODE_PRIVATE)
    private val activeSavingLocalIds = java.util.Collections.synchronizedSet(mutableSetOf<Long>())

    init {
        // Automatic migration of old WebApp URLs to the new active one
        val currentUrl = prefs.getString("web_app_url", "") ?: ""
        if (currentUrl.isEmpty() || currentUrl == "https://script.google.com/macros/s/AKfycbxxiBFBcc3uVqNyoED-13XNigqVV2HlPxgqujEHwLZfE5HTNBV8chuYbku6yTWr2e8y8Q/exec") {
            prefs.edit().putString("web_app_url", "https://script.google.com/macros/s/AKfycbzFAuQd4GgsftSAs4g9eVGl4LJ7C2ZlMLPfnVFEcm9dRus7EptNh_ec71losQr6QNt_6Q/exec").apply()
        }
    }

    // Retrofit client
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://script.google.com/") // Placeholder, we use @Url dynamic paths
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val api: NetworkService = retrofit.create(NetworkService::class.java)

    // Configuration preferences
    var isCloudMode: Boolean
        get() = prefs.getBoolean("is_cloud_mode", false)
        set(value) = prefs.edit().putBoolean("is_cloud_mode", value).apply()

    var themePreference: String
        get() = prefs.getString("theme_preference", "system") ?: "system"
        set(value) = prefs.edit().putString("theme_preference", value).apply()

    var webAppUrl: String
        get() = prefs.getString("web_app_url", "https://script.google.com/macros/s/AKfycbzFAuQd4GgsftSAs4g9eVGl4LJ7C2ZlMLPfnVFEcm9dRus7EptNh_ec71losQr6QNt_6Q/exec") ?: "https://script.google.com/macros/s/AKfycbzFAuQd4GgsftSAs4g9eVGl4LJ7C2ZlMLPfnVFEcm9dRus7EptNh_ec71losQr6QNt_6Q/exec"
        set(value) = prefs.edit().putString("web_app_url", value).apply()

    var sheetId: String
        get() = prefs.getString("sheet_id", "1FjN6mi26dAXfJZixfNICuhlpVoEdPSbyHRURZo5Ztzs") ?: "1FjN6mi26dAXfJZixfNICuhlpVoEdPSbyHRURZo5Ztzs"
        set(value) = prefs.edit().putString("sheet_id", value).apply()

    var schoolName: String
        get() = prefs.getString("school_name", "SMS PRO Local Academy") ?: "SMS PRO Local Academy"
        set(value) = prefs.edit().putString("school_name", value).apply()

    var username: String
        get() = prefs.getString("username", "Admin") ?: "Admin"
        set(value) = prefs.edit().putString("username", value).apply()

    var isLoggedIn: Boolean
        get() = prefs.getBoolean("is_logged_in", false)
        set(value) = prefs.edit().putBoolean("is_logged_in", value).apply()

    var activeClassPreference: String
        get() = prefs.getString("active_class_preference", "Class 1") ?: "Class 1"
        set(value) = prefs.edit().putString("active_class_preference", value).apply()

    var studentSortOrderPreference: Int
        get() = prefs.getInt("student_sort_order_preference", 0)
        set(value) = prefs.edit().putInt("student_sort_order_preference", value).apply()

    var isDashboardLoadedPreference: Boolean
        get() = prefs.getBoolean("is_dashboard_loaded_preference", false)
        set(value) = prefs.edit().putBoolean("is_dashboard_loaded_preference", value).apply()

    var isNoticeEnabled: Boolean
        get() = prefs.getBoolean("is_notice_enabled", true)
        set(value) = prefs.edit().putBoolean("is_notice_enabled", value).apply()

    var noticeTitle: String
        get() = prefs.getString("notice_title", "📢 SMS PRO Official Announcement") ?: "📢 SMS PRO Official Announcement"
        set(value) = prefs.edit().putString("notice_title", value).apply()

    var noticeMessage: String
        get() = prefs.getString("notice_message", "Mubarak Ho! SMS PRO application is fully synced and live. Join our official WhatsApp Channel to get instant updates regarding new features and direct support guides!") ?: "Mubarak Ho! SMS PRO application is fully synced and live. Join our official WhatsApp Channel to get instant updates regarding new features and direct support guides!"
        set(value) = prefs.edit().putString("notice_message", value).apply()

    var noticeActionUrl: String
        get() = prefs.getString("notice_action_url", "https://chat.whatsapp.com/invite") ?: "https://chat.whatsapp.com/invite"
        set(value) = prefs.edit().putString("notice_action_url", value).apply()

    suspend fun fetchNoticeSettings(): Result<NoticeResponse> {
        if (webAppUrl.isNotEmpty()) {
            try {
                val res = api.getNotice(url = webAppUrl, sheetId = sheetId)
                if (res.success) {
                    val enabledObj = res.isNoticeEnabled
                    val enabled = when (enabledObj) {
                        is Boolean -> enabledObj
                        is Number -> enabledObj.toInt() == 1
                        is String -> enabledObj.trim().lowercase() == "true" || enabledObj.trim() == "1"
                        else -> true
                    }
                    isNoticeEnabled = enabled
                    res.title?.let { noticeTitle = it }
                    res.message?.let { noticeMessage = it }
                    res.actionUrl?.let { noticeActionUrl = it }
                    return Result.success(res)
                } else {
                    return Result.failure(Exception(res.msg ?: "Notice service failed"))
                }
            } catch (e: Exception) {
                Log.e("SchoolRepo", "Failed fetching dynamic dynamic notice config", e)
                return Result.failure(e)
            }
        }
        return Result.failure(Exception("WebApp Url is missing"))
    }

    // Retrieve active student list flow
    fun getStudentsByClass(className: String): Flow<List<Student>> {
        return studentDao.getStudentsByClass(className)
    }

    fun getAllStudentsFlow(): Flow<List<Student>> {
        return studentDao.getAllStudents()
    }

    fun getUnsyncedCountFlow(): Flow<Int> {
        return studentDao.getUnsyncedCountFlow()
    }

    // Seed mock local data if database is empty - Disabled for production clean state
    suspend fun seedMockDataIfNeeded() = withContext(Dispatchers.IO) {
        // Do nothing in production to ensure clean slate
    }

    // Execute cloud and local login
    suspend fun performLogin(user: String, pass: String): Result<LoginResponse> {
        return if (isCloudMode) {
            try {
                if (webAppUrl.isEmpty()) {
                    return Result.failure(Exception("Apps Script Web App URL is not set in settings!"))
                }
                val res = api.checkLogin(url = webAppUrl, username = user, passcode = pass)
                if (res.success) {
                    sheetId = res.sheetId ?: sheetId
                    schoolName = res.schoolName ?: schoolName
                    username = res.username ?: user
                    isLoggedIn = true
                    Result.success(res)
                } else {
                    Result.failure(Exception(res.msg ?: "Invalid login details"))
                }
            } catch (e: Exception) {
                Log.e("SchoolRepo", "Cloud Login Failed", e)
                Result.failure(Exception("Connection Error: ${e.message}"))
            }
        } else {
            // Local login: default is admin / admin
            if (user.trim().uppercase() == "ADMIN" && pass == "admin") {
                schoolName = "SMS PRO Local Academy"
                username = "Admin"
                isLoggedIn = true
                Result.success(LoginResponse(true, sheetId, schoolName, username))
            } else {
                Result.failure(Exception("Offline mode credentials are admin / admin."))
            }
        }
    }

    // Class enrollment counts (for Bar Chart metrics)
    suspend fun loadClassCounts(): Map<String, Int> {
        val classes = listOf("Nursury", "Prep", "Class 1", "Class 2", "Class 3", "Class 4", "Class 5")
        if (isCloudMode && webAppUrl.isNotEmpty()) {
            try {
                val res = api.getClassCounts(url = webAppUrl, sheetId = sheetId)
                val labels = res.labels
                val counts = res.counts
                if (labels != null && counts != null && labels.size == counts.size) {
                    val map = mutableMapOf<String, Int>()
                    for (i in labels.indices) {
                        map[labels[i]] = counts[i]
                    }
                    return map
                }
            } catch (e: Exception) {
                Log.e("SchoolRepo", "Failed fetching sheet class counts", e)
            }
        }

        // Offline / cached fallback
        val map = mutableMapOf<String, Int>()
        classes.forEach { cls ->
            val list = studentDao.getStudentsByClassList(cls)
            map[cls] = list.size
        }
        return map
    }

    // Sync student list for a class
    suspend fun syncStudentData(className: String): Result<List<Student>> {
        if (isCloudMode && webAppUrl.isNotEmpty()) {
            try {
                val list = api.getStudentData(url = webAppUrl, sheetId = sheetId, className = className)
                val domainStudents = list.map { it.toDomainModel(className) }
                // Merge/Sync local cache: wipe items in this class and rewrite freshly synced records
                // Wait: to avoid removing localId fields, we can insert students fresh
                val existingLocal = studentDao.getStudentsByClassList(className)
                // Let's clear previous class elements and insert fresh, keeping offline unsynced drafts!
                val remoteAdmNos = domainStudents.map { it.admNo }.toSet()
                existingLocal.forEach {
                    if (it.rowId != null || remoteAdmNos.contains(it.admNo)) {
                        studentDao.deleteStudentByLocalId(it.localId)
                    }
                }
                studentDao.insertStudents(domainStudents)
                val freshlyInserted = studentDao.getStudentsByClassList(className)
                return Result.success(freshlyInserted)
            } catch (e: Exception) {
                Log.e("SchoolRepo", "Failed to sync class data (No internet). Loading from local cache.", e)
                val localCached = studentDao.getStudentsByClassList(className)
                return Result.success(localCached)
            }
        } else {
            return Result.success(studentDao.getStudentsByClassList(className))
        }
    }

    // Helper to re-sequence local student IDs consecutively (1, 2, 3...) sorted by rowId or localId
    private suspend fun resequenceLocalClassStudents(className: String) = withContext(Dispatchers.IO) {
        val list = studentDao.getStudentsByClassList(className)
        val sorted = list.sortedWith(compareBy<Student> { it.rowId ?: Int.MAX_VALUE }.thenBy { it.localId })
        sorted.forEachIndexed { index, st ->
            val correctStdId = index + 1
            if (st.stdId != correctStdId) {
                studentDao.updateStudent(st.copy(stdId = correctStdId))
            }
        }
    }

    // Create / Update record
    suspend fun saveStudent(student: Student): Result<String> {
        // Save to local database (Room) FIRST!
        val studentToSave = if (student.localId == 0L) {
            // Determine mock stdId for local parity
            val currentInClass = studentDao.getStudentsByClassList(student.className)
            val nextStdId = (currentInClass.maxOfOrNull { it.stdId ?: 0 } ?: 0) + 1
            student.copy(stdId = nextStdId, rowId = null)
        } else {
            student
        }

        val savedLocalId = if (studentToSave.localId == 0L) {
            studentDao.insertStudent(studentToSave)
        } else {
            studentDao.updateStudent(studentToSave)
            studentToSave.localId
        }

        resequenceLocalClassStudents(studentToSave.className)

        activeSavingLocalIds.add(savedLocalId)
        try {
            if (isCloudMode && webAppUrl.isNotEmpty()) {
                try {
                    val studentWithLocalId = studentToSave.copy(localId = savedLocalId)
                    // If it doesn't have stdId (registration), let Server Apps Script generate it
                    val networkRes = api.processStudent(
                        url = webAppUrl,
                        targetSheetId = sheetId,
                        sheetId = sheetId,
                        className = studentWithLocalId.className,
                        rowId = studentWithLocalId.rowId,
                        stdId = studentWithLocalId.stdId,
                        admNo = studentWithLocalId.admNo,
                        admDate = studentWithLocalId.admDate,
                        stdName = studentWithLocalId.stdName,
                        stdFname = studentWithLocalId.stdFname,
                        dob = studentWithLocalId.dob,
                        gender = studentWithLocalId.gender
                    )
                    if (networkRes.success) {
                        // Delete the temporary local draft to prevent duplication as syncStudentData loads it with its synced rowId
                        if (student.localId == 0L) {
                            studentDao.deleteStudentByLocalId(savedLocalId)
                        }
                        // Update cache by pulling fresh synced entries for this class
                        syncStudentData(studentWithLocalId.className)
                        return Result.success("Mubarak ho! Record Google Sheets me back-sync ho gaya hai.")
                    } else {
                        return Result.failure(Exception(networkRes.msg))
                    }
                } catch (e: Exception) {
                    Log.e("SchoolRepo", "Cloud Save Failed in background, keeping local copy.", e)
                    return Result.success("Offline draft locally saved successfully! Internet restored hone par automatic cloud sync ho jayega.")
                }
            } else {
                // Offline Local Database Insert / Update success return
                return Result.success(if (student.localId == 0L) "Student Registered Successfully in Local Database" else "Student Record Updated Successfully in Local Database")
            }
        } finally {
            activeSavingLocalIds.remove(savedLocalId)
        }
    }

    // Sync all offline-created or modified student records to Google Sheets
    suspend fun syncOfflineRecords(currentClass: String, onProgress: ((Int, Int) -> Unit)? = null): Result<Int> {
        if (!isCloudMode || webAppUrl.isEmpty()) {
            return Result.failure(Exception("Cloud Sync requires Cloud Mode enabled and configured."))
        }
        try {
            var syncCount = 0
            
            // 1. Process any pending deletions first
            val pendingDeletes = studentDao.getPendingDeletions()
            for (st in pendingDeletes) {
                try {
                    val rowId = st.rowId
                    if (rowId != null) {
                        val networkRes = api.deleteStudent(
                            url = webAppUrl,
                            sheetId = sheetId,
                            className = st.className,
                            rowId = rowId
                        )
                        if (networkRes.success) {
                            studentDao.deleteStudentByLocalId(st.localId)
                            syncCount++
                        }
                    } else {
                        studentDao.deleteStudentByLocalId(st.localId)
                        syncCount++
                    }
                } catch (itemEx: Exception) {
                    Log.e("SchoolRepo", "Failed to sync pending deletion offline for ${st.stdName}", itemEx)
                }
                kotlinx.coroutines.delay(100)
            }

            // 2. Process pending additions/updates
            val allLocal = studentDao.getAllStudents().first()
            val unsynced = allLocal.filter { it.rowId == null && !activeSavingLocalIds.contains(it.localId) }
            if (unsynced.isEmpty()) {
                // Trigger fresh class sync to keep client absolutely updated if anything was deleted
                if (syncCount > 0) {
                    try {
                        syncStudentData(currentClass)
                    } catch (ignore: Exception) {}
                }
                return Result.success(syncCount)
            }
            
            val totalCount = unsynced.size
            for ((index, st) in unsynced.withIndex()) {
                // Report progress before sync task begins
                onProgress?.invoke(index + 1, totalCount)
                try {
                    val networkRes = api.processStudent(
                        url = webAppUrl,
                        targetSheetId = sheetId,
                        sheetId = sheetId,
                        className = st.className,
                        rowId = null,
                        stdId = st.stdId,
                        admNo = st.admNo,
                        admDate = st.admDate,
                        stdName = st.stdName,
                        stdFname = st.stdFname,
                        dob = st.dob,
                        gender = st.gender
                    )
                    if (networkRes.success) {
                        // Remove transient offline draft, as cloud sheet fresh sync will sync it properly and set permanent IDs
                        studentDao.deleteStudentByLocalId(st.localId)
                        syncCount++
                    } else {
                        Log.e("SchoolRepo", "Failed to upload specific record: ${st.stdName} (Reason: ${networkRes.msg})")
                    }
                } catch (itemEx: Exception) {
                    Log.e("SchoolRepo", "Transient network failure on student record: ${st.stdName}", itemEx)
                    // Continue sequential execution so remaining students are safely loaded
                }
                // Rate-throttling courtesy delay
                kotlinx.coroutines.delay(150)
            }
            // Trigger fresh class sync to keep client absolutely updated
            try {
                syncStudentData(currentClass)
            } catch (ignore: Exception) {
                Log.e("SchoolRepo", "Ignored roster fetch exception during finalize sync", ignore)
            }
            return Result.success(syncCount)
        } catch (e: Exception) {
            Log.e("SchoolRepo", "Failed to sync offline record queue", e)
            return Result.failure(Exception("Sync queue failed due to connectivity: ${e.message}"))
        }
    }

    // Delete record permanently
    suspend fun deleteStudent(student: Student): Result<String> {
        if (isCloudMode && webAppUrl.isNotEmpty()) {
            val rowId = student.rowId
            if (rowId == null) {
                // If student has no rowId, they are an unsynced local draft. Delete locally and succeed.
                studentDao.deleteStudentByLocalId(student.localId)
                resequenceLocalClassStudents(student.className)
                return Result.success("Offline student draft deleted locally.")
            }
            try {
                val networkRes = api.deleteStudent(
                    url = webAppUrl,
                    sheetId = sheetId,
                    className = student.className,
                    rowId = rowId
                )
                if (networkRes.success) {
                    // Remove from cache
                    studentDao.deleteStudentByLocalId(student.localId)
                    // Trigger sync to fetch accurate updated rowIds (since deleting rows alters subsequent row IDs)
                    syncStudentData(student.className)
                    return Result.success(networkRes.msg)
                } else {
                    return Result.failure(Exception(networkRes.msg))
                }
            } catch (e: Exception) {
                Log.e("SchoolRepo", "Cloud Delete Failed", e)
                // Differentiate offline network exceptions to display nice user messaging
                val isNetworkError = e is java.net.UnknownHostException || 
                                     e is java.net.ConnectException || 
                                     e.message?.contains("Unable to resolve host") == true ||
                                     e.message?.contains("No address associated") == true
                
                if (isNetworkError) {
                     // Queue for offline deletion! Mark as pending delete and save back in ROOM.
                     // This triggers list Flow emission and is pending delete = 1 filters it out instantly.
                     val studentPendingDelete = student.copy(isPendingDelete = true)
                     studentDao.insertStudent(studentPendingDelete)
                     return Result.success("Offline delete queued. Student removed successfully and will be synced to Google Sheets when you are online.")
                } else {
                     return Result.failure(Exception("Cloud Delete Error: ${e.message}"))
                }
            }
        } else {
            // Offline Local Database Delete
            studentDao.deleteStudentByLocalId(student.localId)
            resequenceLocalClassStudents(student.className)
            return Result.success("Record Deleted Permanently from Local Database")
        }
    }

    fun logout() {
        isLoggedIn = false
        isDashboardLoadedPreference = false
        // Keep credentials but mark login flag as false
    }
}
