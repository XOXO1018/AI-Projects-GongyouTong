package com.gongyoutong.app.data;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.gongyoutong.app.R;
import com.gongyoutong.app.ai.OnlineKnowledgeService.OnlineKnowledgeItem;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

/**
 * 在线知识列表 Adapter —— 橙色卡片 + 点击阅读 + 添加到本地
 */
public class OnlineKnowledgeAdapter extends RecyclerView.Adapter<OnlineKnowledgeAdapter.VH> {

    public interface OnItemActionListener {
        void onViewContent(OnlineKnowledgeItem item);
        void onAddToLocal(OnlineKnowledgeItem item, int position);
    }

    private final List<OnlineKnowledgeItem> list = new ArrayList<>();
    private OnItemActionListener listener;

    public OnlineKnowledgeAdapter(OnItemActionListener listener) {
        this.listener = listener;
    }

    public void setList(List<OnlineKnowledgeItem> newList) {
        list.clear();
        if (newList != null) list.addAll(newList);
        notifyDataSetChanged();
    }

    public void markAdded(int position) {
        if (position >= 0 && position < list.size()) {
            list.get(position).setAdded(true);
            notifyItemChanged(position);
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_online_knowledge, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        OnlineKnowledgeItem item = list.get(position);

        h.tvTitle.setText(item.getTitle() != null ? item.getTitle() : "");
        h.tvCategory.setText(item.getCategory() != null ? item.getCategory() : "综合维修");
        h.tvSummary.setText(item.getSummary() != null ? item.getSummary() : "");

        if (item.isAdded()) {
            h.btnAdd.setIconResource(android.R.drawable.checkbox_on_background);
            h.btnAdd.setEnabled(false);
        } else {
            h.btnAdd.setIconResource(R.drawable.ic_add);
            h.btnAdd.setEnabled(true);
        }

        // 点击文本框→打开 WebView 阅读
        h.layoutContent.setOnClickListener(v -> {
            if (listener != null) listener.onViewContent(item);
        });

        // 添加到我的知识
        h.btnAdd.setOnClickListener(v -> {
            if (listener != null && !item.isAdded()) {
                listener.onAddToLocal(item, h.getAdapterPosition());
            }
        });
    }

    @Override
    public int getItemCount() { return list.size(); }

    static class VH extends RecyclerView.ViewHolder {
        View layoutContent;
        TextView tvTitle, tvCategory, tvSummary;
        MaterialButton btnAdd;

        VH(@NonNull View v) {
            super(v);
            layoutContent = v.findViewById(R.id.layoutContent);
            tvTitle = v.findViewById(R.id.tvTitle);
            tvCategory = v.findViewById(R.id.tvCategory);
            tvSummary = v.findViewById(R.id.tvSummary);
            btnAdd = v.findViewById(R.id.btnAddToLocal);
        }
    }
}
