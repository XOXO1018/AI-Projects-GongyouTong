package com.gongyoutong.app.database;

import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

public class MigrationV8To9 {
    public static final Migration MIGRATION_8_9 = new Migration(8, 9) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // 创建报价单表
            database.execSQL("CREATE TABLE IF NOT EXISTS quotations ("
                    + "id TEXT NOT NULL, "
                    + "workOrderId TEXT, "
                    + "customerId TEXT, "
                    + "totalAmount REAL DEFAULT 0, "
                    + "itemsJson TEXT, "
                    + "status TEXT, "
                    + "createdAt INTEGER DEFAULT 0, "
                    + "confirmedAt INTEGER DEFAULT 0, "
                    + "customerSignature TEXT, "
                    + "PRIMARY KEY(id))");
        }
    };
}
