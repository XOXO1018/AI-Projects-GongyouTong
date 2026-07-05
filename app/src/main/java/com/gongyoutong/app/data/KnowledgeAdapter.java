package com.gongyoutong.app.data;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.gongyoutong.app.R;
import com.gongyoutong.app.database.KnowledgeEntity;
import com.gongyoutong.app.utils.DateUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 知识库列表 Adapter
 */
public class KnowledgeAdapter extends RecyclerView.Adapter<KnowledgeAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(KnowledgeEntity entity);
    }

    private final List<KnowledgeEntity> list = new ArrayList<>();
    private OnItemClickListener listener;

    public KnowledgeAdapter(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setList(List<KnowledgeEntity> newList) {
        list.clear();
        if (newList != null) list.addAll(newList);
        notifyDataSetChanged();
    }

    public void addItem(KnowledgeEntity entity) {
        list.add(0, entity);
        notifyItemInserted(0);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_knowledge, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        KnowledgeEntity entity = list.get(position);

        // 标题
        holder.tvTitle.setText(
                entity.getTitle() != null && !entity.getTitle().isEmpty()
                        ? entity.getTitle()
                        : "（无标题）"
        );

        // AI 摘要
        String summary = entity.getAiSummary();
        if (summary != null && !summary.isEmpty()) {
            holder.tvAiSummary.setText(summary);
        } else {
            // 摘要未生成时展示原文前80字
            String raw = entity.getRawContent();
            holder.tvAiSummary.setText(raw != null && raw.length() > 80
                    ? raw.substring(0, 80) + "..."
                    : raw);
        }

        // 来源标签
        holder.tvSourceType.setText(sourceTypeLabel(entity.getSourceType()));

        // 录入人
        String creator = entity.getCreatorName();
        holder.tvCreatorName.setText(creator != null && !creator.isEmpty() ? creator : "工友通师傅");

        // 时间
        holder.tvCreatedAt.setText(DateUtils.formatDisplayDateTime(new Date(entity.getCreatedAt())));

        // 点击
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(entity);
        });
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    private String sourceTypeLabel(String sourceType) {
        if (sourceType == null) return "文字";
        switch (sourceType) {
            case "voice":    return "语音";
            case "photo":    return "图片";
            case "document": return "文档";
            case "online":   return "联网获取";
            default:         return "文字";
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvAiSummary, tvSourceType, tvCreatorName, tvCreatedAt;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle       = itemView.findViewById(R.id.tvTitle);
            tvAiSummary   = itemView.findViewById(R.id.tvAiSummary);
            tvSourceType  = itemView.findViewById(R.id.tvSourceType);
            tvCreatorName = itemView.findViewById(R.id.tvCreatorName);
            tvCreatedAt   = itemView.findViewById(R.id.tvCreatedAt);
        }
    }
}
