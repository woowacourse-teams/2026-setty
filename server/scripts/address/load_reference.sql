-- build_reference.py가 만든 address_reference.tsv를 로컬 DB에 넣는다. TSV가 있는 폴더에서 실행한다.
-- mysql --local-infile=1 -h127.0.0.1 -usetty -psetty setty < load_reference.sql
TRUNCATE TABLE address_reference;

LOAD DATA LOCAL INFILE 'address_reference.tsv'
INTO TABLE address_reference
CHARACTER SET utf8mb4
FIELDS TERMINATED BY '\t'
LINES TERMINATED BY '\n'
(mgmt_no, road_code, road_name, underground, building_main, building_sub, sigungu, legal_dong,
 jibun_mountain, jibun_main, jibun_sub, zip_code, dong_code, building_name, latitude, longitude);

SHOW WARNINGS LIMIT 10;

-- build_reference.py가 출력한 건수와 같아야 한다(2026-09 기준 주소 523,227건, 좌표를 넣었으면 522,525건).
SELECT COUNT(*) AS addresses, COUNT(latitude) AS with_coords, COUNT(dong_code) AS with_dong
FROM address_reference;
