package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "students")
data class Student(
    @PrimaryKey(autoGenerate = true)
    val localId: Long = 0L,
    val rowId: Int? = null,      // Row index in Google Sheets (if synced)
    val stdId: Int? = null,      // Unique Student ID
    val admNo: String,           // Admission Number
    val admDate: String,         // Format "30-May-2026" or "YYYY-MM-DD"
    val stdName: String,         // Student Name
    val stdFname: String,        // Father Name
    val dob: String,             // Date of Birth
    val gender: String,          // "Male" or "Female"
    val className: String,       // "Nursury", "Prep", "Class 1", "Class 2", etc.
    val isPendingDelete: Boolean = false, // Track local delete queue when offline
    val cnic: String? = null,
    val fCnic: String? = null,
    val enrolmentType: String? = "Fresh"
) : Serializable
