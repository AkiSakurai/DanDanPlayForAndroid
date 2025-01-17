package com.xyoye.common_component.database.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.xyoye.data_component.entity.DanmuBlockEntity

@Dao
interface DanmuBlockDao {

    @Query("SELECT * FROM danmu_block ORDER BY add_time DESC")
    fun getAll(): LiveData<MutableList<DanmuBlockEntity>>

    @Query("SELECT * FROM danmu_block WHERE is_cloud = (:isCloud) ORDER BY add_time DESC")
    fun getAll(isCloud: Boolean): LiveData<MutableList<DanmuBlockEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg entities: DanmuBlockEntity)

    @Query("DELETE FROM danmu_block WHERE id = (:id)")
    suspend fun delete(id: Int)

    @Query("DELETE FROM danmu_block WHERE is_cloud = (:isCloud)")
    suspend fun deleteByType(isCloud: Boolean)
}