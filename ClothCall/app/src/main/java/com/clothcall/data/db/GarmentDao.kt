package com.clothcall.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface GarmentDao {

    @Query("SELECT * FROM garments ORDER BY dateAdded DESC")
    fun getAllGarments(): Flow<List<Garment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGarment(garment: Garment): Long

    @Delete
    suspend fun deleteGarment(garment: Garment)

    @Query("SELECT * FROM garments WHERE id = :id")
    suspend fun getGarmentById(id: Int): Garment?
}
