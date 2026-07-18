package com.gongyoutong.app.ui.knowledge;

import android.graphics.Color;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.gongyoutong.app.R;
import com.gongyoutong.app.database.AppDatabase;
import com.gongyoutong.app.database.KnowledgeEntity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 知识详情页 —— 含 AI 总结 + 思维导图 + 原始内容
 */
public class KnowledgeDetailActivity extends AppCompatActivity {

    public static final String EXTRA_KNOWLEDGE_ID = "knowledge_id";

    private TextView tvTitle, tvAiSummary, tvRawContent, tvSourceType, tvCreatorName, tvCreatedAt;
    private LinearLayout layoutMindMap;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_knowledge_detail);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        tvTitle = findViewById(R.id.tvTitle);
        tvAiSummary = findViewById(R.id.tvAiSummary);
        tvRawContent = findViewById(R.id.tvRawContent);
        tvSourceType = findViewById(R.id.tvSourceType);
        tvCreatorName = findViewById(R.id.tvCreatorName);
        tvCreatedAt = findViewById(R.id.tvCreatedAt);
        layoutMindMap = findViewById(R.id.layoutMindMap);

        String id = getIntent().getStringExtra(EXTRA_KNOWLEDGE_ID);
        if (id != null) loadData(id);
    }

    private void loadData(String id) {
        executor.execute(() -> {
            KnowledgeEntity entity = AppDatabase.getInstance(this).knowledgeDao().getById(id);
            if (entity == null) return;

            runOnUiThread(() -> {
                tvTitle.setText(entity.getTitle() != null && !entity.getTitle().isEmpty()
                        ? entity.getTitle() : "（无标题）");

                String summary = entity.getAiSummary();
                tvAiSummary.setText(summary != null && !summary.isEmpty() ? summary : "暂无摘要");

                tvRawContent.setText(entity.getRawContent() != null ? entity.getRawContent() : "");

                tvSourceType.setText(sourceTypeLabel(entity.getSourceType()));

                String creator = entity.getCreatorName();
                tvCreatorName.setText(creator != null && !creator.isEmpty() ? creator : "工友通师傅");

                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
                tvCreatedAt.setText(sdf.format(new Date(entity.getCreatedAt())));

                // 渲染思维导图
                renderMindMap(entity.getMindMapJson());
            });
        });
    }

    /**
     * 将思维导图 JSON 渲染为嵌套缩进视图
     */
    private void renderMindMap(String mindMapJson) {
        layoutMindMap.removeAllViews();
        if (mindMapJson == null || mindMapJson.isEmpty()) {
            layoutMindMap.setVisibility(View.GONE);
            return;
        }

        try {
            JSONObject root = new JSONObject(mindMapJson);
            String rootText = root.optString("root", "思维导图");
            JSONArray children = root.optJSONArray("children");

            // 根节点
            layoutMindMap.setVisibility(View.VISIBLE);
            addMindMapNode(layoutMindMap, rootText, 0, true);

            if (children != null) {
                for (int i = 0; i < children.length(); i++) {
                    JSONObject child = children.getJSONObject(i);
                    String text = child.optString("text", "");
                    JSONArray subChildren = child.optJSONArray("children");
                    addMindMapNode(layoutMindMap, text, 1, false);
                    if (subChildren != null) {
                        for (int j = 0; j < subChildren.length(); j++) {
                            JSONObject sub = subChildren.getJSONObject(j);
                            String subText = sub.optString("text", "");
                            addMindMapNode(layoutMindMap, subText, 2, false);
                        }
                    }
                }
            }
        } catch (Exception e) {
            TextView tv = new TextView(this);
            tv.setText("思维导图解析失败");
            tv.setTextColor(Color.GRAY);
            tv.setTextSize(12);
            layoutMindMap.addView(tv);
        }
    }

    private void addMindMapNode(LinearLayout parent, String text, int level, boolean isRoot) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int padLeft = 8 + level * 28;
        row.setPadding(padLeft, level == 0 ? 2 : 4, 12, 4);

        // 节点标记
        TextView dot = new TextView(this);
        dot.setText(isRoot ? "● " : (level == 1 ? "◆ " : "◦ "));
        dot.setTextColor(isRoot ? Color.parseColor("#F97316") :
                (level == 1 ? Color.parseColor("#EA580C") : Color.parseColor("#78716C")));
        dot.setTextSize(isRoot ? 15 : (level == 1 ? 13 : 12));
        row.addView(dot);

        // 节点文字
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(isRoot ? Color.parseColor("#1C1917") :
                (level == 1 ? Color.parseColor("#44403C") : Color.parseColor("#78716C")));
        tv.setTextSize(isRoot ? 15 : (level == 1 ? 13 : 12));
        tv.setPadding(0, 0, 12, 0);
        if (isRoot) tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
        row.addView(tv);

        parent.addView(row);
    }

    private String sourceTypeLabel(String type) {
        if (type == null) return "文字";
        switch (type) {
            case "voice": return "语音";
            case "photo": return "图片";
            case "document": return "文档";
            case "online": return "联网获取";
            default: return "文字";
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
        super.onDestroy();
    }
}
