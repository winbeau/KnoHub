# KnoHub

<div align="center">

<img src="frontend/src/assets/logo.svg" alt="KnoHub Logo" width="120">

**课程资料 · 技术文档 · 校园信息 — 一站式知识管理平台**

[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3.5-brightgreen.svg)](https://vuejs.org/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.9-blue.svg)](https://www.typescriptlang.org/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

</div>

---

## 项目简介

KnoHub 是一个面向校园场景的知识库管理系统，采用前后端分离架构，提供资源分类管理、文件上传与预览、文件树组织等功能。系统支持 PDF 和 Word 文档在线预览，并具备灵活的文件组织与拖拽排序能力。

### 核心特性

- **模块化资源管理** — 课程资料 / 技术文档 / 校园信息 三大模块分类存储
- **资源卡片系统** — 快速创建、编辑、删除资源卡片，支持标签标记
- **文件树管理** — 树形目录结构，支持文件夹创建、重命名、拖拽排序
- **文档在线预览** — 支持 PDF（多页渲染）、DOC/DOCX、图片格式预览
- **智能缩放** — 文档预览支持缩放、自适应宽度，缩放值本地缓存
- **访客统计** — 实时显示活跃用户数
- **最近上传** — 主页展示最近更新的资源卡片

---

## 技术栈

### 后端
| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 17 | 运行环境 |
| Spring Boot | 3.x | Web 框架 |
| Spring Data JPA | - | 数据持久化 |
| H2 / PostgreSQL | - | 数据库（开发/生产） |
| Maven | 3.6+ | 构建工具 |

### 前端
| 技术 | 版本 | 说明 |
|------|------|------|
| Vue | 3.5 | 渐进式框架 |
| TypeScript | 5.9 | 类型安全 |
| Vite | 7.x | 构建工具 |
| Tailwind CSS | 4.x | 原子化 CSS |
| pdfjs-dist | 3.11 | PDF 渲染 |
| docx-preview | 0.3 | Word 文档预览 |
| Font Awesome | 7.x | 图标库 |

---

## 项目结构

```
KnoHub/
├── backend/                    # Spring Boot 后端
│   ├── src/main/java/com/knohub/backend/
│   │   ├── controller/         # REST API 控制器
│   │   ├── service/            # 业务逻辑层
│   │   ├── repository/         # 数据访问层
│   │   ├── model/              # 实体模型
│   │   ├── dto/                # 数据传输对象
│   │   └── config/             # 配置类
│   ├── src/main/resources/
│   │   └── application.yml     # 应用配置
│   ├── uploads/                # 上传文件存储（勿提交）
│   ├── data/                   # H2 数据库文件（勿提交）
│   └── target/                 # Maven 构建产物
│
├── frontend/                   # Vue 3 前端
│   ├── src/
│   │   ├── components/         # Vue 组件
│   │   │   ├── FileTreeItem.vue    # 文件树组件
│   │   │   ├── PdfPreview.vue      # PDF 预览
│   │   │   ├── DocPreview.vue      # Word 预览
│   │   │   ├── UploadModal.vue     # 上传弹窗
│   │   │   ├── ResourceCard.vue    # 资源卡片
│   │   │   └── ...
│   │   ├── api/                # API 客户端
│   │   ├── types/              # TypeScript 类型
│   │   ├── data/               # 静态数据配置
│   │   └── assets/             # 静态资源
│   ├── public/                 # 公共静态文件
│   └── dist/                   # 构建产物（勿提交）
│
├── scripts/                    # 实用脚本
│   ├── postgres_up.sh          # PostgreSQL Docker 启动
│   ├── postgres_env.sh         # 环境变量配置
│   └── clear_postgres_data.sql # 数据清理脚本
│
└── README.md
```

---

## 快速开始

### 前置要求

- **JDK 17** — [下载 OpenJDK](https://adoptium.net/)
- **Maven 3.6+** — [下载 Maven](https://maven.apache.org/download.cgi)
- **Node.js 18+** — [下载 Node.js](https://nodejs.org/)
- **pnpm** — `corepack enable` 或 `npm install -g pnpm`
- **Docker**（可选）— 用于本地 PostgreSQL

### 1. 克隆项目

```bash
git clone https://github.com/your-username/KnoHub.git
cd KnoHub
```

### 2. 启动后端

```bash
cd backend

# 使用内置 H2 数据库（开发模式）
mvn spring-boot:run

# 后端默认运行在 http://localhost:8080
```

### 3. 启动前端

```bash
cd frontend

# 安装依赖
pnpm install

# 启动开发服务器
pnpm dev --host

# 前端默认运行在 http://localhost:5173
```

### 4. 访问应用

打开浏览器访问 `http://localhost:5173`，即可使用 KnoHub。

---

## 数据库配置

### 开发环境（H2，默认）

默认使用文件型 H2 数据库，无需额外配置，数据存储在 `backend/data/` 目录。

### 生产环境（PostgreSQL）

#### 方式一：Docker 快速启动

```bash
# 启动 PostgreSQL 容器
sudo ./scripts/postgres_up.sh

# 导出环境变量
source ./scripts/postgres_env.sh

# 启动后端
cd backend && mvn spring-boot:run
```

#### 方式二：手动配置

设置以下环境变量后启动后端：

```bash
export DB_URL=jdbc:postgresql://localhost:5432/knohub
export DB_DRIVER=org.postgresql.Driver
export DB_USERNAME=knohub
export DB_PASSWORD=your_password
export DB_DIALECT=org.hibernate.dialect.PostgreSQLDialect

mvn spring-boot:run
```

---

## API 接口

### 资源管理 `/api/resources`

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | `/api/resources` | 获取所有资源 |
| GET | `/api/resources/{id}` | 获取单个资源 |
| GET | `/api/resources/type/{type}` | 按类型获取资源 |
| GET | `/api/resources/search?keyword=` | 搜索资源 |
| POST | `/api/resources` | 创建资源 |
| PUT | `/api/resources/{id}` | 更新资源 |
| DELETE | `/api/resources/{id}` | 删除资源 |

### 文件管理 `/api/files`

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | `/api/files/{resourceId}` | 获取资源文件列表 |
| POST | `/api/files/{resourceId}/upload` | 上传文件 |
| POST | `/api/files/{resourceId}/folders` | 创建文件夹 |
| PUT | `/api/files/{fileId}/rename` | 重命名文件/文件夹 |
| POST | `/api/files/reorder` | 拖拽排序 |
| DELETE | `/api/files/{fileId}` | 删除文件（软删除） |
| DELETE | `/api/files/folders/{folderId}` | 删除文件夹（软删除） |
| GET | `/api/files/{fileId}/html` | 获取 DOC 预览 HTML |

### 统计 `/api/metrics`

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | `/api/metrics/active-users` | 获取活跃用户数 |

---

## 生产构建

### 后端打包

```bash
cd backend
mvn clean package -DskipTests

# 生成的 JAR 文件
java -jar target/backend-0.0.1-SNAPSHOT.jar
```

### 前端构建

```bash
cd frontend
pnpm build

# 构建产物在 dist/ 目录
# 可部署到 Nginx、Vercel、Netlify 等
```

---

## 配置说明

### 后端配置 `application.yml`

```yaml
server:
  port: 8080                    # 服务端口

spring:
  servlet:
    multipart:
      max-file-size: 50MB       # 单文件大小限制
      max-request-size: 50MB    # 请求大小限制

file:
  upload-dir: ./uploads         # 上传文件存储目录
```

### 环境变量

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `DB_URL` | 数据库连接 URL | H2 文件数据库 |
| `DB_DRIVER` | JDBC 驱动类 | `org.h2.Driver` |
| `DB_USERNAME` | 数据库用户名 | `sa` |
| `DB_PASSWORD` | 数据库密码 | 空 |
| `DB_DIALECT` | Hibernate 方言 | `org.hibernate.dialect.H2Dialect` |
| `VITE_API_BASE_URL` | 前端 API 地址 | 自动检测 |

---

## 开发规范

### 代码风格

- **Java**: 4 空格缩进，使用 Lombok 简化代码，控制器保持轻量，业务逻辑放 Service 层
- **前端**: Composition API + `<script setup>`，组件命名 PascalCase，状态命名 camelCase
- **API**: RESTful 风格，统一使用 `ApiResponse` 包装响应

### 提交规范

遵循 [Conventional Commits](https://www.conventionalcommits.org/)：

```
feat(frontend): add PDF preview component
fix(backend): resolve file upload issue
docs: update README
chore: upgrade dependencies
```

### 不应提交的文件

确保 `.gitignore` 包含以下内容：

```gitignore
# 依赖
node_modules/
.venv/

# 构建产物
dist/
target/

# 数据与上传
backend/data/
backend/uploads/
backend/postgres-data/
backend/logs/

# IDE
.idea/
*.iml
.vscode/

# 环境
.env
.env.local
```

---

## 常用命令

```bash
# 后端
cd backend && mvn spring-boot:run      # 启动开发服务器
cd backend && mvn test                  # 运行测试
cd backend && mvn clean package -DskipTests  # 打包

# 前端
cd frontend && pnpm install             # 安装依赖
cd frontend && pnpm dev --host          # 启动开发服务器
cd frontend && pnpm build               # 生产构建
cd frontend && pnpm preview             # 预览构建产物

# PostgreSQL
sudo ./scripts/postgres_up.sh           # 启动 PostgreSQL
source ./scripts/postgres_env.sh        # 导出环境变量
docker stop knohub-postgres             # 停止 PostgreSQL
```

---

## 功能截图

> 待补充：主页概览、资源卡片、文件树、PDF 预览等界面截图

---

## 路线图

- [ ] 用户认证与权限管理
- [ ] 全文搜索功能
- [ ] 资源收藏与分享
- [ ] Markdown 文档支持
- [ ] 移动端适配优化
- [ ] 深色模式

---

## 贡献指南

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'feat: add amazing feature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 提交 Pull Request

---

## 许可证

本项目采用 MIT 许可证 - 详见 [LICENSE](LICENSE) 文件

---

## 联系方式

如有问题或建议，欢迎提交 [Issue](https://github.com/your-username/KnoHub/issues)

---

<div align="center">

**⭐ 如果这个项目对你有帮助，请给一个 Star！**

</div>
