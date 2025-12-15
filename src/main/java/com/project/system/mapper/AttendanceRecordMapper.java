package com.project.system.mapper;

import com.project.system.entity.AttendanceRecord;
import com.project.system.entity.AttendanceSummary;
import com.project.system.dto.StudentAttendanceAgg;
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

    List<StudentAttendanceAgg> countByClassAndCourseIdsAndStudentIds(
            @Param("classId") Long classId,
            @Param("courseIds") List<Long> courseIds,
            @Param("studentIds") List<Long> studentIds);
}
