package com.silvertown.domain.account.mapper;
import com.silvertown.domain.account.vo.BankAccount;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
@Mapper
public interface AccountMapper {
    List<BankAccount> findActiveByUserId(@Param("userId") String userId);

    BankAccount findOwnedActiveById(
            @Param("userId") String userId, @Param("accountId") String accountId);

    int insertPrimaryAccount(
            @Param("accountId") String accountId,
            @Param("userId") String userId,
            @Param("bankCode") String bankCode,
            @Param("accountNumberEncrypted") byte[] accountNumberEncrypted,
            @Param("accountNumberHash") String accountNumberHash,
            @Param("createdAt") java.time.OffsetDateTime createdAt);
}
