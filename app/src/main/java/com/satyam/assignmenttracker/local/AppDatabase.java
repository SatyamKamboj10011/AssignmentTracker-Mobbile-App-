package com.satyam.assignmenttracker.local;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(
        entities = {
                DraftSubmissionEntity.class,
                AssignmentProgressEntity.class
        },
        version = 2,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    public abstract DraftSubmissionDao draftSubmissionDao();

    // ⬇️ NEW DAO
    public abstract AssignmentProgressDao assignmentProgressDao();

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "assignment_tracker_db"
                            )
                            .fallbackToDestructiveMigration() // ok for a student project
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
