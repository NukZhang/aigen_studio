# SDK 包

本包用于存放所有第三方接口和 SDK 的适配器实现。

## 包结构

```
com.aigen.studio.sdk/
├── ICodingService.java          # AI 编码服务接口（核心抽象）
└── iflow/                       # iFlow SDK 实现包
    └── IFlowClientHelper.java   # iFlow SDK 适配器实现
```

## 设计原则

### 面向接口编程

- `ICodingService` 是核心抽象接口，定义了 AI SDK 的标准能力
- 具体实现（如 `IFlowClientHelper`）通过实现接口来提供 SDK 功能
- 业务服务（如 `PromptTaskService`）只依赖 `ICodingService` 接口，不依赖具体实现

### 可替换性

- 未来可以轻松添加其他 SDK 实现（如 OpenAI、Claude 等）
- 只需实现 `ICodingService` 接口，无需修改业务代码
- 通过 Spring 依赖注入可以动态切换实现

### 职责分离

- `ICodingService`：定义 SDK 接口规范
- `IFlowClientHelper`：iFlow SDK 的具体实现和适配
- `PromptTaskService`：提示词任务处理（业务服务）

## 核心接口

### ICodingService

```java
public interface ICodingService {
    // 执行 AI 任务（带消息处理器）
    void executeTask(Path workDir, String prompt, MessageHandler handler);
    
    // 消息处理器接口
    interface MessageHandler {
        void onText(String text);
        void onToolCall(String toolName, String status);
        void onToolResult(String content);
        void onTaskFinish(String stopReason);
        void onError(Throwable error);
    }
}
```

## 使用示例



### 执行 AI 任务

```java
@Service
@RequiredArgsConstructor
public class PromptTaskService {
    private final ICodingService codingService;
    
    public void generateCode(String irContent, Path outputPath) {
        codingService.executeTask(
            outputPath,
            "生成代码的提示词",
            new ICodingService.MessageHandler() {
                @Override
                public void onText(String text) {
                    System.out.println("AI: " + text);
                }
                
                // ... 其他回调方法
            }
        );
    }
}
```

## 扩展新 SDK

如果要添加新的 AI SDK 实现（例如 OpenAI），只需：

1. 创建新的包：`sdk/openai/`
2. 创建实现类：`OpenAIClientHelper implements ICodingService`
3. 实现 `executeTask()` 方法
4. 在 Spring 配置中指定使用哪个实现

无需修改任何业务代码！

## 未来规划

- 添加 OpenAI SDK 实现
- 添加 Claude SDK 实现
- 添加其他国产大模型 SDK 实现
- 支持多模型并行调用
- 支持模型性能监控和自动切换