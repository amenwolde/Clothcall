package com.clothcall.ui.viewmodels

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.clothcall.data.db.Garment
import com.clothcall.data.db.GarmentDao
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class WardrobeViewModel(private val garmentDao: GarmentDao) : ViewModel() {

    val garments: StateFlow<List<Garment>> = garmentDao.getAllGarments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addGarment(context: Context, name: String, bitmap: Bitmap) {
        viewModelScope.launch {
            val filename = "garment_${UUID.randomUUID()}.jpg"
            val file = File(context.filesDir, filename)
            FileOutputStream(file).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos)
            }
            garmentDao.insertGarment(Garment(name = name, imagePath = file.absolutePath))
        }
    }

    fun deleteGarment(garment: Garment) {
        viewModelScope.launch {
            File(garment.imagePath).delete()
            garmentDao.deleteGarment(garment)
        }
    }

    companion object {
        fun factory(dao: GarmentDao): ViewModelProvider.Factory =
            viewModelFactory { initializer { WardrobeViewModel(dao) } }
    }
}
