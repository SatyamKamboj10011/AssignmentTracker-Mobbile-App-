package com.satyam.assignmenttracker.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface AssignmentProgressDao {

    @Query("SELECT * FROM assignment_progress WHERE id = :id LIMIT 1")
    com.satyam.assignmenttracker.local.AssignmentProgressEntity getById(String id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(com.satyam.assignmenttracker.local.AssignmentProgressEntity entity);
}
