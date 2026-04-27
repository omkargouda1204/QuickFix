package com.example.quickfix;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

/**
 * Centralized HTTP client provider for optimized connection pooling and consistent timeouts
 */
public class HttpClientProvider {
    
    private static volatile OkHttpClient instance;
    private static final Object lock = new Object();
    
    // Optimized timeout settings for the app
    private static final int CONNECT_TIMEOUT_SECONDS = 12;
    private static final int READ_TIMEOUT_SECONDS = 18;
    private static final int WRITE_TIMEOUT_SECONDS = 15;
    
    public static OkHttpClient getInstance() {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new OkHttpClient.Builder()
                            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                            .retryOnConnectionFailure(true)
                            .build();
                }
            }
        }
        return instance;
    }
    
    /**
     * Get a client with custom timeouts for specific use cases
     */
    public static OkHttpClient getCustomClient(int connectTimeout, int readTimeout, int writeTimeout) {
        return getInstance().newBuilder()
                .connectTimeout(connectTimeout, TimeUnit.SECONDS)
                .readTimeout(readTimeout, TimeUnit.SECONDS)
                .writeTimeout(writeTimeout, TimeUnit.SECONDS)
                .build();
    }
    
    /**
     * Get a client optimized for quick operations like ping/reachability checks
     */
    public static OkHttpClient getFastClient() {
        return getCustomClient(8, 8, 8);
    }
    
    /**
     * Get a client optimized for large file uploads/downloads
     */
    public static OkHttpClient getBulkClient() {
        return getCustomClient(20, 30, 25);
    }
}
