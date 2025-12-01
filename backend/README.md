# KnoHub Backend

Spring Boot 后端服务，为课程资料共享平台提供 API 支持。

## 功能特性

- **文件上传**：支持多种文件格式上传
- **软删除**：删除文件/文件夹时使用序号标记，确保同名文件重新上传不报错
- **文件夹管理**：创建、删除文件夹（支持嵌套）
- **资源管理**：课程资料的 CRUD 操作

## API 端点

### 资源管理
| 方法 | 端点 | 描述 |
|------|------|------|
| GET | `/api/resources` | 获取所有资源 |
| GET | `/api/resources/{id}` | 获取单个资源 |
| GET | `/api/resources/type/{type}` | 按类型获取资源 |
| GET | `/api/resources/search?keyword=xxx` | 搜索资源 |
| POST | `/api/resources` | 创建资源 |
| PUT | `/api/resources/{id}` | 更新资源 |
| DELETE | `/api/resources/{id}` | 删除资源 |

### 文件管理
| 方法 | 端点 | 描述 |
|------|------|------|
| GET | `/api/files/{resourceId}` | 获取资源的文件列表 |
| POST | `/api/files/{resourceId}/upload?folderId=xxx` | 上传文件 |
| POST | `/api/files/{resourceId}/upload/batch?folderId=xxx` | 批量上传文件 |
| DELETE | `/api/files/{fileId}` | 删除文件（软删除） |
| POST | `/api/files/{resourceId}/folders` | 创建文件夹 |
| DELETE | `/api/files/folders/{folderId}` | 删除文件夹（软删除） |
| GET | `/api/files/{resourceId}/download/{filename}` | 下载文件 |

## 软删除机制

当删除文件或文件夹时：
1. 不会真正删除记录，而是标记 `deleted = true`
2. 分配递增的 `deleteSequence` 序号
3. 文件名修改为 `原名_deleted_序号`
4. 物理文件也会重命名

这样设计的好处：
- 可以重新上传同名文件
- 支持数据恢复（如需要）
- 保留删除历史

## 运行项目

### 前置要求
- JDK 17+
- Maven 3.6+
- 可选：本地 PostgreSQL 实例（默认使用内置的文件型 H2 数据库）

### 启动命令

```bash
cd backend
mvn spring-boot:run
```

或者打包后运行：

```bash
mvn clean package -DskipTests
java -jar target/backend-0.0.1-SNAPSHOT.jar
```

### 配置说明

配置文件位于 `src/main/resources/application.yml`：

```yaml
server:
  port: 8080            # 服务端口

spring:
  servlet:
    multipart:
      max-file-size: 50MB    # 单文件大小限制
      max-request-size: 50MB # 请求大小限制

file:
  upload-dir: ./uploads  # 文件存储目录
```

## 数据库

默认使用文件型 H2 数据库，数据文件位于 `backend/data/knohub.mv.db`，开箱即用（不再预置示例数据）。可以通过环境变量切换到 PostgreSQL：

```
DB_URL=jdbc:postgresql://<host>:5432/knohub
DB_USERNAME=<username>
DB_PASSWORD=<password>
DB_DRIVER=org.postgresql.Driver
DB_DIALECT=org.hibernate.dialect.PostgreSQLDialect
```

JPA `ddl-auto=update`，应用启动时不会再自动插入示例数据，数据库为空由用户自行创建资源。

本地快速启动 PostgreSQL（Docker）：
```bash
./scripts/postgres_up.sh
# 如果需要自定义端口/用户/密码等，可覆盖环境变量：
# HOST_PORT=15432 DB_NAME=knohub DB_USER=postgres DB_PASSWORD=postgres ./scripts/postgres_up.sh
```
首次运行会自动初始化数据库并将数据保存在 `backend/postgres-data`，后续运行会复用该目录中的数据。

## 日志

- 采用 Logback，自定义配置位于 `src/main/resources/logback-spring.xml`。
- 控制台 + 滚动文件输出：`logs/knohub-backend.log`，错误级别额外写入 `logs/knohub-backend-error.log`，自动按天/大小归档到 `logs/archive/`。
- 每条请求日志包含 traceId（可通过请求头 `X-Request-Id` 传入），记录方法、URL、状态码、耗时、IP、UA；状态码 4xx/5xx 会提升日志级别（WARN/ERROR）。
- 可通过调整 `logback-spring.xml` 中的 `MAX_FILE_SIZE`/`MAX_HISTORY` 等属性控制滚动策略。

## 与前端联调

1. 启动后端（端口 8080）
2. 启动前端（端口 5173）
3. 前端会自动请求后端 API

CORS 已配置允许前端开发服务器访问。
