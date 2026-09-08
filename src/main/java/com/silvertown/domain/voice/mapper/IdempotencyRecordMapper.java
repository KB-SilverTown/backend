package com.silvertown.domain.voice.mapper;

import com.silvertown.domain.voice.vo.IdempotencyRecordVo;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface IdempotencyRecordMapper {
    IdempotencyRecordVo findByUserOperationAndKey(
            @Param("userId") String userId,
            @Param("operation") String operation,
            @Param("idempotencyKey") String idempotencyKey);

    int insert(IdempotencyRecordVo idempotencyRecord);

    int complete(
            @Param("idempotencyRecordId") String idempotencyRecordId,
            @Param("resourceType") String resourceType,
            @Param("resourceId") String resourceId,
            @Param("responseStatus") int responseStatus,
            @Param("responseBody") String responseBody,
            @Param("updatedAt") LocalDateTime updatedAt);

    int fail(
            @Param("idempotencyRecordId") String idempotencyRecordId,
            @Param("responseStatus") int responseStatus,
            @Param("responseBody") String responseBody,
            @Param("updatedAt") LocalDateTime updatedAt);
}
