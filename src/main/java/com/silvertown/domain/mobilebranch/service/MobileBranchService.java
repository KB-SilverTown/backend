package com.silvertown.domain.mobilebranch.service;

import com.silvertown.domain.mobilebranch.dto.MobileBranchRecommendationQuery;
import com.silvertown.domain.mobilebranch.dto.MobileBranchRecommendationResponse;
import java.util.List;

public interface MobileBranchService {
    List<MobileBranchRecommendationResponse> recommend(MobileBranchRecommendationQuery query);
}
