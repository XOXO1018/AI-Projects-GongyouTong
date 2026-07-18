package com.gongyoutong.app.data;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.gongyoutong.app.R;
import com.gongyoutong.app.workorder.WorkOrderStatus;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 工单列表 Adapter
 */
public class WorkOrderAdapter extends RecyclerView.Adapter<WorkOrderAdapter.ViewHolder> {

    private List<WorkOrder> list = new ArrayList<>();
    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(WorkOrder workOrder);
    }

    public WorkOrderAdapter(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setList(List<WorkOrder> newList) {
        list.clear();
        if (newList != null) {
            list.addAll(newList);
        }
        notifyDataSetChanged();
    }

    public WorkOrder getItem(int position) {
        if (position >= 0 && position < list.size()) {
            return list.get(position);
        }
        return null;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_workorder, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        WorkOrder item = list.get(position);

        // 标题
        holder.tvTitle.setText(item.getTitle() != null ? item.getTitle() : "");

        // 描述（2行截断）
        String desc = item.getDescription();
        if (desc != null && !desc.isEmpty()) {
            holder.tvDescription.setText(desc);
            holder.tvDescription.setVisibility(View.VISIBLE);
        } else {
            holder.tvDescription.setVisibility(View.GONE);
        }

        // 客户名
        holder.tvContactName.setText(item.getContactName() != null ? item.getContactName() : "");

        // 地址
        holder.tvAddress.setText(item.getAddress() != null ? item.getAddress() : "");

        // 时间
        if (item.getAppointmentTime() > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA);
            holder.tvTime.setText(sdf.format(new Date(item.getAppointmentTime())));
        } else {
            holder.tvTime.setText("时间待定");
        }

        // 状态标签（带颜色背景）
        WorkOrderStatus status = WorkOrderStatus.fromString(item.getStatus());
        holder.tvStatus.setText(status.getDisplayName());
        applyStatusStyle(holder, status);

        // 距离（暂不计算，留占位）
        holder.tvDistance.setText("");

        // 点击事件
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    /**
     * 根据状态设置标签颜色
     */
    private void applyStatusStyle(ViewHolder holder, WorkOrderStatus status) {
        int bgColorRes;
        int textColorRes;

        switch (status) {
            case PENDING:
                bgColorRes = R.color.workorder_tag_bg;
                textColorRes = R.color.workorder_pending;
                break;
            case ACCEPTED:
            case DEPARTED:
            case ARRIVED:
            case REPAIRING:
            case VERIFYING:
                bgColorRes = R.color.workorder_active_tag_bg;
                textColorRes = R.color.workorder_active;
                break;
            case COMPLETED:
                bgColorRes = R.color.workorder_completed_tag_bg;
                textColorRes = R.color.workorder_completed;
                break;
            case EXCEPTION:
                bgColorRes = R.color.workorder_exception_tag_bg;
                textColorRes = R.color.workorder_exception;
                break;
            default:
                bgColorRes = R.color.workorder_tag_bg;
                textColorRes = R.color.workorder_pending;
                break;
        }

        holder.tvStatus.setBackgroundTintList(
                ContextCompat.getColorStateList(holder.itemView.getContext(), bgColorRes));
        holder.tvStatus.setTextColor(
                ContextCompat.getColor(holder.itemView.getContext(), textColorRes));
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvStatus;
        TextView tvTime;
        TextView tvTitle;
        TextView tvDescription;
        TextView tvContactName;
        TextView tvAddress;
        TextView tvDistance;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvDescription = itemView.findViewById(R.id.tvDescription);
            tvContactName = itemView.findViewById(R.id.tvContactName);
            tvAddress = itemView.findViewById(R.id.tvAddress);
            tvDistance = itemView.findViewById(R.id.tvDistance);
        }
    }
}
