package com.gongyoutong.app.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.gongyoutong.app.Config;

/**
 * 应用数据库
 */
@Database(entities = {
        ScheduleEntity.class,
        KnowledgeEntity.class,
        KnowledgeVectorEntity.class,
        WorkOrderEntity.class,
        CustomerEntity.class,
        RepairRecordEntity.class,
        DiagnosisRecordEntity.class
}, version = Config.DATABASE_VERSION, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    
    private static volatile AppDatabase INSTANCE;
    
    public abstract ScheduleDao scheduleDao();

    public abstract KnowledgeDao knowledgeDao();

    public abstract WorkOrderDao workOrderDao();

    public abstract CustomerDao customerDao();

    public abstract RepairRecordDao repairRecordDao();

    public abstract DiagnosisRecordDao diagnosisRecordDao();
    
    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            Config.DATABASE_NAME
                    )
                    .addMigrations(
                            MigrationV1To2.MIGRATION_1_2,
                            MigrationV2To3.MIGRATION_2_3,
                            MigrationV3To4.MIGRATION_3_4,
                            MigrationV4To5.MIGRATION_4_5,
                            MigrationV5To6.MIGRATION_5_6,
                            MigrationV6To7.MIGRATION_6_7
                    )
                    .build();
                }
            }
        }
        return INSTANCE;
    }
}
