package com.gongyoutong.app.ui.main;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.gongyoutong.app.R;
import com.gongyoutong.app.ai.VivoAiService;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatViewHolder> {

    private final List<VivoAiService.ChatMessage> messages = new ArrayList<>();
    private static final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.CHINA);

    public void setMessages(List<VivoAiService.ChatMessage> newMessages) {
        messages.clear();
        messages.addAll(newMessages);
        notifyDataSetChanged();
    }

    public void addMessage(VivoAiService.ChatMessage message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    public void updateLastMessage(String content) {
        if (!messages.isEmpty()) {
            VivoAiService.ChatMessage last = messages.get(messages.size() - 1);
            messages.set(messages.size() - 1, new VivoAiService.ChatMessage(last.role, content));
            notifyItemChanged(messages.size() - 1);
        }
    }

    public void removeLastMessage() {
        if (!messages.isEmpty()) {
            int pos = messages.size() - 1;
            messages.remove(pos);
            notifyItemRemoved(pos);
        }
    }

    public int getLastPosition() {
        return messages.size() - 1;
    }

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_message, parent, false);
        return new ChatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        VivoAiService.ChatMessage message = messages.get(position);
        boolean isUser = "user".equals(message.role);

        holder.tvChatRole.setText(isUser ? "我" : "蓝心助手");
        holder.tvChatRole.setTextColor(holder.itemView.getContext().getResources().getColor(
                isUser ? R.color.primary : R.color.accent_blue, null));
        holder.tvChatContent.setText(message.content);
        holder.tvChatTime.setText(timeFormat.format(new Date(message.timestamp)));

        // User messages show user icon, AI messages show AI icon
        holder.ivChatAvatar.setImageResource(isUser ? R.drawable.ic_send : R.drawable.ic_ai_worker);
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class ChatViewHolder extends RecyclerView.ViewHolder {
        ImageView ivChatAvatar;
        TextView tvChatRole, tvChatContent, tvChatTime;

        ChatViewHolder(@NonNull View itemView) {
            super(itemView);
            ivChatAvatar = itemView.findViewById(R.id.ivChatAvatar);
            tvChatRole = itemView.findViewById(R.id.tvChatRole);
            tvChatContent = itemView.findViewById(R.id.tvChatContent);
            tvChatTime = itemView.findViewById(R.id.tvChatTime);
        }
    }
}
