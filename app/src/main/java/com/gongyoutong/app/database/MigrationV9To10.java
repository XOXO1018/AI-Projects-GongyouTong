package com.gongyoutong.app.database;

import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

public class MigrationV9To10 {
    public static final Migration MIGRATION_9_10 = new Migration(9, 10) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // 添加客户新字段
            database.execSQL("ALTER TABLE customers ADD COLUMN tag TEXT");
            database.execSQL("ALTER TABLE customers ADD COLUMN serviceCount INTEGER DEFAULT 0");
            database.execSQL("ALTER TABLE customers ADD COLUMN totalSpent REAL DEFAULT 0");
            database.execSQL("ALTER TABLE customers ADD COLUMN lastServiceTime INTEGER DEFAULT 0");
            database.execSQL("ALTER TABLE customers ADD COLUMN notes TEXT");
        }
    };
}
