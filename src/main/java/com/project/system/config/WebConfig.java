package com.project.system.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    // 读取 application.properties 中的 file.upload-dir
    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 获取上传目录的绝对路径
        // 注意：file: 协议后需要跟绝对路径，并且以 / 结尾
        String path = "file:" + new File(uploadDir).getAbsolutePath() + File.separator;

        // 映射 URL /uploads/** 到本地磁盘路径
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(path);

        System.out.println("静态资源映射已配置: /uploads/** -> " + path);
    }
}