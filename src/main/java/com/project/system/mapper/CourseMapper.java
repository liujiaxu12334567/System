// src/main/java/com/project/system/mapper/CourseMapper.java

package com.project.system.mapper;

import com.project.system.entity.Course;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface CourseMapper {
    int insertCourse(Course course);
    int insertBatchCourses(@Param("list") List<Course> list);

    List<Course> selectAllCourses();
    Course selectCourseById(@Param("id") Long id);
    Course selectByNameSemesterClassId(@Param("name") String name, @Param("semester") String semester, @Param("classId") Long classId);
    Course selectByGroupIdAndClassId(@Param("groupId") Long groupId, @Param("classId") Long classId);
    int countCoursesByTeacherIdAndSemester(@Param("teacherId") Long teacherId, @Param("semester") String semester);
    List<Long> selectDistinctClassIdsByTeacherIdAndSemester(@Param("teacherId") Long teacherId, @Param("semester") String semester);
    int updateLeaderByGroupId(@Param("groupId") Long groupId, @Param("leaderId") Long leaderId, @Param("leaderName") String leaderName);
    int updateCourse(Course course);
    int deleteCourseById(Long id);

    // 【核心方法】根据课题组长姓名查询其负责的课程
    List<Course> selectCoursesByManagerName(@Param("managerName") String managerName);

    // 新增：按课程ID列表查询
    List<Course> selectCoursesByIds(@Param("ids") List<Long> ids);
}
