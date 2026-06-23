import requests
import json
import uuid
import time

# API 配置
url = "https://api-ai.vivo.com.cn/api/v1/image_generation"
app_key = "sk-xuanji-2026062497-YmpCQXZQeWVkSU9Kd2JaaQ=="

# 构建请求
request_id = str(uuid.uuid4())
system_time = int(time.time())

full_url = f"{url}?module=aigc&request_id={request_id}&system_time={system_time}"

headers = {
    "Content-Type": "application/json",
    "Authorization": f"Bearer {app_key}"
}

# 测试不同的 size 参数
test_cases = [
    {"size": "2K", "desc": "2K格式"},
    {"size": "2048x2048", "desc": "2048x2048格式"},
]

for test in test_cases:
    print(f"\n=== 测试: {test['desc']} ===")
    
    payload = {
        "model": "Doubao-Seedream-4.5",
        "prompt": "一只可爱的猫咪",
        "parameters": {
            "size": test["size"]
        }
    }
    
    print(f"Payload: {json.dumps(payload, ensure_ascii=False, indent=2)}")
    
    try:
        response = requests.post(full_url, headers=headers, json=payload, timeout=120)
        result = response.json()
        print(f"Status Code: {response.status_code}")
        print(f"Response: {json.dumps(result, ensure_ascii=False, indent=2)}")
        
        if result.get("code") == 0:
            print("✅ 成功!")
            break
        else:
            print(f"❌ 失败: {result.get('message')}")
    except Exception as e:
        print(f"Error: {e}")