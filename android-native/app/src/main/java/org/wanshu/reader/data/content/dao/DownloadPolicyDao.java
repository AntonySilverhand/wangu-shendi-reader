package org.wanshu.reader.data.content.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import org.wanshu.reader.data.content.entity.DownloadPolicyEntity;

@Dao
public interface DownloadPolicyDao {

    @Query("SELECT * FROM download_policies WHERE book_id = :bookId LIMIT 1")
    DownloadPolicyEntity getPolicy(String bookId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void savePolicy(DownloadPolicyEntity policy);

    @Query("DELETE FROM download_policies WHERE book_id = :bookId")
    void deletePolicy(String bookId);
}
