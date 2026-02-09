# 图片预览 UI 生成提示词模板

> 维护说明：UI 生成提示词统一维护在本文件。代码只做变量替换与分段选择。
> 可用变量：`{{PROJECT_NAME}}`、`{{USER_REQUIREMENT}}`、`{{AI_UNDERSTANDING}}`。

<!-- TEMPLATE:PRIMARY -->
模板模式：PRIMARY

你是资深 UI 设计师和前端工程师。请根据以下上下文，生成一个可直接在浏览器打开的单文件 HTML UI 原型。

项目名称：{{PROJECT_NAME}}
用户需求：{{USER_REQUIREMENT}}
AI 理解：{{AI_UNDERSTANDING}}

创建一个移动端优先的页面，并严格满足：

## 1. 基础结构
- 完整 HTML（DOCTYPE、html、head、body）
- 内联 CSS（可选内联 JS），不依赖本地文件
- 最大宽度 480px，居中展示
- 包含清晰的头部、内容区、底部操作区（如适用）

## 2. 组件清单
- 至少包含：标题区、内容卡片区、操作按钮区、表单/输入交互区
- 组件命名清晰，结构语义化（section/main/nav/button/form 等）

## 3. 图片需求（关键）
- 头像：使用 DiceBear URL，例如 `https://api.dicebear.com/7.x/avataaars/svg?seed=Alice`
- 内容配图：必须使用 Unsplash URL，例如 `https://images.unsplash.com/photo-1490750967868-88aa4486c946?w=400&h=400&fit=crop`
- 图片网格布局必须包含以下类名并实际生效：
  - 单图：`.single { grid-template-columns: 1fr; }`
  - 双图：`.double { grid-template-columns: repeat(2, 1fr); }`
  - 多图：`.multiple { grid-template-columns: repeat(3, 1fr); }`
- 图片样式：`aspect-ratio: 1; object-fit: cover;`
- 页面中至少出现 1 个 `images.unsplash.com` 链接

## 4. 样式要求
- 使用 CSS 变量定义主题色
- 背景不能是纯空白，使用渐变或纹理背景
- 卡片圆角+阴影+hover 过渡动画
- 文本可读性高，对比度合规

## 5. 交互设计
- 按钮有 hover/active 反馈
- 至少一个可见图表区域（SVG 或 CSS 实现均可）
- 至少一个圆形头像区域

## 6. 输出要求
- 只输出完整 HTML 代码，不输出解释文本
- 第一行必须是：`<!-- 已调用并遵循 pencil-ui-design 规范（图片/背景/图表/头像） -->`
- 严禁调用任何工具、命令或技能；必须由 assistant 直接输出 HTML

<!-- /TEMPLATE:PRIMARY -->

<!-- TEMPLATE:COMPACT -->
模板模式：COMPACT

请输出单文件 HTML 原型，且只输出 HTML。

项目名称：{{PROJECT_NAME}}
用户需求：{{USER_REQUIREMENT}}
AI 理解：{{AI_UNDERSTANDING}}

硬性要求：
1. 完整 HTML + 内联 CSS，可直接浏览器打开。
2. 图片与头像必须使用真实 URL：
   - 头像：`https://api.dicebear.com/7.x/avataaars/svg?seed=...`
   - 配图：`https://images.unsplash.com/...`（至少 1 个）
3. 图片网格必须同时支持 `.single/.double/.multiple` 三种布局。
4. 页面必须有背景视觉效果、图表区域、圆形头像。
5. 第一行必须是：`<!-- 已调用并遵循 pencil-ui-design 规范（图片/背景/图表/头像） -->`
6. 不得输出解释；不得调用工具；直接输出完整 HTML。

<!-- /TEMPLATE:COMPACT -->

<!-- TEMPLATE:LEGACY -->
模板模式：LEGACY

根据下列信息生成静态 HTML UI 原型：
- 项目名称：{{PROJECT_NAME}}
- 用户需求：{{USER_REQUIREMENT}}
- AI 理解：{{AI_UNDERSTANDING}}

最低要求：
1. 输出完整 HTML（DOCTYPE/html/head/body）+ 内联 CSS。
2. 页面需有结构化模块、按钮、表单与导航元素。
3. 图片与头像必须是可访问真实地址：
   - DiceBear 头像 URL
   - Unsplash 配图 URL（至少 1 个 `images.unsplash.com`）
4. 图片展示包含 single/double/multiple 三种网格样式定义。
5. 保证背景、图表、图片、头像都可见。
6. 第一行必须是：`<!-- 已调用并遵循 pencil-ui-design 规范（图片/背景/图表/头像） -->`
7. 只输出 HTML，禁止输出命令和解释。

<!-- /TEMPLATE:LEGACY -->
