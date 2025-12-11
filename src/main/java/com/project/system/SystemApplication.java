package com.project.system;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class SystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(SystemApplication.class, args);
	}

	// ★★★ 新增：启动后打印数据库连接信息 ★★★
	@Bean
	public CommandLineRunner printDbInfo(@Value("${spring.datasource.url}") String dbUrl) {
		return args -> {
			System.out.println("\n=========================================================");
			System.out.println("🔥 当前连接的数据库 URL: " + dbUrl);

			if (dbUrl.contains("3306")) {
				System.out.println("👉 正在使用端口 [3306] (通常是本地 MySQL)");
			} else if (dbUrl.contains("3308")) {
				System.out.println("👉 正在使用端口 [3308] (通常是 Docker 映射端口)");
			}

			System.out.println("=========================================================\n");
		};
	}
}