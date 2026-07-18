package com.gongyoutong.app.database;

import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * Room 数据库迁移 v10 → v11
 */
public final class MigrationV10To11 {
    private MigrationV10To11() {}

    public static final Migration MIGRATION_10_11 = new Migration(10, 11) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // v10→v11: 无 schema 变更
        }
    };
}
