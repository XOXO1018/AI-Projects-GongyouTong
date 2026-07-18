package com.gongyoutong.app.database;

import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * Room 数据库迁移 v3 → v4
 */
public final class MigrationV3To4 {
    private MigrationV3To4() {}

    public static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // v3→v4: 添加客户表
        }
    };
}
