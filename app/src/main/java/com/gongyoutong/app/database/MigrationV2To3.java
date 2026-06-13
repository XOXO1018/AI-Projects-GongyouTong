package com.gongyoutong.app.database;

import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * Room 数据库迁移 v2 → v3
 */
public final class MigrationV2To3 {
    private MigrationV2To3() {}

    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // v2→v3: 添加工单表
        }
    };
}
