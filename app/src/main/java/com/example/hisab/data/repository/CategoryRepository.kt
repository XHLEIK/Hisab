package com.example.hisab.data.repository

import com.example.hisab.data.db.dao.CategoryDao
import com.example.hisab.data.db.entity.CategoryEntity
import com.example.hisab.data.model.TransactionType
import kotlinx.coroutines.flow.Flow

class CategoryRepository(private val categoryDao: CategoryDao) {

    fun getAllCategories(): Flow<List<CategoryEntity>> =
        categoryDao.getAll()

    fun getCategoriesByType(type: TransactionType): Flow<List<CategoryEntity>> =
        categoryDao.getAllByType(type.name)

    suspend fun getCategoryById(id: Long): CategoryEntity? =
        categoryDao.getById(id)

    suspend fun insertCategory(category: CategoryEntity): Long =
        categoryDao.insert(category)

    suspend fun updateCategory(category: CategoryEntity) =
        categoryDao.update(category)

    suspend fun deleteCategory(category: CategoryEntity) =
        categoryDao.delete(category)

    suspend fun getAllCategoriesSync(): List<CategoryEntity> =
        categoryDao.getAllSync()
}
