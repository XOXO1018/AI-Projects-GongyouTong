package com.gongyoutong.app.ui.schedule;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.tabs.TabLayout;
import com.gongyoutong.app.R;
import com.gongyoutong.app.data.Schedule;
import com.gongyoutong.app.data.ScheduleAdapter;
import com.gongyoutong.app.database.AppDatabase;
import com.gongyoutong.app.database.ScheduleDao;
import com.gongyoutong.app.database.ScheduleEntity;
import com.gongyoutong.app.ui.detail.ScheduleDetailActivity;
import com.gongyoutong.app.ui.main.MainActivity;
import com.gongyoutong.app.ui.profile.ProfileActivity;
import com.gongyoutong.app.ui.workorder.WorkOrderActivity;
import com.gongyoutong.app.utils.DateUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 日程页面 - 日历视图 + 筛选功能
 */
public class ScheduleActivity extends AppCompatActivity {

    private RecyclerView rvCalendar;
    private RecyclerView rvSchedule;
    private TextView tvScheduleCount;
    private TextView tvCurrentMonth;
    private TextView tvEmptyTitle;
    private TextView tvEmptyHint;
    private LinearLayout layoutEmpty;
    private TabLayout tabFilter;
    private ImageView btnPrevMonth;
    private ImageView btnNextMonth;
    
    private ScheduleAdapter scheduleAdapter;
    private CalendarAdapter calendarAdapter;
    private final List<Schedule> allSchedules = new ArrayList<>();
    private final List<Schedule> filteredSchedules = new ArrayList<>();
    private final Set<String> datesWithSchedules = new HashSet<>();

    private AppDatabase database;
    private ScheduleDao scheduleDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private int currentFilter = 0; // 0=全部, 1=待出发, 2=已完成
    private Calendar selectedDate = Calendar.getInstance();
    private Calendar currentMonth = Calendar.getInstance();
    private SimpleDateFormat monthFormat = new SimpleDateFormat("yyyy年M月", Locale.CHINA);
    private SimpleDateFormat dayFormat = new SimpleDateFormat("d", Locale.CHINA);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_schedule);

        database = AppDatabase.getInstance(this);
        scheduleDao = database.scheduleDao();

        initViews();
        setupCalendar();
        setupRecyclerView();
        setupTabFilter();
        setupMonthNavigation();
        setupBottomNav();
        setupBackHandler();
        loadAllSchedules();
        updateMonthDisplay();
    }

    private void initViews() {
        rvCalendar = findViewById(R.id.rvCalendar);
        rvSchedule = findViewById(R.id.rvSchedule);
        tvScheduleCount = findViewById(R.id.tvScheduleCount);
        tvCurrentMonth = findViewById(R.id.tvCurrentMonth);
        tvEmptyTitle = findViewById(R.id.tvEmptyTitle);
        tvEmptyHint = findViewById(R.id.tvEmptyHint);
        layoutEmpty = findViewById(R.id.layoutEmpty);
        tabFilter = findViewById(R.id.tabFilter);
        btnPrevMonth = findViewById(R.id.btnPrevMonth);
        btnNextMonth = findViewById(R.id.btnNextMonth);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            return insets;
        });
    }

    private void setupCalendar() {
        // 使用clone确保selectedDate和displayMonth是独立对象
        calendarAdapter = new CalendarAdapter(
                (Calendar) selectedDate.clone(), 
                (Calendar) currentMonth.clone(), 
                datesWithSchedules, day -> {
            // 选择日期 - 需要更新年、月、日
            selectedDate.set(Calendar.YEAR, currentMonth.get(Calendar.YEAR));
            selectedDate.set(Calendar.MONTH, currentMonth.get(Calendar.MONTH));
            selectedDate.set(Calendar.DAY_OF_MONTH, day);
            // 更新adapter的选中日期（使用clone避免引用问题）
            calendarAdapter.setSelectedDate((Calendar) selectedDate.clone());
            calendarAdapter.setDisplayMonth((Calendar) currentMonth.clone());
            calendarAdapter.notifyDataSetChanged();
            applyFilter();
        });

        rvCalendar.setLayoutManager(new GridLayoutManager(this, 7));
        rvCalendar.setAdapter(calendarAdapter);
        updateCalendarDays();
    }

    private void setupRecyclerView() {
        scheduleAdapter = new ScheduleAdapter(schedule -> {
            Intent intent = new Intent(this, ScheduleDetailActivity.class);
            intent.putExtra("schedule_id", schedule.getId());
            startActivity(intent);
        });
        
        scheduleAdapter.setOnItemDeleteListener((schedule, position) -> {
            showDeleteConfirmDialog(schedule, position);
        });
        
        rvSchedule.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        rvSchedule.setAdapter(scheduleAdapter);
    }

    private void setupTabFilter() {
        tabFilter.addTab(tabFilter.newTab().setText("全部"));
        tabFilter.addTab(tabFilter.newTab().setText("进行中"));
        tabFilter.addTab(tabFilter.newTab().setText("已完成"));

        tabFilter.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                currentFilter = tab.getPosition(); // 0=全部, 1=待出发, 2=已完成
                applyFilter();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void setupMonthNavigation() {
        btnPrevMonth.setOnClickListener(v -> {
            currentMonth.add(Calendar.MONTH, -1);
            updateMonthDisplay();
            updateCalendarDays();
        });

        btnNextMonth.setOnClickListener(v -> {
            currentMonth.add(Calendar.MONTH, 1);
            updateMonthDisplay();
            updateCalendarDays();
        });
    }

    private void updateMonthDisplay() {
        tvCurrentMonth.setText(monthFormat.format(currentMonth.getTime()));
    }

    private void updateCalendarDays() {
        Calendar cal = (Calendar) currentMonth.clone();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        
        int firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1; // 0 = 周日
        int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        
        List<CalendarDay> days = new ArrayList<>();
        
        // 添加空白格子
        for (int i = 0; i < firstDayOfWeek; i++) {
            days.add(new CalendarDay(0, false, false));
        }
        
        // 添加日期
        Calendar today = Calendar.getInstance();
        for (int i = 1; i <= daysInMonth; i++) {
            boolean isToday = today.get(Calendar.YEAR) == currentMonth.get(Calendar.YEAR)
                    && today.get(Calendar.MONTH) == currentMonth.get(Calendar.MONTH)
                    && today.get(Calendar.DAY_OF_MONTH) == i;
            
            // 使用与loadAllSchedules相同的日期格式 (yyyy-MM-dd)
            String dateKey = String.format(Locale.CHINA, "%d-%02d-%02d", 
                    currentMonth.get(Calendar.YEAR), 
                    currentMonth.get(Calendar.MONTH) + 1, 
                    i);
            boolean hasSchedule = datesWithSchedules.contains(dateKey);
            
            days.add(new CalendarDay(i, isToday, hasSchedule));
        }
        
        calendarAdapter.setDisplayMonth((Calendar) currentMonth.clone());
        calendarAdapter.setDays(days);
        calendarAdapter.notifyDataSetChanged();
    }

    private void setupBottomNav() {
        BottomNavigationView nav = findViewById(R.id.bottomNav);
        nav.setSelectedItemId(R.id.nav_schedule);

        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                Intent intent = new Intent(this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                return true;
            } else if (id == R.id.nav_workorder) {
                Intent intent = new Intent(this, WorkOrderActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                return true;
            } else if (id == R.id.nav_schedule) {
                return true;
            } else if (id == R.id.nav_knowledge) {
                Intent intent = new Intent(this, com.gongyoutong.app.ui.knowledge.KnowledgeActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                return true;
            } else if (id == R.id.nav_profile) {
                Intent intent = new Intent(this, ProfileActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                return true;
            }
            return false;
        });
    }

    private void setupBackHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
            }
        });
    }

    private void loadAllSchedules() {
        executor.execute(() -> {
            List<ScheduleEntity> entities = scheduleDao.getAll();
            allSchedules.clear();
            datesWithSchedules.clear();
            
            // 使用与updateCalendarDays相同的日期格式 (yyyy-MM-dd)
            SimpleDateFormat dateKeyFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
            
            for (ScheduleEntity entity : entities) {
                Schedule schedule = convertFromEntity(entity);
                allSchedules.add(schedule);
                
                // 记录有日程的日期
                if (schedule.getTime() != null) {
                    String dateKey = dateKeyFormat.format(schedule.getTime());
                    datesWithSchedules.add(dateKey);
                }
            }

            // 按时间从早到晚排列
            allSchedules.sort((s1, s2) -> {
                if (s1 == null || s2 == null || s1.getTime() == null || s2.getTime() == null) return 0;
                return Long.compare(s1.getTime().getTime(), s2.getTime().getTime());
            });

            runOnUiThread(() -> {
                updateCalendarDays();
                applyFilter();
                updateEmptyState();
            });
        });
    }

    private void applyFilter() {
        filteredSchedules.clear();
        
        // 筛选状态：currentFilter 0=全部, 1=进行中, 2=已完成
        switch (currentFilter) {
            case 1:
                for (Schedule schedule : allSchedules) {
                    if (!isSameDay(schedule)) continue;
                    String s = schedule.getStatus();
                    if ("待出发".equals(s) || "进行中".equals(s)) {
                        filteredSchedules.add(schedule);
                    }
                }
                break;
            case 2:
                for (Schedule schedule : allSchedules) {
                    if (!isSameDay(schedule)) continue;
                    if ("已完成".equals(schedule.getStatus())) {
                        filteredSchedules.add(schedule);
                    }
                }
                break;
            case 0:
            default:
                for (Schedule schedule : allSchedules) {
                    if (isSameDay(schedule)) {
                        filteredSchedules.add(schedule);
                    }
                }
                break;
        }

        scheduleAdapter.setList(new ArrayList<>(filteredSchedules));
        updateScheduleCount();
        updateEmptyState();
    }

    private boolean isSameDay(Schedule schedule) {
        if (schedule.getTime() == null) return false;
        Calendar scheduleCal = Calendar.getInstance();
        scheduleCal.setTime(schedule.getTime());
        return scheduleCal.get(Calendar.YEAR) == selectedDate.get(Calendar.YEAR)
                && scheduleCal.get(Calendar.MONTH) == selectedDate.get(Calendar.MONTH)
                && scheduleCal.get(Calendar.DAY_OF_MONTH) == selectedDate.get(Calendar.DAY_OF_MONTH);
    }

    private void updateScheduleCount() {
        SimpleDateFormat displayFormat = new SimpleDateFormat("M月d日", Locale.CHINA);
        String[] labels = {"全部", "进行中", "已完成"};
        // 确保索引不越界
        int index = Math.min(currentFilter, labels.length - 1);
        tvScheduleCount.setText(displayFormat.format(selectedDate.getTime()) + " · " + labels[index] + " " + filteredSchedules.size() + " 项");
    }

    private void updateEmptyState() {
        if (filteredSchedules.isEmpty()) {
            layoutEmpty.setVisibility(View.VISIBLE);
            rvSchedule.setVisibility(View.GONE);
            
            String[][] emptyTexts = {
                {"当天暂无日程", "在首页添加新的日程"},
                {"当天暂无进行中日程", "在首页添加新的日程"},
                {"当天暂无已完成日程", "完成工作后会显示在这里"}
            };
            
            // 确保索引不越界
            int index = Math.min(currentFilter, emptyTexts.length - 1);
            tvEmptyTitle.setText(emptyTexts[index][0]);
            tvEmptyHint.setText(emptyTexts[index][1]);
        } else {
            layoutEmpty.setVisibility(View.GONE);
            rvSchedule.setVisibility(View.VISIBLE);
        }
    }

    private void showDeleteConfirmDialog(Schedule schedule, int position) {
        new AlertDialog.Builder(this)
            .setTitle("删除日程")
            .setMessage("确定要删除 \"" + schedule.getTitle() + "\" 吗？")
            .setPositiveButton("删除", (d, w) -> {
                executor.execute(() -> {
                    scheduleDao.deleteById(schedule.getId());
                    allSchedules.removeIf(s -> s.getId().equals(schedule.getId()));
                    
                    // 更新日期指示 - 使用与loadAllSchedules相同的日期格式
                    if (schedule.getTime() != null) {
                        SimpleDateFormat dateKeyFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
                        String dateKey = dateKeyFormat.format(schedule.getTime());
                        
                        // 检查当天是否还有其他日程（精确匹配年、月、日）
                        boolean hasOtherSchedules = false;
                        for (Schedule s : allSchedules) {
                            if (s.getTime() != null) {
                                Calendar cal = Calendar.getInstance();
                                cal.setTime(s.getTime());
                                Calendar deletedCal = Calendar.getInstance();
                                deletedCal.setTime(schedule.getTime());
                                if (cal.get(Calendar.YEAR) == deletedCal.get(Calendar.YEAR)
                                        && cal.get(Calendar.MONTH) == deletedCal.get(Calendar.MONTH)
                                        && cal.get(Calendar.DAY_OF_MONTH) == deletedCal.get(Calendar.DAY_OF_MONTH)) {
                                    hasOtherSchedules = true;
                                    break;
                                }
                            }
                        }
                        
                        if (!hasOtherSchedules) {
                            datesWithSchedules.remove(dateKey);
                        }
                    }
                    
                    runOnUiThread(() -> {
                        updateCalendarDays();
                        applyFilter();
                        // Toast.makeText(this, "日程已删除", Toast.LENGTH_SHORT).show();
                    });
                });
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    private Schedule convertFromEntity(ScheduleEntity entity) {
        Schedule schedule = new Schedule();
        schedule.setId(entity.getId());
        schedule.setTitle(entity.getTitle());
        schedule.setAddress(entity.getAddress());
        schedule.setWorkType(entity.getWorkType());
        schedule.setTime(new Date(entity.getTime()));
        schedule.setStatus(entity.getStatus());
        return schedule;
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 同步底部导航状态
        BottomNavigationView nav = findViewById(R.id.bottomNav);
        nav.setSelectedItemId(R.id.nav_schedule);
        loadAllSchedules();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null) executor.shutdown();
    }

    // 日历日期数据类
    private static class CalendarDay {
        int day;
        boolean isToday;
        boolean hasSchedule;

        CalendarDay(int day, boolean isToday, boolean hasSchedule) {
            this.day = day;
            this.isToday = isToday;
            this.hasSchedule = hasSchedule;
        }
    }

    // 日历适配器
    private static class CalendarAdapter extends RecyclerView.Adapter<CalendarAdapter.ViewHolder> {
        private List<CalendarDay> days = new ArrayList<>();
        private Calendar selectedDate;
        private Calendar displayMonth;
        private Set<String> datesWithSchedules;
        private OnDayClickListener listener;

        interface OnDayClickListener {
            void onDayClick(int day);
        }

        CalendarAdapter(Calendar selectedDate, Calendar displayMonth, Set<String> datesWithSchedules, OnDayClickListener listener) {
            this.selectedDate = selectedDate;
            this.displayMonth = displayMonth;
            this.datesWithSchedules = datesWithSchedules;
            this.listener = listener;
        }

        void setDays(List<CalendarDay> days) {
            this.days = days;
        }

        void setSelectedDate(Calendar date) {
            this.selectedDate = date;
        }

        void setDisplayMonth(Calendar month) {
            this.displayMonth = month;
        }

        @Override
        public ViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            View view = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_calendar_day, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            CalendarDay day = days.get(position);
            holder.bind(day);
        }

        @Override
        public int getItemCount() {
            return days.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvDay;
            View indicatorDot;

            ViewHolder(View itemView) {
                super(itemView);
                tvDay = itemView.findViewById(R.id.tvDay);
                indicatorDot = itemView.findViewById(R.id.indicatorDot);
            }

            void bind(CalendarDay day) {
                if (day.day == 0) {
                    tvDay.setText("");
                    tvDay.setBackground(null);
                    indicatorDot.setVisibility(View.GONE);
                    itemView.setClickable(false);
                } else {
                    tvDay.setText(String.valueOf(day.day));

                    // 动态判断是否为选中日期（根据selectedDate和displayMonth计算）
                    boolean isSelected = selectedDate.get(Calendar.YEAR) == displayMonth.get(Calendar.YEAR)
                            && selectedDate.get(Calendar.MONTH) == displayMonth.get(Calendar.MONTH)
                            && selectedDate.get(Calendar.DAY_OF_MONTH) == day.day;

                    android.content.Context ctx = itemView.getContext();

                    if (isSelected) {
                        // 选中日期：实心橙色圆圈 + 白色文字
                        tvDay.setBackgroundResource(R.drawable.bg_calendar_selected);
                        tvDay.setTextColor(android.graphics.Color.WHITE);
                    } else if (day.isToday) {
                        // 今日：空心橙色圆圈 + 主色文字（主题感知）
                        tvDay.setBackgroundResource(R.drawable.bg_calendar_today);
                        tvDay.setTextColor(ContextCompat.getColor(ctx, R.color.primary));
                    } else {
                        // 普通日期：无背景 + 主题感知文字色
                        tvDay.setBackground(null);
                        tvDay.setTextColor(ContextCompat.getColor(ctx, R.color.text_primary));
                    }

                    indicatorDot.setVisibility(day.hasSchedule ? View.VISIBLE : View.GONE);

                    itemView.setClickable(true);
                    itemView.setOnClickListener(v -> {
                        if (listener != null) {
                            listener.onDayClick(day.day);
                        }
                    });
                }
            }
        }
    }
}
