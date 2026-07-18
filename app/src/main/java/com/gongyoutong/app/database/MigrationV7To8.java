package com.gongyoutong.app.database;

import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

public class MigrationV7To8 {
    public static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // 添加工单金额字段
            database.execSQL("ALTER TABLE work_orders ADD COLUMN amount REAL DEFAULT 0");
        }
    };
}
