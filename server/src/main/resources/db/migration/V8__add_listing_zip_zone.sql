-- 판매자 우편번호 구역 번호(deliveryfee/seoul-zip-zones.csv의 index). 적용 후에는 수정하지 말고 새 버전을 추가한다.
-- 등록 때 판매자의 표준 주소 우편번호로 정한다. 표준 주소가 없거나 서울 밖이면 NULL이고, 그 매물은 행정동으로 계산한다.
SET @listings_zip_zone_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'listings' AND column_name = 'zip_zone'
);
SET @add_listings_zip_zone = IF(
    @listings_zip_zone_exists = 0,
    'ALTER TABLE listings ADD COLUMN zip_zone SMALLINT NULL AFTER dong_code',
    'SELECT 1'
);
PREPARE add_listings_zip_zone_statement FROM @add_listings_zip_zone;
EXECUTE add_listings_zip_zone_statement;
DEALLOCATE PREPARE add_listings_zip_zone_statement;

-- 배송비 예산 검색(우편번호)용. idx_listings_delivery_filter와 같은 꼴로, 최신순으로 읽으면서 색인 안에서 (구역, 부피) 조건을 거른다.
-- 행정동 색인은 우편번호가 없는 구매자가 계속 쓰므로 그대로 둔다.
SET @listings_zip_zone_filter_index_exists = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'listings' AND index_name = 'idx_listings_zip_zone_filter'
);
SET @add_listings_zip_zone_filter_index = IF(
    @listings_zip_zone_filter_index_exists = 0,
    'ALTER TABLE listings ADD INDEX idx_listings_zip_zone_filter (deleted_at, sale_status, created_at, id, zip_zone, volume_tier)',
    'SELECT 1'
);
PREPARE add_listings_zip_zone_filter_index_statement FROM @add_listings_zip_zone_filter_index;
EXECUTE add_listings_zip_zone_filter_index_statement;
DEALLOCATE PREPARE add_listings_zip_zone_filter_index_statement;
