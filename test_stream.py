import urllib.request
import json
import os

# 从 Config.java 读取配置
config_path = os.path.join(os.path.dirname(__file__), "app", "src", "main", "java", "com", "gongyoutong", "app", "Config.java")
api_key = None
with open(config_path, 'r', encoding='utf-8') as f:
    for line in f:
        if 'VIVO_APP_KEY' in line and '=' in line:
            api_key = line.split('"')[1] if '"' in line else None
            break

if not api_key:
    print("未找到 API Key")
    exit(1)

url = "https://api-ai.vivo.com.cn/v1/chat/completions"
headers = {
    "Content-Type": "application/json",
    "Authorization": f"Bearer {api_key}",
    "Accept": "text/event-stream"
}

data = {
    "model": "Volc-DeepSeek-V3.2",
    "messages": [
        {"role": "system", "content": "你是智能助手"},
        {"role": "user", "content": "你好，简单介绍一下自己"}
    ],
    "stream": True,
    "temperature": 0.7,
    "max_tokens": 100
}

req = urllib.request.Request(url, data=json.dumps(data).encode('utf-8'), headers=headers, method='POST')

print("=== 测试蓝心大模型流式输出 ===\n")
try:
    with urllib.request.urlopen(req, timeout=30) as response:
        print(f"Status: {response.status}")
        print(f"Content-Type: {response.headers.get('Content-Type')}")
        print("\n--- 流式数据 ---\n")

        # 读取前 20 行看看格式
        for i in range(30):
            line = response.readline().decode('utf-8').strip()
            if not line:
                continue
            print(f"Line {i}: {line[:200]}")  # 只打印前200字符
            if line.startswith("data: "):
                data_str = line[6:]
                if data_str == "[DONE]":
                    print("\n[DONE] received")
                    break
                try:
                    json_data = json.loads(data_str)
                    choices = json_data.get("choices", [])
                    if choices:
                        choice = choices[0]
                        delta = choice.get("delta", {})
                        content = delta.get("content", "")
                        if content:
                            print(f"  -> content: {content}")
                        else:
                            print(f"  -> delta keys: {delta.keys() if delta else 'None'}")
                    else:
                        print(f"  -> no choices")
                except Exception as e:
                    print(f"  -> parse error: {e}")

except Exception as e:
    print(f"Error: {e}")
