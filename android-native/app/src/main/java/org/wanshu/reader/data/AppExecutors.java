package org.wanshu.reader.data;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppExecutors {
    private static AppExecutors instance;

    private final ExecutorService personalDbExecutor;
    private final ExecutorService contentDbExecutor;
    private final ExecutorService networkExecutor;
    private final ExecutorService cpuExecutor;
    private final Executor mainThreadExecutor;

    public AppExecutors(
            ExecutorService personalDbExecutor,
            ExecutorService contentDbExecutor,
            ExecutorService networkExecutor,
            ExecutorService cpuExecutor,
            Executor mainThreadExecutor
    ) {
        this.personalDbExecutor = personalDbExecutor;
        this.contentDbExecutor = contentDbExecutor;
        this.networkExecutor = networkExecutor;
        this.cpuExecutor = cpuExecutor;
        this.mainThreadExecutor = mainThreadExecutor;
    }

    public static synchronized AppExecutors getInstance() {
        if (instance == null) {
            instance = new AppExecutors(
                    Executors.newSingleThreadExecutor(new NamedThreadFactory("personal-db")),
                    Executors.newSingleThreadExecutor(new NamedThreadFactory("content-db")),
                    Executors.newFixedThreadPool(2, new NamedThreadFactory("network")),
                    Executors.newFixedThreadPool(2, new NamedThreadFactory("cpu-worker")),
                    new MainThreadExecutor()
            );
        }
        return instance;
    }

    public ExecutorService getPersonalDbExecutor() {
        return personalDbExecutor;
    }

    public ExecutorService getContentDbExecutor() {
        return contentDbExecutor;
    }

    public ExecutorService getNetworkExecutor() {
        return networkExecutor;
    }

    public ExecutorService getCpuExecutor() {
        return cpuExecutor;
    }

    public Executor getMainThreadExecutor() {
        return mainThreadExecutor;
    }
}
