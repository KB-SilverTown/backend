package com.silvertown.domain.mobilebranch.mapper;

import com.silvertown.domain.mobilebranch.vo.MobileBranchScheduleRow;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MobileBranchMapper {
    List<MobileBranchScheduleRow> findUpcomingActiveSchedules(
            @Param("now") LocalDateTime now, @Param("serviceCode") String serviceCode);
}
