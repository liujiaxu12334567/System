# 使用 JDK 17 基础镜像
FROM openjdk:17-jdk-slim
# 设置工作目录
WORKDIR /app
# 复制构建好的 jar 包 (请先执行 mvn package)
COPY target/*.jar app.jar
# 暴露端口
EXPOSE 8080
# 启动命令
ENTRYPOINT ["java", "-jar", "app.jar"]