package com.clothcall

import android.app.Application
import com.clothcall.data.db.AppDatabase

class ClothCallApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
}
