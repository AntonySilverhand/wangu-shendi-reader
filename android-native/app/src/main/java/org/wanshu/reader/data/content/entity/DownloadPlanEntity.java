package org.wanshu.reader.data.content.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "download_plans")
public class DownloadPlanEntity {
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "plan_id")
    public String planId = "";

    @NonNull
    @ColumnInfo(name = "book_id")
    public String bookId = "";

    @NonNull
    @ColumnInfo(name = "scope")
    public String scope = "ENTIRE_BOOK";

    @NonNull
    @ColumnInfo(name = "initial_anchor_chapter_id")
    public String initialAnchorChapterId = "";

    @NonNull
    @ColumnInfo(name = "current_phase")
    public String currentPhase = "PHASE_AFTER_ANCHOR_NEAR";

    @NonNull
    @ColumnInfo(name = "scan_checkpoint_chapter_id")
    public String scanCheckpointChapterId = "";

    @ColumnInfo(name = "policy_version")
    public long policyVersion = 1L;

    @ColumnInfo(name = "created_at")
    public long createdAt = 0L;

    @ColumnInfo(name = "updated_at")
    public long updatedAt = 0L;
}
