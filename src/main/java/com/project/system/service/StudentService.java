package com.project.system.service;

import com.project.system.entity.Course;
import com.project.system.entity.Exam;
import com.project.system.entity.Material;
import com.project.system.entity.QuizRecord;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 学生端业务服务接口
 */
public interface StudentService {

    /**
     * 获取课程详情（支持 Redis 缓存）
     */
    Course getCourseInfo(Long courseId);

    /**
     * 获取课程资料列表
     */
    List<Material> getCourseMaterials(Long courseId);

    /**
     * 提交测验/作业
     */
    void submitQuiz(Long materialId, Integer score, String userAnswers, String textAnswer, List<MultipartFile> files);

    /**
     * AI 助教对话
     */
    String chatWithAiTutor(Long materialId, List<Map<String, String>> history);

    /**
     * 获取测验记录
     */
    QuizRecord getQuizRecord(Long materialId);

    /**
     * 获取课程考试列表（包含状态动态计算）
     */
    List<Exam> getCourseExams(Long courseId);

    /**
     * 提交考试
     */
    void submitExam(Long examId, Integer score, String userAnswers, Integer cheatCount);

    /**
     * 获取考试记录详情（或考试信息）
     */
    Object getExamRecord(Long examId);

    /**
     * 获取最近活动通知
     */
    List<Map<String, Object>> getRecentActivities();
}