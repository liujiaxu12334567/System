package com.project.system.mapper;

import com.project.system.dto.UpcomingCourseSchedule;
import com.project.system.entity.CourseScheduleSlot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CourseScheduleMapper {
    List<CourseScheduleSlot> selectByClassId(@Param("classId") Long classId);

    int deleteByClassId(@Param("classId") Long classId);

    int insertBatch(@Param("list") List<CourseScheduleSlot> list);

    List<CourseScheduleSlot> selectByCourseIdAndDay(@Param("courseId") Long courseId, @Param("dayOfWeek") Integer dayOfWeek);

    int countActiveByCourseIdAndTime(@Param("courseId") Long courseId,
                                     @Param("dayOfWeek") Integer dayOfWeek,
                                     @Param("nowTime") String nowTime);

    List<UpcomingCourseSchedule> selectUpcomingStartingBetween(@Param("dayOfWeek") Integer dayOfWeek,
                                                               @Param("fromTime") String fromTime,
                                                               @Param("toTime") String toTime);
}

