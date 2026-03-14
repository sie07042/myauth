# Direct Message 기능 설계 문서

작성일: 2026-03-08  
대상 프로젝트: `myauth` (Spring Boot + JPA + JWT)

## 1. 현재 프로젝트 분석 요약

- 인증/인가: JWT 기반, `@AuthenticationPrincipal User`로 사용자 주입.
- API 응답: `ApiResponse<T> { success, message, data }` 공통 포맷 사용.
- 도메인 패턴: `entity -> repository -> service -> controller` 계층 분리, 페이지네이션은 `Pageable` 기반.
- 소셜 그래프: `Follow` 도메인 이미 존재하며 `isMutualFollow(user1, user2)` 쿼리 보유.
- 예외 처리: `GlobalExceptionHandler`에서 커스텀 예외를 HTTP 코드로 매핑.
- 기술 제약: 현재 WebSocket/SSE 의존성 없음. 실시간은 2차 확장으로 두는 것이 안전.

## 2. DM 기능 목표 및 범위

### 2.1 목표

- 회원 간 1:1 대화(Direct Message) 제공
- 대화방 목록, 메시지 목록, 메시지 전송, 읽음 처리 지원
- 기존 인증/응답/예외 패턴과 일관성 유지

### 2.2 v1 범위 (이번 설계 기준)

- 1:1 DM만 지원 (그룹 채팅 제외)
- 텍스트 메시지 우선 (첨부파일 제외)
- REST + 폴링 기반 갱신
- 읽음 상태는 "상대가 마지막으로 읽은 메시지 ID" 방식

### 2.3 v2 이후 확장 범위

- WebSocket 또는 SSE 실시간 푸시
- 첨부파일/이미지 메시지
- 차단(Block)/신고(Report)
- 메시지 삭제 정책 고도화(양쪽 삭제 vs 개인 숨김)

## 3. 핵심 정책

### 3.1 대화 시작/전송 권한 정책 (권장)

초기 정책은 스팸 방지를 위해 `상호 팔로우(Mutual Follow)` 사용자끼리만 DM 허용.

- 장점: 기존 `FollowRepository.isMutualFollow()` 재활용 가능
- 대안: 팔로워만 허용, 모두 허용, 수신 허용 설정 기반 (v2)

### 3.2 본인 DM 금지

- `senderId == receiverId` 요청은 400 처리

### 3.3 삭제 정책

- v1: 메시지 하드 삭제 API는 제공하지 않음
- 필요 시 `deletedBySender`, `deletedByReceiver` 개인 숨김 방식으로 확장

## 4. 데이터 모델 설계

## 4.1 엔티티

### 4.1.1 `dm_rooms`

- 1:1 대화방 메타 정보
- 한 쌍의 유저는 하나의 대화방만 가짐

주요 컬럼:

- `id` BIGINT PK
- `user1_id` BIGINT NOT NULL (작은 ID)
- `user2_id` BIGINT NOT NULL (큰 ID)
- `last_message_id` BIGINT NULL
- `last_message_at` DATETIME NULL
- `created_at` DATETIME
- `updated_at` DATETIME

제약/인덱스:

- `UNIQUE(user1_id, user2_id)`
- `INDEX(last_message_at DESC)`
- `INDEX(user1_id)`, `INDEX(user2_id)`

정규화 규칙:

- 저장 전 항상 `user1_id < user2_id`로 정렬해 unique 충돌/중복 방지

### 4.1.2 `dm_messages`

- 대화방 내 메시지

주요 컬럼:

- `id` BIGINT PK
- `room_id` BIGINT NOT NULL
- `sender_id` BIGINT NOT NULL
- `content` TEXT NOT NULL
- `is_deleted` BOOLEAN DEFAULT false (옵션)
- `created_at` DATETIME

제약/인덱스:

- `FK(room_id) -> dm_rooms(id)`
- `FK(sender_id) -> users(id)`
- `INDEX(room_id, id DESC)` (최신 메시지 페이징)
- `INDEX(sender_id, created_at DESC)`

### 4.1.3 `dm_room_reads`

- 사용자별 마지막 읽은 메시지 저장

주요 컬럼:

- `id` BIGINT PK
- `room_id` BIGINT NOT NULL
- `user_id` BIGINT NOT NULL
- `last_read_message_id` BIGINT NULL
- `last_read_at` DATETIME NULL
- `created_at` DATETIME
- `updated_at` DATETIME

제약/인덱스:

- `UNIQUE(room_id, user_id)`
- `INDEX(user_id, updated_at DESC)`

## 5. API 설계 (v1)

모든 응답은 `ApiResponse` 포맷을 유지.

### 5.1 대화방 생성 또는 조회

- `POST /api/dm/rooms`
- body: `{ "targetUserId": 22 }`
- 동작: 기존 방 있으면 반환, 없으면 생성

검증:

- 대상 유저 존재 여부
- 본인 대상 금지
- 상호 팔로우 검증

### 5.2 내 대화방 목록 조회

- `GET /api/dm/rooms?page=0&size=20`
- 정렬: `lastMessageAt DESC NULLS LAST`

응답 데이터 예:

- roomId
- peerUser (id, name, profileImage)
- lastMessagePreview
- lastMessageAt
- unreadCount

### 5.3 메시지 목록 조회

- `GET /api/dm/rooms/{roomId}/messages?beforeId=12345&size=30`
- 커서 기반 권장 (`beforeId`)
- 최신부터 내려주고 프론트에서 역정렬 가능

### 5.4 메시지 전송

- `POST /api/dm/rooms/{roomId}/messages`
- body: `{ "content": "안녕하세요" }`

검증:

- 방 참여자 여부
- 메시지 길이(예: 1~2000)
- 상대와의 DM 정책(상호 팔로우) 재검증 여부는 정책 선택

처리:

- 메시지 insert
- `dm_rooms.last_message_id`, `last_message_at` 업데이트

### 5.5 읽음 처리

- `POST /api/dm/rooms/{roomId}/read`
- body: `{ "lastReadMessageId": 9999 }`

처리:

- `dm_room_reads` upsert
- `lastReadMessageId`는 기존 값보다 클 때만 갱신

## 6. DTO 설계

추천 패키지:

- `dto/dm/`

주요 DTO:

- `DmRoomCreateRequest`
- `DmRoomResponse`
- `DmRoomListItemResponse`
- `DmMessageCreateRequest`
- `DmMessageResponse`
- `DmReadRequest`

필드 예시:

- `DmMessageCreateRequest.content` -> `@NotBlank`, `@Size(max = 2000)`

## 7. 서버 컴포넌트 설계

### 7.1 Entity

- `DmRoom`
- `DmMessage`
- `DmRoomRead`

### 7.2 Repository

- `DmRoomRepository`
- `DmMessageRepository`
- `DmRoomReadRepository`

핵심 메서드 예:

- `Optional<DmRoom> findByUser1IdAndUser2Id(Long user1Id, Long user2Id)`
- `Page<DmRoom> findMyRooms(Long meId, Pageable pageable)` (custom query)
- `Slice<DmMessage> findByRoomIdAndIdLessThanOrderByIdDesc(...)`

### 7.3 Service

- `DmService`
  - `createOrGetRoom(meId, targetUserId)`
  - `getMyRooms(meId, pageable)`
  - `getMessages(meId, roomId, beforeId, size)`
  - `sendMessage(meId, roomId, request)`
  - `markAsRead(meId, roomId, request)`

### 7.4 Controller

- `DmController` (`/api/dm`)

## 8. 예외/에러 설계

신규 예외 클래스:

- `DmRoomNotFoundException` -> 404
- `DmAccessDeniedException` -> 403
- `DmPolicyViolationException` -> 403 또는 400
- `DmMessageValidationException` -> 400 (필요 시)

`GlobalExceptionHandler`에 매핑 추가.

## 9. 성능 및 인덱스 전략

- 방 목록: `dm_rooms.last_message_at` 인덱스 필수
- 메시지 조회: `dm_messages(room_id, id DESC)` 인덱스 필수
- unread 계산:
  - 기본: `count(message.id > lastReadMessageId)`
  - 최적화: 필요 시 `dm_room_reads.unread_count` 캐시 컬럼 추가

## 10. 보안 및 무결성

- 모든 DM API 인증 필수 (`/api/**` 정책으로 자동 적용)
- 방 접근 시 "참여자 검증" 필수
- 전송자 `senderId`는 토큰 사용자 ID만 사용 (클라이언트 입력 금지)
- 메시지 콘텐츠 XSS는 프론트 렌더링 시 escape 원칙 유지

## 11. 실시간 전략

### 11.1 v1 (즉시 적용)

- 3~5초 폴링:
  - 방 목록 갱신
  - 현재 열린 방의 새 메시지 조회

### 11.2 v2

- WebSocket(STOMP) 또는 SSE 도입
- 도입 시 필요 작업:
  - 의존성 추가
  - `Security` + 소켓 인증 연동
  - room 단위 구독 채널 정의

## 12. 단계별 구현 계획

1. 엔티티/리포지토리 추가 + 인덱스 반영  
2. 서비스 계층 구현(권한/정책/트랜잭션)  
3. 컨트롤러 + DTO + 예외 처리 연결  
4. 통합 테스트(생성/전송/읽음/권한 거부)  
5. API 문서(`API_COMPLETE_GUIDE.md`) 업데이트

## 13. 테스트 시나리오

- 상호 팔로우 아님 -> DM 생성 실패(403)
- 상호 팔로우 상태 -> DM 생성 성공
- 본인에게 DM 생성 시도 -> 실패(400)
- 방 비참여자 메시지 조회/전송 -> 실패(403)
- 읽음 처리 후 unreadCount 감소 검증
- 메시지 페이징(beforeId) 정상 동작 검증

## 14. 오픈 이슈

- DM 허용 정책을 상호 팔로우로 고정할지, 사용자 설정(`누구나/팔로워/상호팔로우`)으로 확장할지
- 메시지 삭제/편집 요구사항 필요 여부
- 신고/차단 기능 우선순위

---

이 문서는 현재 코드베이스 구조와 제약을 기준으로 작성된 v1 설계안입니다.
