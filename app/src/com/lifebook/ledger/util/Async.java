package com.lifebook.ledger.util;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 简单的后台线程 + 主线程回写工具，避免在主线程做数据库查询 */
public final class Async {
    private static final ExecutorService EXEC = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Async() {
    }

    public interface Callback<T> {
        void onResult(T value);
    }

    public static <T> void run(Callable<T> work, Callback<T> cb) {
        EXEC.execute(() -> {
            try {
                final T result = work.call();
                MAIN.post(() -> {
                    if (cb != null) cb.onResult(result);
                });
            } catch (Exception e) {
                MAIN.post(() -> {
                    if (cb != null) cb.onResult(null);
                });
            }
        });
    }
}
