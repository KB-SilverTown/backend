package com.silvertown.domain.mobilebranch.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Flat row returned by the mobile-branch schedule and service join. */
@Getter
@Setter
public class MobileBranchScheduleRow {
    private String branchId;
    private String scheduleId;
    private String name;
    private String branchType;
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String phone;
    private LocalDate serviceDate;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private String serviceId;
    private String serviceCode;
    private String serviceName;
    private String serviceCategory;
    private String preparationNote;
}
