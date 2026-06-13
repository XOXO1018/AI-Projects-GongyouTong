# 工友通 App - 问题诊断与修复指南（已更新 2026-04-11）

## ✅ 已修复问题总览

| 功能 | 问题 | 状态 | 修复方案 |
|------|------|------|----------|
| AI 日期解析 | Ollama 提示词过于简化，无法正确处理复杂日期 | ✅ 已修复 | 重写提示词，添加详细规则说明和示例 |
| AI 日期解析 | 本地正则不支持"大后天"、"下周X"、"下个月"等 | ✅ 已修复 | 添加 WEEKDAY_PATTERN、NEXT_WEEKDAY_PATTERN 等正则 |
| AI 日期解析 | 无法正确处理跨年日期（12月说1月） | ✅ 已修复 | 添加智能年份推断逻辑 |
| AI 日期解析 | 缺少时间验证机制（如2月30日） | ✅ 已修复 | 添加 parseAndValidateTime() 和 isValidDate() 方法 |
| AI 时间解析 | 不支持"一刻"、"三刻"等口语化表达 | ✅ 已修复 | 添加 TIME_QUARTER 正则和处理逻辑 |
| AI 提醒生成 | 提示词过于通用，缺乏针对性 | ✅ 已修复 | 重写提示词，提取设备类型并提供设备特定建议 |
| AI 提醒生成 | 工作类型匹配不够精确 | ✅ 已修复 | 添加 WORK_TYPE_MAPPING 和优先级排序 |
| Ollama AI | 详情页"重新生成"调用方式错误 | ✅ 已修复 | 新增 `generateReminderAsync()` 专用接口 |
| Ollama AI | 提醒提示词不精确 | ✅ 已修复 | 新增专属工作提醒提示词 |
| 百度地图 | SDKInitializer 重复初始化 | ✅ 已修复 | 迁移到 Application 类统一初始化 |
| 百度地图 | GeoCode 缺少城市参数 | ✅ 已修复 | 定位后自动获取城市名传入 GeoCodeOption |
| 数据列表 | 创建日程后列表重复显示 | ✅ 已修复 | 移除多余的 scheduleList.add(0, schedule) |
| 网络安全 | 明文 HTTP 被阻止 | ✅ 已修复 | network_security_config.xml |

---

## 🔧 修复详情

### 1. Ollama AI 提醒生成修复

**根本原因**：`ScheduleDetailActivity.refreshAiReminder()` 错误地调用了 `parseScheduleFromText()`（这个方法是用来**解析新日程**的），把 `title + workType` 当输入文本解析，会重新试图提取日期时间，输出的提醒内容完全偏离实际工作需求。

**修复方案**：
- 在 `VivoAiService.java` 新增 `generateReminderAsync()` 方法和 `ReminderCallback` 接口
- 专用提示词让模型输出"工作准备清单"而非"日程解析JSON"
- `ScheduleDetailActivity` 改为调用新接口

### 2. 百度地图 SDK 初始化修复

**根本原因**：每次打开 `BaiduNavigationActivity` 都执行 `SDKInitializer.initialize()`，重复初始化会导致内部状态混乱甚至 crash。正确做法是**只在 Application.onCreate() 调用一次**。

**修复方案**：
- 新建 `GongyouTongApplication.java`，在 `onCreate()` 中统一初始化百度地图 SDK（含 `setAgreePrivacy` + `initialize` + `setCoordType`）
- 在 `AndroidManifest.xml` 注册：`android:name=".GongyouTongApplication"`
- `BaiduNavigationActivity` 中只保留 `setAgreePrivacy`（幂等安全，不影响）

### 3. 地理编码失败修复

**根本原因**：`GeoCodeOption.address()` 未设置 `.city()` 参数，百度地图要求指定城市才能准确解析地址，否则在全国范围内检索会大概率返回无结果或错误结果。

**修复方案**：
- 定位回调中读取 `bdLocation.getCity()` 赋值给 `currentCityName`
- `searchDestination()` 中通过 `geoCodeOption.city(currentCityName)` 传入

### 4. 数据列表重复 Bug 修复

**根本原因**：`handleSend()` 中先 `scheduleList.addAll(loadSchedulesFromDb())`（数据库已含新记录），再 `scheduleList.add(0, schedule)` 手动追加，同一条日程出现两次。

**修复方案**：移除手动追加，直接用 `loadSchedulesFromDb()` 重新加载即可。

---

## 🚨 百度地图 AK 安全码配置（必须）

百度地图 SDK 在真机运行时需要 AK 与包名/SHA1 签名匹配，否则地图显示灰色。

### 获取 Debug 签名 SHA1

```powershell
# Windows PowerShell 执行
keytool -list -v -keystore "$env:USERPROFILE\.android\debug.keystore" -alias androiddebugkey -storepass android -keypass android
```

复制输出中的 `SHA1:` 值。

### 在百度地图开放平台配置

1. 访问 https://lbsyun.baidu.com/ 并登录
2. 控制台 → 应用管理 → 创建应用（类型选 Android）
3. 填入：
   - **包名**：`com.gongyoutong.app`
   - **SHA1**：上面命令获取的值
4. 获取新 AK，替换 `AndroidManifest.xml` 中的 `android:value`
5. 同步替换 `Config.java` 中的 `BAIDU_MAP_AK`

> ⚠️ **Release 包**需要额外配置 Release 签名的 SHA1（见 `build.gradle.kts` 中的 signingConfigs.release），调试和发布需要分别配置或同一 AK 配置两个 SHA1。

---

## 🤖 Ollama 本地大模型配置

### 当前配置（Config.java）
- **地址**：`http://10.50.80.97:11434`
- **模型**：`deepseek-r1:8b`
- **USE_LOCAL_AI**：`true`

### 验证 Ollama 是否可被手机访问

在**电脑命令行**执行：
```bash
curl http://10.50.80.97:11434/api/tags
```

如返回 JSON 模型列表，说明 Ollama 正常。

### 确保 Ollama 监听外网

Ollama 默认只监听 `127.0.0.1`，需设置为监听全部网卡：

**Windows（推荐方式）：**
```powershell
# 设置用户级环境变量后重启 Ollama
[Environment]::SetEnvironmentVariable("OLLAMA_HOST", "0.0.0.0", "User")
# 然后重新打开终端并运行：
ollama serve
```

**开放防火墙端口：**
```powershell
netsh advfirewall firewall add rule name="Ollama_11434" dir=in action=allow protocol=tcp localport=11434
```

### 下载模型

```bash
# deepseek-r1 8B（约 5GB，推理能力强）
ollama pull deepseek-r1:8b

# 轻量替代（约 2GB，速度更快）
ollama pull qwen2.5:3b
```

---

## 📱 完整测试流程

### 测试 1：AI 创建日程
```
输入：3月30日上午十点去幸福小区安装热水器
预期：
1. 显示"正在调用 Ollama AI..."
2. 自动跳转到日程详情页
3. AI 提醒卡片显示个性化安装工具清单
4. 右上角标注"✓ 基于 deepseek-r1:8b 生成"
```

### 测试 2：重新生成 AI 提醒
```
操作：在详情页点击"重新生成"按钮
预期：
1. 显示进度条
2. 调用 Ollama 生成针对该工作类型的提醒
3. 更新内容，不再是通用模板
```

### 测试 3：导航
```
操作：点击"开始导航"按钮
预期：
1. 跳转到 BaiduNavigationActivity
2. 地图正常显示（非灰色）
3. 自动定位当前位置
4. 地理编码目的地址并在地图标注
5. 自动规划驾车路线（橙色路线）
6. 底部显示路程/时间信息
```

---

## ❓ 常见问题

| 问题 | 原因 | 解决 |
|------|------|------|
| 地图显示灰色/白屏 | AK 安全码未配置 | 按上面步骤配置 SHA1 |
| AI 提醒内容是固定模板 | Ollama 不可用，已降级本地 | 检查网络/Ollama 服务 |
| Ollama 连接超时 | 未在同一网络，或端口被防火墙拦截 | 检查 IP + 防火墙规则 |
| 路线规划失败 | 地理编码未返回坐标 | 确认 AK 有效 + 地址不要太模糊 |
| 首页日程出现重复 | 旧 Bug，已修复 | 重新构建即可 |
