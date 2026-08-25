# Listing 이미지 저장소 운영 조건

Listing API를 배포하기 전에 아래 조건을 인프라에서 먼저 충족해야 한다.

## S3 설정

- EC2 인스턴스 역할에 대상 버킷의 `listings/*`에 대한 `s3:PutObject`, `s3:DeleteObject` 권한만 부여한다.
- Access Key와 Secret Key는 환경 파일이나 저장소에 넣지 않는다. 애플리케이션은 AWS 기본 자격 증명 체인을 사용한다.
- `SETTY_S3_PUBLIC_BASE_URL`로 제공하는 경로에는 이미지 읽기만 허용한다. 업로드와 삭제 권한은 공개하지 않는다.
- 버킷 버전 관리를 활성화한다. 애플리케이션 삭제 요청은 현재 객체에 delete marker를 만들어 공개 조회를 즉시 막는다.
- noncurrent version은 7일 뒤 만료되도록 lifecycle을 설정한다.

필수 환경 변수는 `deploy/setty.env.example`을 따른다.

## 삭제 실패 대응

이미지 교체와 매물 soft delete는 DB 커밋 후 S3 객체를 삭제한다. AWS SDK 재시도 후에도 실패하면 DB 상태는 유지하고 다음 로그를 남긴다.

```text
매물 이미지 커밋 후 정리 실패
```

이 로그에는 알람을 연결한다. 알람이 발생하면 soft delete 후에도 보존되는 `listing_images.object_key`를 기준으로 대상 객체를 수동 정리한다. 영속적인 재시도 큐는 Sprint 1 범위에 포함하지 않는다.

## 병합 순서

- Member 작업이 `members(id)` 테이블을 먼저 제공해야 `listings.seller_id` 외래 키를 생성할 수 있다.
- Member/Auth 작업이 세션의 `memberId`와 PLATFORM 역할을 보장한 뒤 Listing 쓰기 API를 노출한다.
