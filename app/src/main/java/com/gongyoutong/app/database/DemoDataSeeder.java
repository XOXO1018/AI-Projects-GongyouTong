package com.gongyoutong.app.database;

import android.content.Context;
import android.util.Log;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 演示数据预置器
 * 在应用首次启动时填充示例数据，用于比赛演示
 */
public class DemoDataSeeder {

    private static final String TAG = "DemoDataSeeder";
    private static final String PREFS_NAME = "demo_data_prefs";
    private static final String KEY_SEEDED = "data_seeded";

    private final Context context;
    private final AppDatabase database;
    private final ExecutorService executor;

    public DemoDataSeeder(Context context) {
        this.context = context.getApplicationContext();
        this.database = AppDatabase.getInstance(context);
        this.executor = Executors.newSingleThreadExecutor();
    }

    /**
     * 检查是否需要预置数据，如果需要则执行
     */
    public void seedIfNeeded() {
        android.content.SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY_SEEDED, false)) {
            Log.d(TAG, "演示数据已预置，跳过");
            return;
        }

        Log.d(TAG, "开始预置演示数据...");
        executor.execute(() -> {
            try {
                seedKnowledgeData();
                seedWorkOrderData();
                prefs.edit().putBoolean(KEY_SEEDED, true).apply();
                Log.d(TAG, "演示数据预置完成");
            } catch (Exception e) {
                Log.e(TAG, "预置演示数据失败", e);
            }
        });
    }

    /**
     * 预置知识库数据
     */
    private void seedKnowledgeData() {
        KnowledgeDao dao = database.knowledgeDao();

        // 知识1：空调维修
        KnowledgeEntity k1 = new KnowledgeEntity();
        k1.setId(UUID.randomUUID().toString());
        k1.setTitle("空调不制冷故障排查");
        k1.setRawContent("空调不制冷的常见原因及排查步骤：\n\n" +
                "1. 检查电源：确认空调已通电，电源指示灯正常\n" +
                "2. 检查遥控器：确认遥控器电池有电，模式设置为制冷\n" +
                "3. 检查过滤网：过滤网积尘过多会影响制冷效果，需清洗\n" +
                "4. 检查室外机：确认室外机正常运转，散热片无遮挡\n" +
                "5. 检查制冷剂：制冷剂不足会导致制冷效果差，需专业人员添加\n" +
                "6. 检查压缩机：压缩机故障需更换");
        k1.setAiSummary("空调不制冷主要排查：电源→遥控器→过滤网→室外机→制冷剂→压缩机。过滤网积尘和制冷剂不足是最常见原因。");
        k1.setSourceType("text");
        k1.setCreatorName("系统预置");
        long now = System.currentTimeMillis();
        k1.setCreatedAt(now);
        k1.setUpdatedAt(now);
        k1.setMindMapJson("{\"root\":\"空调不制冷\",\"children\":[{\"name\":\"电源问题\"},{\"name\":\"遥控器问题\"},{\"name\":\"过滤网积尘\"},{\"name\":\"室外机问题\"},{\"name\":\"制冷剂不足\"},{\"name\":\"压缩机故障\"}]}");
        dao.insert(k1);

        // 知识2：洗衣机维修
        KnowledgeEntity k2 = new KnowledgeEntity();
        k2.setId(UUID.randomUUID().toString());
        k2.setTitle("洗衣机不排水故障处理");
        k2.setRawContent("洗衣机不排水的排查方法：\n\n" +
                "1. 检查排水管：确认排水管没有弯曲或堵塞\n" +
                "2. 检查排水泵：排水泵可能被异物卡住，需清理\n" +
                "3. 检查过滤器：洗衣机底部过滤器需定期清理\n" +
                "4. 检查水位开关：水位开关故障会导致不排水\n" +
                "5. 检查程序设置：确认选择了正确的洗涤程序");
        k2.setAiSummary("洗衣机不排水主要检查：排水管→排水泵→过滤器→水位开关→程序设置。排水管弯曲和过滤器堵塞是常见原因。");
        k2.setSourceType("text");
        k2.setCreatorName("系统预置");
        k2.setCreatedAt(now - 86400000); // 昨天
        k2.setUpdatedAt(now - 86400000);
        dao.insert(k2);

        // 知识3：冰箱维修
        KnowledgeEntity k3 = new KnowledgeEntity();
        k3.setId(UUID.randomUUID().toString());
        k3.setTitle("冰箱冷藏室结冰处理");
        k3.setRawContent("冰箱冷藏室结冰的原因及解决方法：\n\n" +
                "1. 温度设置过低：将冷藏室温度调至3-5度\n" +
                "2. 门封条老化：门封条不严会导致冷气外泄，结冰\n" +
                "3. 排水孔堵塞：冷藏室排水孔堵塞会导致积水结冰\n" +
                "4. 食物放置过多：食物过多影响空气流通\n" +
                "5. 温控器故障：温控器失灵需更换");
        k3.setAiSummary("冰箱结冰主要检查：温度设置→门封条→排水孔→食物放置→温控器。温度过低和门封条老化最常见。");
        k3.setSourceType("text");
        k3.setCreatorName("系统预置");
        k3.setCreatedAt(now - 172800000); // 前天
        k3.setUpdatedAt(now - 172800000);
        dao.insert(k3);

        // 知识4：电路维修
        KnowledgeEntity k4 = new KnowledgeEntity();
        k4.setId(UUID.randomUUID().toString());
        k4.setTitle("家庭电路跳闸处理");
        k4.setRawContent("家庭电路跳闸的排查步骤：\n\n" +
                "1. 确认跳闸原因：过载、短路或漏电\n" +
                "2. 检查总开关：确认总开关额定电流是否足够\n" +
                "3. 逐个回路排查：关闭所有分开关，逐个打开找出问题回路\n" +
                "4. 检查电器：拔掉该回路所有电器，逐一插入测试\n" +
                "5. 检查线路：线路老化或破损需更换\n" +
                "⚠️ 安全提示：操作前务必断电，使用验电笔确认");
        k4.setAiSummary("电路跳闸排查：过载/短路/漏电→总开关→回路排查→电器测试→线路检查。操作前必须断电！");
        k4.setSourceType("text");
        k4.setCreatorName("系统预置");
        k4.setCreatedAt(now - 259200000); // 3天前
        k4.setUpdatedAt(now - 259200000);
        dao.insert(k4);

        // 知识5：水管维修
        KnowledgeEntity k5 = new KnowledgeEntity();
        k5.setId(UUID.randomUUID().toString());
        k5.setTitle("水管漏水紧急处理");
        k5.setRawContent("水管漏水的紧急处理方法：\n\n" +
                "1. 关闭总阀：立即关闭家中总水阀\n" +
                "2. 临时止漏：使用防水胶带或管夹临时封堵\n" +
                "3. 排空余水：打开水龙头排空管道余水\n" +
                "4. 检查漏点：确定漏水位置（接头、管身、阀门）\n" +
                "5. 修复方案：\n" +
                "   - 接头漏水：重新缠绕生料带或更换接头\n" +
                "   - 管身漏水：更换水管\n" +
                "   - 阀门漏水：更换阀门");
        k5.setAiSummary("水管漏水紧急处理：关总阀→临时止漏→排余水→找漏点→修复。接头漏水最常见，重新缠生料带即可。");
        k5.setSourceType("text");
        k5.setCreatorName("系统预置");
        k5.setCreatedAt(now - 345600000); // 4天前
        k5.setUpdatedAt(now - 345600000);
        dao.insert(k5);

        Log.d(TAG, "预置了5条知识库数据");
    }

    /**
     * 预置工单数据
     */
    private void seedWorkOrderData() {
        WorkOrderDao dao = database.workOrderDao();

        long now = System.currentTimeMillis();

        // 工单1：空调维修（待接单）
        WorkOrderEntity w1 = new WorkOrderEntity();
        w1.setOrderNo("GYT20260629001");
        w1.setTitle("空调不制冷维修");
        w1.setWorkType("空调维修");
        w1.setDescription("客户反映客厅空调开机后不制冷，已使用3年，从未清洗过过滤网");
        w1.setAddress("北京市朝阳区幸福小区12号楼3单元502");
        w1.setContactName("张先生");
        w1.setContactPhone("13800138001");
        w1.setStatus("PENDING");
        w1.setAppointmentTime(now + 3600000); // 1小时后
        w1.setAiPrediction("可能原因：过滤网积尘严重、制冷剂不足、压缩机老化");
        w1.setAiTools("工具：螺丝刀、压力表、制冷剂\n配件：过滤网、制冷剂");
        w1.setCreatedAt(now);
        w1.setUpdatedAt(now);
        dao.insert(w1);

        // 工单2：洗衣机维修（待接单）
        WorkOrderEntity w2 = new WorkOrderEntity();
        w2.setOrderNo("GYT20260629002");
        w2.setTitle("洗衣机不排水");
        w2.setWorkType("洗衣机维修");
        w2.setDescription("洗衣机洗涤完成后不排水，水留在桶内无法取出");
        w2.setAddress("北京市海淀区中关村大街88号");
        w2.setContactName("李女士");
        w2.setContactPhone("13900139002");
        w2.setStatus("PENDING");
        w2.setAppointmentTime(now + 7200000); // 2小时后
        w2.setAiPrediction("可能原因：排水管堵塞、排水泵故障、过滤器积垢");
        w2.setAiTools("工具：钳子、螺丝刀\n配件：排水泵（备用）");
        w2.setCreatedAt(now);
        w2.setUpdatedAt(now);
        dao.insert(w2);

        // 工单3：冰箱维修（已接单）
        WorkOrderEntity w3 = new WorkOrderEntity();
        w3.setOrderNo("GYT20260629003");
        w3.setTitle("冰箱冷藏室结冰");
        w3.setWorkType("冰箱维修");
        w3.setDescription("冰箱冷藏室内壁结冰，食物被冻住，门封条似乎有些变形");
        w3.setAddress("北京市西城区长安街10号");
        w3.setContactName("王大爷");
        w3.setContactPhone("13700137003");
        w3.setStatus("ACCEPTED");
        w3.setAppointmentTime(now - 1800000); // 已过30分钟
        w3.setAiPrediction("可能原因：门封条老化、温控器故障、排水孔堵塞");
        w3.setAiTools("工具：温度计、吹风机\n配件：门封条、温控器（备用）");
        w3.setCreatedAt(now - 3600000);
        w3.setUpdatedAt(now - 1800000);
        dao.insert(w3);

        Log.d(TAG, "预置了3条工单数据");
    }

    /**
     * 强制重新预置数据（用于测试）
     */
    public void forceSeed() {
        android.content.SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_SEEDED, false).apply();
        seedIfNeeded();
    }
}
