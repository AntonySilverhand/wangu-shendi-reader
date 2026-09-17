package org.wanshu.reader.data.content.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;
import org.wanshu.reader.data.content.entity.DownloadTaskEntity;

@Dao
public interface DownloadTaskDao {

    @Query("SELECT * FROM download_tasks WHERE book_id = :bookId AND chapter_id = :chapterId LIMIT 1")
    DownloadTaskEntity getTask(String bookId, String chapterId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertTask(DownloadTaskEntity task);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertTasks(List<DownloadTaskEntity> tasks);

    @Query("UPDATE download_tasks SET state = :state, attempts = :attempts, next_attempt_at = :nextAttemptAt, error_type = :errorType, run_token = :runToken WHERE book_id = :bookId AND chapter_id = :chapterId")
    void updateTaskState(String bookId, String chapterId, String state, int attempts, long nextAttemptAt, String errorType, long runToken);

    @Query("SELECT * FROM download_tasks WHERE book_id = :bookId AND (state = 'PENDING' OR (state = 'RETRY_AT' AND next_attempt_at <= :currentTime)) ORDER BY priority DESC, next_attempt_at ASC LIMIT :limit")
    List<DownloadTaskEntity> getReadyTasks(String bookId, long currentTime, int limit);

    @Query("SELECT COUNT(*) FROM download_tasks WHERE book_id = :bookId AND state = 'PENDING'")
    int getPendingCount(String bookId);

    @Query("SELECT COUNT(*) FROM download_tasks WHERE book_id = :bookId AND (state = 'NEEDS_ACTION' OR state = 'RETRY_AT')")
    int getFailedCount(String bookId);

    @Query("UPDATE download_tasks SET state = 'PENDING', run_token = 0 WHERE state = 'RUNNING'")
    int resetOrphanedRunningTasks();

    @Query("UPDATE download_tasks SET state = 'PENDING', attempts = 0, next_attempt_at = 0, error_type = '', run_token = 0 WHERE book_id = :bookId AND (state = 'NEEDS_ACTION' OR state = 'RETRY_AT')")
    int resetFailedTasks(String bookId);

    @Query("UPDATE download_tasks SET priority = MAX(priority, :priority), demand_flags = demand_flags | :demandFlag WHERE book_id = :bookId AND chapter_id = :chapterId")
    void elevatePriority(String bookId, String chapterId, int priority, int demandFlag);

    @Query("UPDATE download_tasks SET demand_flags = demand_flags & ~:demandFlag WHERE book_id = :bookId")
    void removeDemandFlag(String bookId, int demandFlag);

    @Query("DELETE FROM download_tasks WHERE book_id = :bookId AND demand_flags = 0")
    void deleteOrphanedTasks(String bookId);

    @Query("SELECT * FROM download_tasks WHERE book_id = :bookId AND (demand_flags & :flag) != 0")
    List<DownloadTaskEntity> getTasksByDemand(String bookId, int flag);

    @Query("SELECT COUNT(*) FROM download_tasks WHERE book_id = :bookId AND state = 'COMPLETE'")
    int getCompletedCount(String bookId);

    @Query("DELETE FROM download_tasks WHERE book_id = :bookId")
    void clearTasksForBook(String bookId);
}

