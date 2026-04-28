package com.b101.butterfly.segment.dto.response;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 즐겨찾기 목록 응답 (#46). 즐겨찾기 우선 + 최근 사용 분리.
 */
public record FavoritesResponse(
        List<FavoriteItem> favorites, List<RecentlyUsedItem> recentlyUsed) {

    public record FavoriteItem(
            Long segmentId, String name, Integer estimatedCount, LocalDateTime favoritedAt) {
    }

    public record RecentlyUsedItem(
            Long segmentId, String name, Integer estimatedCount, LocalDateTime lastUsedAt) {
    }
}
