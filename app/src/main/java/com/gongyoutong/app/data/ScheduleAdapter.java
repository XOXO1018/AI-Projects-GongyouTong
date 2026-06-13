package com.gongyoutong.app.data;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import androidx.core.content.ContextCompat;
import com.gongyoutong.app.R;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class ScheduleAdapter extends RecyclerView.Adapter<ScheduleAdapter.ViewHolder> {

    private List<Schedule> list = new ArrayList<>();
    private final OnItemClick listener;
    private OnItemDeleteListener deleteListener;

    // ========== 静态日期格式化器（避免重复创建对象）==========
    private static final ThreadLocal<SimpleDateFormat> TIME_FORMAT =
            ThreadLocal.withInitial(() -> new SimpleDateFormat("HH:mm", Locale.CHINA));
    private static final ThreadLocal<SimpleDateFormat> PERIOD_FORMAT =
            ThreadLocal.withInitial(() -> new SimpleDateFormat("a", Locale.CHINA));
    private static final ThreadLocal<SimpleDateFormat> DATE_FORMAT =
            ThreadLocal.withInitial(() -> new SimpleDateFormat("MM/dd HH:mm", Locale.CHINA));
    private static final ThreadLocal<SimpleDateFormat> DATE_TIME_FORMAT =
            ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA));

    public interface OnItemClick {
        void onClick(Schedule schedule);
    }

    public interface OnItemDeleteListener {
        void onDelete(Schedule schedule, int position);
    }

    public ScheduleAdapter(OnItemClick listener) {
        this.listener = listener;
    }

    public void setOnItemDeleteListener(OnItemDeleteListener listener) {
        this.deleteListener = listener;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setList(List<Schedule> newList) {
        List<Schedule> oldList = new ArrayList<>(this.list);
        this.list = new ArrayList<>(newList);
        
        // Use DiffUtil for smooth animations
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldList.size();
            }

            @Override
            public int getNewListSize() {
                return newList.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return oldList.get(oldItemPosition).getId()
                        .equals(newList.get(newItemPosition).getId());
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                Schedule oldItem = oldList.get(oldItemPosition);
                Schedule newItem = newList.get(newItemPosition);
                // 【修复】用 Objects.equals + null 安全比较
                return Objects.equals(oldItem.getTitle(), newItem.getTitle())
                        && Objects.equals(oldItem.getAddress(), newItem.getAddress())
                        && Objects.equals(oldItem.getWorkType(), newItem.getWorkType())
                        && Objects.equals(oldItem.getStatus(), newItem.getStatus())
                        && Objects.equals(oldItem.getTime(), newItem.getTime());
            }
        });
        
        diffResult.dispatchUpdatesTo(this);
    }

    public void addItem(Schedule schedule) {
        list.add(0, schedule);
        notifyItemInserted(0);
    }

    public void removeItem(int position) {
        if (position >= 0 && position < list.size()) {
            list.remove(position);
            notifyItemRemoved(position);
            notifyItemRangeChanged(position, list.size() - position);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_schedule, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Schedule item = list.get(position);
        
        // Title
        holder.tvTaskTitle.setText(item.getTitle());
        
        // Time - 左侧大时间显示
        formatTimeDisplay(item.getTime(), holder.tvTimeValue, holder.tvTimePeriod);
        
        // 右侧时间信息
        holder.tvTime.setText(formatTimeWithDate(item.getTime()));
        
        // Address
        holder.tvAddress.setText(item.getAddress());
        
        // Status with dynamic styling
        updateStatusBadge(holder, item.getStatus());
        
        // Work type tag
        holder.tvWorkType.setText(item.getWorkType());
        
        // Click listeners
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onClick(item);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (deleteListener != null) {
                deleteListener.onDelete(item, position);
            }
            return true;
        });

        // Status indicator color
        int statusColor = getStatusColor(holder, item.getStatus());
        holder.colorBar.setBackgroundColor(statusColor);
    }

    private void updateStatusBadge(ViewHolder holder, String status) {
        holder.tvStatus.setText(status);
        
        int bgColor;
        int textColor;
        
        switch (status) {
            case "待出发":
                bgColor = holder.itemView.getContext().getColor(R.color.status_pending_bg);
                textColor = holder.itemView.getContext().getColor(R.color.status_pending);
                break;
            case "进行中":
                bgColor = holder.itemView.getContext().getColor(R.color.status_in_progress_bg);
                textColor = holder.itemView.getContext().getColor(R.color.status_in_progress);
                break;
            case "已完成":
                bgColor = holder.itemView.getContext().getColor(R.color.status_completed_bg);
                textColor = holder.itemView.getContext().getColor(R.color.status_completed);
                break;
            case "已取消":
                bgColor = holder.itemView.getContext().getColor(R.color.error_container);
                textColor = holder.itemView.getContext().getColor(R.color.error);
                break;
            default:
                bgColor = holder.itemView.getContext().getColor(R.color.status_pending_bg);
                textColor = holder.itemView.getContext().getColor(R.color.status_pending);
                break;
        }
        
        holder.tvStatus.getBackground().setTint(bgColor);
        holder.tvStatus.setTextColor(textColor);
    }

    private int getStatusColor(ViewHolder holder, String status) {
        android.content.Context ctx = holder.itemView.getContext();
        switch (status) {
            case "待出发":
                return ContextCompat.getColor(ctx, R.color.status_pending);
            case "进行中":
                return ContextCompat.getColor(ctx, R.color.status_in_progress);
            case "已完成":
                return ContextCompat.getColor(ctx, R.color.status_completed);
            default:
                return ContextCompat.getColor(ctx, R.color.status_pending);
        }
    }

    /**
     * 设置左侧时间显示：10:00 上午 并排显示
     */
    private void formatTimeDisplay(Date time, TextView tvTimeValue, TextView tvTimePeriod) {
        if (time == null) {
            tvTimeValue.setText("--:--");
            tvTimePeriod.setText("待定");
            return;
        }

        String timeStr = TIME_FORMAT.get().format(time);
        String periodStr = PERIOD_FORMAT.get().format(time);

        // 转换上午/下午为中文
        if ("AM".equals(periodStr)) {
            periodStr = "上午";
        } else if ("PM".equals(periodStr)) {
            periodStr = "下午";
        }

        tvTimeValue.setText(timeStr);
        tvTimePeriod.setText(periodStr);
    }

    /**
     * 右侧时间信息：今天 10:00 格式
     */
    private String formatTimeWithDate(Date time) {
        if (time == null) return "时间待定";

        Calendar today = Calendar.getInstance();
        Calendar scheduleTime = Calendar.getInstance();
        scheduleTime.setTime(time);

        if (isSameDay(today, scheduleTime)) {
            return "今天 " + TIME_FORMAT.get().format(time);
        } else if (isTomorrow(today, scheduleTime)) {
            return "明天 " + TIME_FORMAT.get().format(time);
        } else if (isYesterday(today, scheduleTime)) {
            return "昨天 " + TIME_FORMAT.get().format(time);
        } else {
            return DATE_FORMAT.get().format(time);
        }
    }

    private boolean isSameDay(Calendar cal1, Calendar cal2) {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)
                && cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }

    private boolean isTomorrow(Calendar today, Calendar scheduleTime) {
        Calendar tomorrow = (Calendar) today.clone();
        tomorrow.add(Calendar.DAY_OF_YEAR, 1);
        return isSameDay(tomorrow, scheduleTime);
    }

    private boolean isYesterday(Calendar today, Calendar scheduleTime) {
        Calendar yesterday = (Calendar) today.clone();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        return isSameDay(yesterday, scheduleTime);
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    public Schedule getItem(int position) {
        if (position >= 0 && position < list.size()) {
            return list.get(position);
        }
        return null;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        View colorBar;
        TextView tvTaskTitle;
        TextView tvTime;
        TextView tvTimeValue;
        TextView tvTimePeriod;
        TextView tvAddress;
        TextView tvStatus;
        TextView tvWorkType;
        ImageView ivArrow;

        ViewHolder(View itemView) {
            super(itemView);
            colorBar = itemView.findViewById(R.id.colorBar);
            tvTaskTitle = itemView.findViewById(R.id.tvTaskTitle);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvTimeValue = itemView.findViewById(R.id.tvTimeValue);
            tvTimePeriod = itemView.findViewById(R.id.tvTimePeriod);
            tvAddress = itemView.findViewById(R.id.tvAddress);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            tvWorkType = itemView.findViewById(R.id.tvWorkType);
            ivArrow = itemView.findViewById(R.id.ivArrow);
        }
    }
}
