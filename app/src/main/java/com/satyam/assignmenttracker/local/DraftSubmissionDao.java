package com.satyam.assignmenttracker.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface DraftSubmissionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(DraftSubmissionEntity draft);

    @Query("SELECT * FROM draft_submissions WHERE id = :id LIMIT 1")
    DraftSubmissionEntity getById(String id);

    @Query("DELETE FROM draft_submissions WHERE id = :id")
    void deleteById(String id);
}
