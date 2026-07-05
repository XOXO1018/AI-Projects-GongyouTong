package com.gongyoutong.app.workorder;

import com.gongyoutong.app.database.AppDatabase;
import com.gongyoutong.app.database.CustomerEntity;
import com.gongyoutong.app.database.WorkOrderDao;
import com.gongyoutong.app.database.WorkOrderEntity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 模拟派单服务
 * 生成模拟工单和客户数据，用于开发调试阶段
 */
public class MockDispatcher {

    /** 故障类型 */
    private static final String[] FAULT_TYPES = {
            "空调维修", "热水器维修", "冰箱维修", "洗衣机维修",
            "管道疏通", "油烟机清洗", "燃气灶维修", "电路检修",
            "水龙头更换", "马桶维修", "空调安装", "热水器安装"
    };

    /** 北京区域随机地址 */
    private static final String[] ADDRESSES = {
            "朝阳区望京SOHO T1", "海淀区中关村软件园二期",
            "丰台区芳城园一区", "西城区金融街甲15号",
            "东城区东直门外大街48号", "通州区新华西街58号",
            "大兴区亦庄经济开发区", "昌平区回龙观龙泽苑东区",
            "顺义区后沙峪镇", "石景山区古城大街",
            "朝阳区劲松南路1号", "海淀区五道口华联商厦"
    };

    /** 客户姓 */
    private static final String[] SURNAMES = {
            "张", "王", "李", "赵", "刘", "陈", "杨", "黄", "吴", "周"
    };

    /** 客户名 */
    private static final String[] GIVEN_NAMES = {
            "伟", "芳", "秀英", "敏", "强", "丽", "军", "洋", "勇", "静"
    };

    /** 北京大致经纬度范围 */
    private static final double LAT_MIN = 39.70;
    private static final double LAT_MAX = 40.10;
    private static final double LNG_MIN = 116.20;
    private static final double LNG_MAX = 116.70;

    private static final Random random = new Random();
    private static final AtomicInteger sequence = new AtomicInteger(0);

    /**
     * 生成模拟工单
     * @return 新的 WorkOrderEntity
     */
    public static WorkOrderEntity generateMockWorkOrder() {
        WorkOrderEntity entity = new WorkOrderEntity();

        // 工单编号：WO + YYYYMMDD + 4位序号
        String datePart = new SimpleDateFormat("yyyyMMdd", Locale.CHINA).format(new Date());
        String seqPart = String.format(Locale.CHINA, "%04d", sequence.incrementAndGet());
        entity.setOrderNo("WO" + datePart + seqPart);

        // 随机故障类型
        String faultType = FAULT_TYPES[random.nextInt(FAULT_TYPES.length)];
        entity.setTitle(faultType);
        entity.setWorkType(faultType.contains("安装") ? "安装服务" : "维修服务");

        // 随机描述
        entity.setDescription(faultType + "，客户反馈设备运行异常，需要上门检修");

        // 随机地址
        String address = ADDRESSES[random.nextInt(ADDRESSES.length)];
        entity.setAddress(address);

        // 随机经纬度（北京区域）
        entity.setLatitude(LAT_MIN + random.nextDouble() * (LAT_MAX - LAT_MIN));
        entity.setLongitude(LNG_MIN + random.nextDouble() * (LNG_MAX - LNG_MIN));

        // 随机客户
        String name = SURNAMES[random.nextInt(SURNAMES.length)]
                + GIVEN_NAMES[random.nextInt(GIVEN_NAMES.length)];
        entity.setContactName(name);
        entity.setContactPhone("1" + (3 + random.nextInt(7))
                + String.format(Locale.CHINA, "%08d", random.nextInt(100000000)));

        // 客户ID（用手机号模拟）
        entity.setCustomerId("CUST_" + entity.getContactPhone());

        // 状态：待接单
        entity.setStatus(WorkOrderStatus.PENDING.name());

        // 预约时间：未来 1~6 小时
        long appointmentTime = System.currentTimeMillis()
                + (1 + random.nextInt(6)) * 3600_000L;
        entity.setAppointmentTime(appointmentTime);

        // 时间戳
        long now = System.currentTimeMillis();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        return entity;
    }

    /**
     * 将工单存入数据库
     * @param entity 工单实体
     * @param dao    工单 DAO
     */
    public static void dispatchToDatabase(WorkOrderEntity entity, WorkOrderDao dao) {
        if (entity == null || dao == null) {
            return;
        }
        dao.insert(entity);
    }

    /**
     * 检查是否有待接单工单
     * @param dao 工单 DAO
     * @return true 表示有待接单工单
     */
    public static boolean hasPendingOrders(WorkOrderDao dao) {
        if (dao == null) {
            return false;
        }
        return !dao.getPendingOrders().isEmpty();
    }

    /**
     * 生成模拟客户
     * @return 新的 CustomerEntity
     */
    public static CustomerEntity generateMockCustomer() {
        CustomerEntity entity = new CustomerEntity();

        String phone = "1" + (3 + random.nextInt(7))
                + String.format(Locale.CHINA, "%08d", random.nextInt(100000000));
        entity.setId("CUST_" + phone);

        String name = SURNAMES[random.nextInt(SURNAMES.length)]
                + GIVEN_NAMES[random.nextInt(GIVEN_NAMES.length)];
        entity.setName(name);
        entity.setPhone(phone);

        String address = ADDRESSES[random.nextInt(ADDRESSES.length)];
        entity.setAddress(address);

        entity.setLatitude(LAT_MIN + random.nextDouble() * (LAT_MAX - LAT_MIN));
        entity.setLongitude(LNG_MIN + random.nextDouble() * (LNG_MAX - LNG_MIN));

        entity.setOrderCount(1 + random.nextInt(10));

        long now = System.currentTimeMillis();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        return entity;
    }
}
