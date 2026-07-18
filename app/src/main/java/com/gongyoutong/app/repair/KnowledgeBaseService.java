package com.gongyoutong.app.repair;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.gongyoutong.app.database.AppDatabase;
import com.gongyoutong.app.database.KnowledgeVectorEntity;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 知识库服务 —— 本地知识存储 + 检索
 *
 * 职责：
 * 1. 保存维修知识条目（向量化后可支持语义检索）
 * 2. 基于关键词匹配搜索知识库
 * 3. 按分类或最近时间检索知识条目
 *
 * 线程安全：所有数据库操作在单线程 Executor 中执行
 */
public class KnowledgeBaseService {
    private static final String TAG = "KnowledgeBaseService";

    /** 单例（DCL） */
    private static volatile KnowledgeBaseService sInstance;

    private final ExecutorService executor;
    private final Handler mainHandler;
    private Context appContext;

    /** 私有构造，通过 init() 或 getInstance(Context) 初始化 */
    private KnowledgeBaseService() {
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * 获取单例
     *
     * @return KnowledgeBaseService 实例
     */
    public static KnowledgeBaseService getInstance() {
        if (sInstance == null) {
            synchronized (KnowledgeBaseService.class) {
                if (sInstance == null) {
                    sInstance = new KnowledgeBaseService();
                }
            }
        }
        return sInstance;
    }

    /**
     * 初始化：设置 Application Context（首次使用前必须调用）
     *
     * @param context Application Context
     */
    public void init(Context context) {
        if (context != null) {
            this.appContext = context.getApplicationContext();
        }
    }

    // ==================== 回调接口 ====================

    /** 知识搜索回调 */
    public interface SearchCallback {
        void onResults(List<KnowledgeVectorEntity> results);
        void onError(String msg);
    }

    // ==================== 核心 API ====================

    /**
     * 在本地知识库中按关键词搜索
     *
     * @param query    搜索关键词
     * @param topK     返回结果数量上限
     * @param callback 结果回调（主线程）
     */
    public void search(String query, int topK, SearchCallback callback) {
        if (query == null || query.trim().isEmpty()) {
            mainHandler.post(() -> callback.onError("搜索关键词为空"));
            return;
        }
        if (topK <= 0) {
            topK = 5;
        }
        final int limit = topK;

        executor.execute(() -> {
            try {
                AppDatabase db = getDatabase();
                if (db == null) {
                    mainHandler.post(() -> callback.onError("数据库未初始化，请先调用 init()"));
                    return;
                }
                List<KnowledgeVectorEntity> results =
                        db.knowledgeDao().searchByContent("%" + query + "%");
                if (results.size() > limit) {
                    results = results.subList(0, limit);
                }
                final List<KnowledgeVectorEntity> finalResults = results;
                mainHandler.post(() -> callback.onResults(finalResults));
            } catch (Exception e) {
                Log.e(TAG, "知识库搜索失败: " + e.getMessage());
                mainHandler.post(() -> callback.onError("搜索失败: " + e.getMessage()));
            }
        });
    }

    /**
     * 按分类搜索知识条目
     *
     * @param category 知识分类
     * @param callback 结果回调（主线程）
     */
    public void searchByCategory(String category, SearchCallback callback) {
        if (category == null || category.trim().isEmpty()) {
            mainHandler.post(() -> callback.onError("分类名称为空"));
            return;
        }

        executor.execute(() -> {
            try {
                AppDatabase db = getDatabase();
                if (db == null) {
                    mainHandler.post(() -> callback.onError("数据库未初始化，请先调用 init()"));
                    return;
                }
                List<KnowledgeVectorEntity> results =
                        db.knowledgeDao().searchByCategory(category);
                mainHandler.post(() -> callback.onResults(results));
            } catch (Exception e) {
                Log.e(TAG, "按分类搜索失败: " + e.getMessage());
                mainHandler.post(() -> callback.onError("搜索失败: " + e.getMessage()));
            }
        });
    }

    /**
     * 保存知识条目
     *
     * @param title    知识标题
     * @param content  知识内容
     * @param category 分类
     */
    public void save(String title, String content, String category) {
        if (content == null || content.trim().isEmpty()) {
            Log.w(TAG, "保存失败: 内容为空");
            return;
        }

        executor.execute(() -> {
            try {
                AppDatabase db = getDatabase();
                if (db == null) {
                    Log.e(TAG, "保存失败: 数据库未初始化");
                    return;
                }
                KnowledgeVectorEntity entity = new KnowledgeVectorEntity(
                        UUID.randomUUID().toString(),
                        title, content, category, null
                );
                db.knowledgeDao().insert(entity);
                Log.d(TAG, "知识条目已保存: " + (title != null ? title : "(无标题)"));
            } catch (Exception e) {
                Log.e(TAG, "保存知识失败: " + e.getMessage());
            }
        });
    }

    /**
     * 保存知识条目（含向量表示）
     *
     * @param title         知识标题
     * @param content       知识内容
     * @param category      分类
     * @param embeddingJson 向量 JSON 字符串（可为 null）
     */
    public void saveWithEmbedding(String title, String content, String category,
                                   String embeddingJson) {
        if (content == null || content.trim().isEmpty()) {
            Log.w(TAG, "保存失败: 内容为空");
            return;
        }

        executor.execute(() -> {
            try {
                AppDatabase db = getDatabase();
                if (db == null) {
                    Log.e(TAG, "保存失败: 数据库未初始化");
                    return;
                }
                KnowledgeVectorEntity entity = new KnowledgeVectorEntity(
                        UUID.randomUUID().toString(),
                        title, content, category, embeddingJson
                );
                db.knowledgeDao().insert(entity);
                Log.d(TAG, "知识条目（含向量）已保存: " + (title != null ? title : "(无标题)"));
            } catch (Exception e) {
                Log.e(TAG, "保存知识失败: " + e.getMessage());
            }
        });
    }

    /**
     * 获取最近添加的知识条目
     *
     * @param limit    返回数量上限
     * @param callback 结果回调（主线程）
     */
    public void getRecent(int limit, SearchCallback callback) {
        if (limit <= 0) {
            limit = 10;
        }
        final int finalLimit = limit;

        executor.execute(() -> {
            try {
                AppDatabase db = getDatabase();
                if (db == null) {
                    mainHandler.post(() -> callback.onError("数据库未初始化，请先调用 init()"));
                    return;
                }
                List<KnowledgeVectorEntity> results =
                        db.knowledgeDao().getRecent(finalLimit);
                mainHandler.post(() -> callback.onResults(results));
            } catch (Exception e) {
                Log.e(TAG, "获取最近知识失败: " + e.getMessage());
                mainHandler.post(() -> callback.onError("查询失败: " + e.getMessage()));
            }
        });
    }

    // ==================== 内部工具方法 ====================

    /**
     * 获取 AppDatabase 实例
     *
     * @return AppDatabase 实例，未初始化时返回 null
     */
    private AppDatabase getDatabase() {
        if (appContext == null) {
            Log.e(TAG, "AppDatabase: appContext 未初始化");
            return null;
        }
        return AppDatabase.getInstance(appContext);
    }
}
