package com.project.system.service;

import com.project.system.entity.Application;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.List;

public interface QualityService {
    void submitCreditApplication(String type, String title, String desc, MultipartFile file) throws IOException;
    void submitLeaveApplication(Application app);
    List<Application> getManagedApplications();
    // 在 interface QualityService 中添加
    void sendNotificationToManagedClasses(String title, String content);
    void reviewApplication(Long id, String status);
}