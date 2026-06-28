package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentDao {
    @Query("SELECT * FROM students WHERE isPendingDelete = 0 ORDER BY admNo ASC")
    fun getAllStudents(): Flow<List<Student>>

    @Query("SELECT * FROM students WHERE className = :className AND isPendingDelete = 0 ORDER BY admNo ASC")
    fun getStudentsByClass(className: String): Flow<List<Student>>

    @Query("SELECT * FROM students WHERE className = :className AND isPendingDelete = 0 ORDER BY admNo ASC")
    suspend fun getStudentsByClassList(className: String): List<Student>

    @Query("SELECT * FROM students WHERE isPendingDelete = 1")
    suspend fun getPendingDeletions(): List<Student>

    @Query("SELECT (SELECT COUNT(*) FROM students WHERE rowId IS NULL) + (SELECT COUNT(*) FROM students WHERE isPendingDelete = 1)")
    fun getUnsyncedCountFlow(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudent(student: Student): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudents(students: List<Student>)

    @Update
    suspend fun updateStudent(student: Student)

    @Query("DELETE FROM students WHERE localId = :localId")
    suspend fun deleteStudentByLocalId(localId: Long)

    @Query("DELETE FROM students WHERE rowId = :rowId AND className = :className")
    suspend fun deleteStudentByRowId(rowId: Int, className: String)

    @Query("DELETE FROM students")
    suspend fun clearAll()
}
