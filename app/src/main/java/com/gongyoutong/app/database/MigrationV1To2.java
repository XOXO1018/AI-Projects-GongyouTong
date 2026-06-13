package com.gongyoutong.app.database;

import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * Room 数据库迁移 v1 → v2
 */
public final class MigrationV1To2 {
    private MigrationV1To2() {}

    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // v1→v2: 首次正式化，无 schema 变更
        }
    };
}
