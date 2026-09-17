package org.wanshu.reader.data.content.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import org.wanshu.reader.data.content.entity.SourceCooldownEntity;

@Dao
public interface SourceCooldownDao {

    @Query("SELECT * FROM source_cooldown WHERE source_id = :sourceId LIMIT 1")
    SourceCooldownEntity getCooldown(String sourceId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void setCooldown(SourceCooldownEntity cooldown);

    @Query("DELETE FROM source_cooldown WHERE source_id = :sourceId")
    void clearCooldown(String sourceId);
}
