package com.clothcall.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "caregiver_profiles")
data class CaregiverProfile(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val fadeThreshold: Int,  // 0–30 percent
    val isActive: Boolean = false
)
