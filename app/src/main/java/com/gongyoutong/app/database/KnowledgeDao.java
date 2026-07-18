package com.gongyoutong.app.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/**
 * 知识库 DAO
 */
@Dao
public interface KnowledgeDao {

    // ==================== KnowledgeEntity 操作 ====================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(KnowledgeEntity entity);

    @Update
    void update(KnowledgeEntity entity);

    @Query("SELECT * FROM knowledge ORDER BY createdAt DESC")
    List<KnowledgeEntity> getAll();

    @Query("SELECT * FROM knowledge WHERE id = :id LIMIT 1")
    KnowledgeEntity getById(String id);

    @Query("DELETE FROM knowledge WHERE id = :id")
    void deleteById(String id);

    @Query("SELECT COUNT(*) FROM knowledge")
    int getCount();

    // ==================== KnowledgeVectorEntity 操作 ====================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(KnowledgeVectorEntity entity);

    @Query("SELECT * FROM knowledge_vectors WHERE category = :category ORDER BY createdAt DESC")
    List<KnowledgeVectorEntity> searchByCategory(String category);

    @Query("SELECT * FROM knowledge_vectors WHERE content LIKE :query OR title LIKE :query ORDER BY createdAt DESC")
    List<KnowledgeVectorEntity> searchByContent(String query);

    @Query("SELECT * FROM knowledge_vectors ORDER BY createdAt DESC LIMIT :limit")
    List<KnowledgeVectorEntity> getRecent(int limit);
}
