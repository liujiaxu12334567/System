package com.project.system.mapper;

import com.project.system.entity.AttendanceRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AttendanceRecordMapper {
    void batchInsert(@Param("records") List<AttendanceRecord> records);

    List<AttendanceRecord> selectByClassCourseStudent(
            @Param("classId") Long classId,
            @Param("courseId") Long courseId,
            @Param("studentId") Long studentId);
}
