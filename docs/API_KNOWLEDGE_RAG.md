# Knowledge / RAG API 文档

本文档说明 AIGen Studio 当前已提供的知识库摄入、检索和缓存监控接口。

## 基础信息

- Base URL：`/api`
- Controller 前缀：`/knowledge`
- Content-Type：`application/json`（文件上传接口除外）

## 1. 上传知识文件

- 方法：`POST`
- 路径：`/knowledge/upload`
- 请求类型：`multipart/form-data`
- 参数：
  - `file`（必填）：知识文件，支持 Markdown/PDF
  - `source`（可选）：来源标记
  - `conversationId`（可选）：会话 ID

示例：

```bash
curl -X POST "http://localhost:8080/api/knowledge/upload?source=manual&conversationId=9" \
  -F "file=@./docs/需求说明.md"
```

响应示例：

```json
{
  "segmentCount": 3,
  "segmentIds": ["seg-1", "seg-2", "seg-3"]
}
```

## 2. 摄入文本知识

- 方法：`POST`
- 路径：`/knowledge/text`
- 请求体：

```json
{
  "content": "# 订单模块\n\n支持订单创建、支付、取消。",
  "metadata": {
    "source": "spec",
    "domain": "order"
  }
}
```

响应示例：

```json
{
  "segmentCount": 2,
  "segmentIds": ["seg-a", "seg-b"]
}
```

## 3. 检索知识

- 方法：`GET`
- 路径：`/knowledge/search`
- Query 参数：
  - `q`（必填）：查询词
  - `maxResults`（可选，默认 `5`）：返回条数
  - `conversationId`（可选）：指定会话语义记忆检索

示例：

```bash
curl "http://localhost:8080/api/knowledge/search?q=订单&maxResults=5"
```

响应示例：

```json
[
  {
    "score": 0.88,
    "text": "订单核心流程",
    "metadata": {
      "conversationId": "9",
      "source": "spec"
    }
  }
]
```

## 4. 查询缓存指标

- 方法：`GET`
- 路径：`/knowledge/cache/stats`

响应字段：

- `hits`：缓存命中次数
- `misses`：缓存未命中次数
- `evictions`：缓存淘汰次数
- `size`：当前缓存条目数

## 5. 查询缓存告警状态

- 方法：`GET`
- 路径：`/knowledge/cache/alerts`

响应字段：

- `enabled`：是否启用告警
- `triggered`：当前是否触发告警
- `totalRequests`：总请求数（hits + misses）
- `missRate`：当前 miss rate
- `hits` / `misses` / `evictions` / `size`：缓存统计
- `minRequests`：触发告警判定的最小请求数
- `maxMissRate`：告警阈值
- `message`：告警状态说明

## 6. 查询检索性能统计

- 方法：`GET`
- 路径：`/knowledge/perf/stats`

响应字段：

- `totalSearches`：累计检索次数
- `avgLatencyMs`：平均检索耗时（毫秒）
- `p95LatencyMs`：P95 检索耗时（毫秒）
- `maxLatencyMs`：最大检索耗时（毫秒）
- `latencyThresholdMs`：耗时阈值
- `minSamples`：阈值判定最小样本数
- `sampleCount`：当前样本窗口内数据量
- `thresholdBreached`：是否超过阈值

## 配置项（`application.yml`）

```yaml
aigen:
  rag:
    search-cache-enabled: true
    search-cache-ttl-seconds: 120
    search-cache-max-size: 500
    alert-enabled: true
    alert-min-requests: 20
    alert-max-miss-rate: 0.60
    alert-log-cooldown-seconds: 300
    perf-latency-threshold-ms: 100
    perf-sample-size: 200
    perf-min-samples: 30
    embedding-batch-size: 16
    conversation-index-timeout-ms: 1500
```
