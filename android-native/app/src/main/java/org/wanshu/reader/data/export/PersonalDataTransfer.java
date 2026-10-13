package org.wanshu.reader.data.export;

import android.content.Context;
import android.net.Uri;
import org.wanshu.reader.data.AppExecutors;
import org.wanshu.reader.data.personal.PersonalDatabase;
import org.wanshu.reader.data.repository.DataCallback;

/** One operation per app; results survive recreation. UI listeners attach only while resumed. */
public final class PersonalDataTransfer implements Runnable {
    final Context context;
    final PersonalDatabase database;
    final AppExecutors executors;
    private boolean busy;
    private boolean importing;
    private String result;
    private Throwable error;
    private DataCallback<String> listener;

    public PersonalDataTransfer(Context context, PersonalDatabase database, AppExecutors executors) {
        this.context = context.getApplicationContext(); this.database = database; this.executors = executors;
    }
    public synchronized boolean isBusy() { return busy; }
    public synchronized boolean wasImport() { return importing; }
    public synchronized boolean start(Uri uri, boolean importing) {
        if (busy) return false;
        if (uri == null || !"content".equals(uri.getScheme())) throw new IllegalArgumentException("请选择文档提供器中的文件");
        busy = true; this.importing = importing; result = null; error = null;
        executors.getCpuExecutor().execute(new PersonalDataTransferTask(this, uri, importing));
        return true;
    }
    public synchronized void attach(DataCallback<String> listener) {
        this.listener = listener;
        executors.getMainThreadExecutor().execute(this);
    }
    public synchronized void detach(DataCallback<String> listener) {
        if (this.listener == listener) this.listener = null;
    }
    synchronized void finish(String result, Throwable error) {
        this.result = result; this.error = error; busy = false;
        executors.getMainThreadExecutor().execute(this);
    }
    @Override public synchronized void run() {
        if (busy || result == null || listener == null) return;
        String message = result; Throwable failure = error;
        result = null; error = null;
        if (failure == null) listener.onSuccess(message);
        else listener.onError(new java.io.IOException(message, failure));
    }
}
