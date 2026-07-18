package com.gongyoutong.app.database;

import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * Room 数据库迁移 v4 → v5
 */
public final class MigrationV4To5 {
    private MigrationV4To5() {}

    public static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // v4→v5: 添加诊断记录表
        }
    };
}
