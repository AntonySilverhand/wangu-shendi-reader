package org.wanshu.reader.data.content;

import androidx.room.Database;
import androidx.room.RoomDatabase;
import org.wanshu.reader.data.content.dao.BookDao;
import org.wanshu.reader.data.content.dao.ChapterBlockDao;
import org.wanshu.reader.data.content.dao.ChapterDao;
import org.wanshu.reader.data.content.dao.DownloadPlanDao;
import org.wanshu.reader.data.content.dao.DownloadPolicyDao;
import org.wanshu.reader.data.content.dao.DownloadTaskDao;
import org.wanshu.reader.data.content.dao.SourceCooldownDao;
import org.wanshu.reader.data.content.dao.SourcePageDao;
import org.wanshu.reader.data.content.dao.TocDao;
import org.wanshu.reader.data.content.entity.BookEntity;
import org.wanshu.reader.data.content.entity.ChapterBlockEntity;
import org.wanshu.reader.data.content.entity.ChapterEntity;
import org.wanshu.reader.data.content.entity.DownloadPlanEntity;
import org.wanshu.reader.data.content.entity.DownloadPolicyEntity;
import org.wanshu.reader.data.content.entity.DownloadTaskEntity;
import org.wanshu.reader.data.content.entity.SourceCooldownEntity;
import org.wanshu.reader.data.content.entity.SourcePageEntity;
import org.wanshu.reader.data.content.entity.TocEntryEntity;
import org.wanshu.reader.data.content.entity.TocPageEntity;

@Database(
    entities = {
        BookEntity.class,
        TocPageEntity.class,
        TocEntryEntity.class,
        ChapterEntity.class,
        ChapterBlockEntity.class,
        SourcePageEntity.class,
        DownloadPolicyEntity.class,
        DownloadTaskEntity.class,
        DownloadPlanEntity.class,
        SourceCooldownEntity.class
    },
    version = 1,
    exportSchema = true
)
public abstract class ContentDatabase extends RoomDatabase {
    public abstract BookDao bookDao();
    public abstract TocDao tocDao();
    public abstract ChapterDao chapterDao();
    public abstract ChapterBlockDao chapterBlockDao();
    public abstract SourcePageDao sourcePageDao();
    public abstract DownloadPolicyDao downloadPolicyDao();
    public abstract DownloadTaskDao downloadTaskDao();
    public abstract DownloadPlanDao downloadPlanDao();
    public abstract SourceCooldownDao sourceCooldownDao();
}
