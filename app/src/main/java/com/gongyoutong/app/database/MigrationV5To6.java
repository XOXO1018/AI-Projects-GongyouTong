package com.gongyoutong.app.database;

import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * Room 数据库迁移 v5 → v6
 */
public final class MigrationV5To6 {
    private MigrationV5To6() {}

    public static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // v5→v6: 添加维修记录表
        }
    };
}
