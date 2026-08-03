package com.example.hisab

import android.app.Application
import com.example.hisab.data.db.HisabDatabase

class HisabApplication : Application() {

    val database: HisabDatabase by lazy {
        HisabDatabase.getDatabase(this)
    }
}
