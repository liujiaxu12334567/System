package com.project.system.mapper;

import com.project.system.entity.CourseGroup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CourseGroupMapper {
    CourseGroup selectById(@Param("groupId") Long groupId);

    CourseGroup selectByNameAndSemester(@Param("name") String name, @Param("semester") String semester);

    List<CourseGroup> selectAll(@Param("semester") String semester);

    List<CourseGroup> selectByLeaderId(@Param("leaderId") Long leaderId);

    int insert(CourseGroup group);

    int updateLeader(@Param("groupId") Long groupId, @Param("leaderId") Long leaderId, @Param("leaderName") String leaderName);

    int countByLeaderId(@Param("leaderId") Long leaderId);
}
