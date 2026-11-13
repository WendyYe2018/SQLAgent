SQL Agent 本地启动说明

1. 启动 Ollama
   ollama serve

2. 下载 embedding 模型（用于 schema 语义检索）
   ollama pull nomic-embed-text

3. 启动 Qdrant
   docker run -d --name qdrant -p 6333:6333 qdrant/qdrant

4. 先测试 Ollama embedding 是否可用
   curl.exe -i -X POST "http://localhost:11434/api/embed" -H "Content-Type: application/json" -d '{"model":"nomic-embed-text","input":"hello world"}'

5. 启动 Spring Boot 项目
   mvn spring-boot:run

5. 访问应用
   http://localhost:8083

配置说明
- 应用配置文件：src/main/resources/application.yml
- Ollama 地址：ollama.base-url
- Ollama 生成 SQL 模型：ollama.model
- Ollama embedding 模型：ollama.embedding-model
- Qdrant 地址：qdrant.base-url

当前 RAG 设计
- 一个 database 一个 collection
- database 名称：Taobao
- collection 名称：Taobao
- schema 注释资源目录：src/main/resources/rag/
  - taobao.txt：数据库级说明
  - orders.txt：订单表说明
  - customers.txt：客户表说明

启动后行为
- 应用启动时自动尝试把 Taobao 数据库 schema 通过 embedding 写入 Qdrant 的 Taobao collection
- 如果 Ollama 或 Qdrant 没启动，应用仍会继续运行，但向量写入会失败并记录日志

检查向量库
- 查看所有集合：curl http://localhost:6333/collections
- 查看 Taobao 集合：curl http://localhost:6333/collections/Taobao
- 查看集合里的点：curl "http://localhost:6333/collections/Taobao/points?limit=10&with_payload=true"
