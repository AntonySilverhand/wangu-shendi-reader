package org.wanshu.reader;

import android.content.Context;
import androidx.room.Room;
import org.wanshu.reader.core.net.SourceHttpClient;
import org.wanshu.reader.core.queue.RequestScheduler;
import org.wanshu.reader.core.text.TextBlockBuilder;
import org.wanshu.reader.core.util.JavaBase64Decoder;
import org.wanshu.reader.data.AppExecutors;
import org.wanshu.reader.data.content.ContentDatabase;
import org.wanshu.reader.data.personal.PersonalDatabase;
import org.wanshu.reader.data.repository.ContentRepository;
import org.wanshu.reader.data.repository.PersonalRepository;
import org.wanshu.reader.data.repository.ReaderRepository;
import org.wanshu.reader.navigation.AppNavigator;
import org.wanshu.reader.navigation.BackController;

public class AppContainer {
    private final Context applicationContext;
    private final AppExecutors executors;
    private final PersonalDatabase personalDb;
    private final ContentDatabase contentDb;
    private final PersonalRepository personalRepository;
    private final ContentRepository contentRepository;
    private final RequestScheduler scheduler;
    private final SourceHttpClient httpClient;
    private final ReaderRepository readerRepository;
    private final TextBlockBuilder textBlockBuilder;
    private final AppNavigator navigator;
    private final BackController backController;

    public AppContainer(Context applicationContext) {
        this.applicationContext = applicationContext.getApplicationContext();
        this.executors = AppExecutors.getInstance();

        this.personalDb = Room.databaseBuilder(
                this.applicationContext,
                PersonalDatabase.class,
                "reader-personal.db"
        ).build();

        this.contentDb = Room.databaseBuilder(
                this.applicationContext,
                ContentDatabase.class,
                "reader-content.db"
        ).build();

        this.textBlockBuilder = new TextBlockBuilder(4000);
        this.personalRepository = new PersonalRepository(this.personalDb, this.executors.getPersonalDbExecutor());
        this.contentRepository = new ContentRepository(this.contentDb, this.executors.getContentDbExecutor(), this.textBlockBuilder);

        this.scheduler = new RequestScheduler();
        this.httpClient = new SourceHttpClient();
        this.readerRepository = new ReaderRepository(
                this.contentRepository,
                this.scheduler,
                this.httpClient,
                new JavaBase64Decoder()
        );

        this.navigator = new AppNavigator();
        this.backController = new BackController(this.navigator);
    }

    public Context getApplicationContext() {
        return applicationContext;
    }

    public AppExecutors getExecutors() {
        return executors;
    }

    public PersonalDatabase getPersonalDb() {
        return personalDb;
    }

    public ContentDatabase getContentDb() {
        return contentDb;
    }

    public PersonalRepository getPersonalRepository() {
        return personalRepository;
    }

    public ContentRepository getContentRepository() {
        return contentRepository;
    }

    public RequestScheduler getScheduler() {
        return scheduler;
    }

    public SourceHttpClient getHttpClient() {
        return httpClient;
    }

    public ReaderRepository getReaderRepository() {
        return readerRepository;
    }

    public TextBlockBuilder getTextBlockBuilder() {
        return textBlockBuilder;
    }

    public AppNavigator getNavigator() {
        return navigator;
    }

    public BackController getBackController() {
        return backController;
    }
}
