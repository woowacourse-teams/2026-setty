-- 주소 기준 데이터와 회원 표준 주소. 적용 후에는 수정하지 말고 새 버전을 추가한다.

-- 도로명주소 DB(주소·부가정보)와 우체국 우편번호 DB를 관리번호로 이은 표. 로컬에서 server/scripts/address로 채운다.
-- 가입·주소 변경 때만 조회하므로 앱 메모리에 올리지 않는다.
CREATE TABLE IF NOT EXISTS address_reference (
    mgmt_no        CHAR(25)     NOT NULL,              -- 도로명주소 건물관리번호
    road_code      CHAR(12)     NOT NULL,
    road_name      VARCHAR(40)  NOT NULL,
    underground    BOOLEAN      NOT NULL,
    building_main  INT          NOT NULL,
    building_sub   INT          NOT NULL,              -- 부번 없으면 0
    sigungu        VARCHAR(10)  NOT NULL,
    legal_dong     VARCHAR(20)  NULL,                  -- 법정동 이름. 우체국 DB에 없는 법정동은 NULL
    jibun_mountain BOOLEAN      NOT NULL,
    jibun_main     INT          NOT NULL,
    jibun_sub      INT          NOT NULL,
    zip_code       CHAR(5)      NOT NULL,
    dong_code      VARCHAR(10)  NULL,                  -- 행정동 코드
    building_name  VARCHAR(50)  NULL,
    latitude       DECIMAL(9,6) NULL,                  -- 출입구 좌표. 이용 조건 확인 전에는 로컬에서만 채운다
    longitude      DECIMAL(9,6) NULL,
    PRIMARY KEY (mgmt_no),
    INDEX idx_address_reference_road (road_name, building_main, building_sub),
    INDEX idx_address_reference_jibun (legal_dong, jibun_main, jibun_sub)
);

-- 회원 자유 입력 주소(address)를 해석한 결과. address_status가 NULL이면 아직 해석하지 않은 회원이다.
SET @members_address_mgmt_no_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'members' AND column_name = 'address_mgmt_no'
);
SET @add_members_address_mgmt_no = IF(
    @members_address_mgmt_no_exists = 0,
    'ALTER TABLE members ADD COLUMN address_mgmt_no CHAR(25) NULL AFTER dong_code',
    'SELECT 1'
);
PREPARE add_members_address_mgmt_no_statement FROM @add_members_address_mgmt_no;
EXECUTE add_members_address_mgmt_no_statement;
DEALLOCATE PREPARE add_members_address_mgmt_no_statement;

SET @members_road_address_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'members' AND column_name = 'road_address'
);
SET @add_members_road_address = IF(
    @members_road_address_exists = 0,
    'ALTER TABLE members ADD COLUMN road_address VARCHAR(200) NULL AFTER address_mgmt_no',
    'SELECT 1'
);
PREPARE add_members_road_address_statement FROM @add_members_road_address;
EXECUTE add_members_road_address_statement;
DEALLOCATE PREPARE add_members_road_address_statement;

SET @members_address_detail_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'members' AND column_name = 'address_detail'
);
SET @add_members_address_detail = IF(
    @members_address_detail_exists = 0,
    'ALTER TABLE members ADD COLUMN address_detail VARCHAR(100) NULL AFTER road_address',
    'SELECT 1'
);
PREPARE add_members_address_detail_statement FROM @add_members_address_detail;
EXECUTE add_members_address_detail_statement;
DEALLOCATE PREPARE add_members_address_detail_statement;

SET @members_zip_code_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'members' AND column_name = 'zip_code'
);
SET @add_members_zip_code = IF(
    @members_zip_code_exists = 0,
    'ALTER TABLE members ADD COLUMN zip_code CHAR(5) NULL AFTER address_detail',
    'SELECT 1'
);
PREPARE add_members_zip_code_statement FROM @add_members_zip_code;
EXECUTE add_members_zip_code_statement;
DEALLOCATE PREPARE add_members_zip_code_statement;

SET @members_latitude_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'members' AND column_name = 'latitude'
);
SET @add_members_latitude = IF(
    @members_latitude_exists = 0,
    'ALTER TABLE members ADD COLUMN latitude DECIMAL(9,6) NULL AFTER zip_code',
    'SELECT 1'
);
PREPARE add_members_latitude_statement FROM @add_members_latitude;
EXECUTE add_members_latitude_statement;
DEALLOCATE PREPARE add_members_latitude_statement;

SET @members_longitude_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'members' AND column_name = 'longitude'
);
SET @add_members_longitude = IF(
    @members_longitude_exists = 0,
    'ALTER TABLE members ADD COLUMN longitude DECIMAL(9,6) NULL AFTER latitude',
    'SELECT 1'
);
PREPARE add_members_longitude_statement FROM @add_members_longitude;
EXECUTE add_members_longitude_statement;
DEALLOCATE PREPARE add_members_longitude_statement;

SET @members_address_status_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'members' AND column_name = 'address_status'
);
SET @add_members_address_status = IF(
    @members_address_status_exists = 0,
    'ALTER TABLE members ADD COLUMN address_status VARCHAR(20) NULL AFTER longitude',
    'SELECT 1'
);
PREPARE add_members_address_status_statement FROM @add_members_address_status;
EXECUTE add_members_address_status_statement;
DEALLOCATE PREPARE add_members_address_status_statement;
