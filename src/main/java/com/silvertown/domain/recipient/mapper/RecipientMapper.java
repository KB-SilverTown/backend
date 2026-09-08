package com.silvertown.domain.recipient.mapper;
import com.silvertown.domain.recipient.vo.Recipient;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
@Mapper
public interface RecipientMapper {
    List<Recipient> findCandidates(@Param("userId") String userId, @Param("keyword") String keyword, @Param("contactNames") List<String> contactNames);

    Recipient findOwnedById(
            @Param("userId") String userId, @Param("recipientId") String recipientId);
}
