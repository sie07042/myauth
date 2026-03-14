package com.example.myauth.exception;

import com.example.myauth.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 전역 예외 처리 핸들러
 * 모든 @RestController에서 발생하는 예외를 한 곳에서 처리한다
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  /**
   * 인증 실패 예외 처리
   * 이메일 또는 비밀번호가 올바르지 않을 때 발생
   */
  @ExceptionHandler(InvalidCredentialsException.class)
  @SuppressWarnings("NullableProblems")
  public ResponseEntity<ApiResponse<Void>> handleInvalidCredentials(
      InvalidCredentialsException ex) {
    log.warn("인증 실패: {}", ex.getMessage());

    return ResponseEntity
        .status(HttpStatus.UNAUTHORIZED)
        .body(ApiResponse.error(ex.getMessage()));
  }

  /**
   * 토큰 관련 예외 처리
   * Refresh Token이 유효하지 않거나 만료되었을 때 발생
   */
  @ExceptionHandler(TokenException.class)
  @SuppressWarnings("NullableProblems")
  public ResponseEntity<ApiResponse<Void>> handleTokenException(
      TokenException ex) {
    log.warn("토큰 오류: {}", ex.getMessage());

    return ResponseEntity
        .status(HttpStatus.UNAUTHORIZED)
        .body(ApiResponse.error(ex.getMessage()));
  }

  /**
   * 계정 상태 관련 예외 처리
   * 계정이 비활성화, 정지, 삭제 등의 상태일 때 발생
   */
  @ExceptionHandler(AccountException.class)
  @SuppressWarnings("NullableProblems")
  public ResponseEntity<ApiResponse<Void>> handleAccountException(
      AccountException ex) {
    log.warn("계정 상태 오류: {}", ex.getMessage());

    return ResponseEntity
        .status(HttpStatus.FORBIDDEN)
        .body(ApiResponse.error(ex.getMessage()));
  }

  /**
   * 이메일 중복 예외 처리
   * 회원가입 시 이미 존재하는 이메일로 가입을 시도할 때 발생
   */
  @ExceptionHandler(DuplicateEmailException.class)
  @SuppressWarnings("NullableProblems")
  public ResponseEntity<ApiResponse<Void>> handleDuplicateEmail(
      DuplicateEmailException ex) {
    log.warn("이메일 중복: {}", ex.getMessage());

    return ResponseEntity
        .status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.error(ex.getMessage()));
  }

  /**
   * 파일 저장/삭제 실패 예외 처리
   * 파일 시스템 관련 IO 오류 발생 시
   */
  @ExceptionHandler(FileStorageException.class)
  @SuppressWarnings("NullableProblems")
  public ResponseEntity<ApiResponse<Void>> handleFileStorageException(
      FileStorageException ex) {
    log.error("파일 저장 오류: {}", ex.getMessage(), ex);

    return ResponseEntity
        .status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiResponse.error(ex.getMessage()));
  }

  /**
   * 파일 유효성 검사 실패 예외 처리
   * 파일이 비어있거나, 크기 초과, 지원하지 않는 형식 등
   */
  @ExceptionHandler(InvalidFileException.class)
  @SuppressWarnings("NullableProblems")
  public ResponseEntity<ApiResponse<Void>> handleInvalidFileException(
      InvalidFileException ex) {
    log.warn("파일 유효성 검사 실패 [{}]: {}", ex.getErrorCode(), ex.getMessage());

    // 에러 코드에 따른 HTTP 상태 코드 결정
    HttpStatus status = switch (ex.getErrorCode()) {
      case EMPTY_FILE, FILE_TOO_LARGE, UNSUPPORTED_TYPE, INVALID_FILENAME -> HttpStatus.BAD_REQUEST;
      case INVALID_PATH -> HttpStatus.FORBIDDEN;  // 경로 조작 시도는 403
    };

    return ResponseEntity
        .status(status)
        .body(ApiResponse.error(ex.getMessage()));
  }

  /**
   * 게시글을 찾을 수 없을 때 예외 처리
   * 존재하지 않는 게시글 ID로 조회/수정/삭제 시도 시 발생
   */
  @ExceptionHandler(PostNotFoundException.class)
  @SuppressWarnings("NullableProblems")
  public ResponseEntity<ApiResponse<Void>> handlePostNotFoundException(
      PostNotFoundException ex) {
    log.warn("게시글 조회 실패: {}", ex.getMessage());

    return ResponseEntity
        .status(HttpStatus.NOT_FOUND)
        .body(ApiResponse.error(ex.getMessage()));
  }

  /**
   * 권한 없는 접근 예외 처리
   * 다른 사용자의 게시글 수정/삭제 시도 또는 비공개 게시글 접근 시 발생
   */
  @ExceptionHandler(UnauthorizedAccessException.class)
  @SuppressWarnings("NullableProblems")
  public ResponseEntity<ApiResponse<Void>> handleUnauthorizedAccessException(
      UnauthorizedAccessException ex) {
    log.warn("권한 없는 접근 시도: {}", ex.getMessage());

    return ResponseEntity
        .status(HttpStatus.FORBIDDEN)
        .body(ApiResponse.error(ex.getMessage()));
  }

  /**
   * 댓글을 찾을 수 없을 때 예외 처리
   * 존재하지 않는 댓글 ID로 조회/수정/삭제 시도 시 발생
   */
  @ExceptionHandler(CommentNotFoundException.class)
  @SuppressWarnings("NullableProblems")
  public ResponseEntity<ApiResponse<Void>> handleCommentNotFoundException(
      CommentNotFoundException ex) {
    log.warn("댓글 조회 실패: {}", ex.getMessage());

    return ResponseEntity
        .status(HttpStatus.NOT_FOUND)
        .body(ApiResponse.error(ex.getMessage()));
  }

  /**
   * 중복 좋아요 예외 처리
   * 이미 좋아요한 게시글/댓글에 다시 좋아요 시도 시 발생
   */
  @ExceptionHandler(DuplicateLikeException.class)
  @SuppressWarnings("NullableProblems")
  public ResponseEntity<ApiResponse<Void>> handleDuplicateLikeException(
      DuplicateLikeException ex) {
    log.warn("중복 좋아요 시도: {}", ex.getMessage());

    return ResponseEntity
        .status(HttpStatus.CONFLICT)
        .body(ApiResponse.error(ex.getMessage()));
  }

  /**
   * 좋아요 기록 없음 예외 처리
   * 좋아요하지 않은 게시글/댓글의 좋아요 취소 시도 시 발생
   */
  @ExceptionHandler(LikeNotFoundException.class)
  @SuppressWarnings("NullableProblems")
  public ResponseEntity<ApiResponse<Void>> handleLikeNotFoundException(
      LikeNotFoundException ex) {
    log.warn("좋아요 기록 없음: {}", ex.getMessage());

    return ResponseEntity
        .status(HttpStatus.NOT_FOUND)
        .body(ApiResponse.error(ex.getMessage()));
  }

  /**
   * 잘못된 인자 예외 처리
   * 비즈니스 로직에서 유효하지 않은 인자 전달 시 발생
   * 예: 대댓글의 대댓글 작성 시도
   */
  @ExceptionHandler(IllegalArgumentException.class)
  @SuppressWarnings("NullableProblems")
  public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(
      IllegalArgumentException ex) {
    log.warn("잘못된 요청: {}", ex.getMessage());

    return ResponseEntity
        .status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.error(ex.getMessage()));
  }

  /**
   * 사용자를 찾을 수 없을 때 예외 처리
   */
  @ExceptionHandler(UserNotFoundException.class)
  @SuppressWarnings("NullableProblems")
  public ResponseEntity<ApiResponse<Void>> handleUserNotFoundException(
      UserNotFoundException ex) {
    log.warn("사용자 조회 실패: {}", ex.getMessage());

    return ResponseEntity
        .status(HttpStatus.NOT_FOUND)
        .body(ApiResponse.error(ex.getMessage()));
  }

  /**
   * 자기 자신 팔로우 시도 예외 처리
   */
  @ExceptionHandler(SelfFollowException.class)
  @SuppressWarnings("NullableProblems")
  public ResponseEntity<ApiResponse<Void>> handleSelfFollowException(
      SelfFollowException ex) {
    log.warn("자기 팔로우 시도: {}", ex.getMessage());

    return ResponseEntity
        .status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.error(ex.getMessage()));
  }

  /**
   * 중복 팔로우 예외 처리
   */
  @ExceptionHandler(DuplicateFollowException.class)
  @SuppressWarnings("NullableProblems")
  public ResponseEntity<ApiResponse<Void>> handleDuplicateFollowException(
      DuplicateFollowException ex) {
    log.warn("중복 팔로우 시도: {}", ex.getMessage());

    return ResponseEntity
        .status(HttpStatus.CONFLICT)
        .body(ApiResponse.error(ex.getMessage()));
  }

  /**
   * 팔로우 관계 없음 예외 처리
   */
  @ExceptionHandler(FollowNotFoundException.class)
  @SuppressWarnings("NullableProblems")
  public ResponseEntity<ApiResponse<Void>> handleFollowNotFoundException(
      FollowNotFoundException ex) {
    log.warn("팔로우 관계 없음: {}", ex.getMessage());

    return ResponseEntity
        .status(HttpStatus.NOT_FOUND)
        .body(ApiResponse.error(ex.getMessage()));
  }

  /**
   * 중복 북마크 예외 처리
   */
  @ExceptionHandler(DuplicateBookmarkException.class)
  @SuppressWarnings("NullableProblems")
  public ResponseEntity<ApiResponse<Void>> handleDuplicateBookmarkException(
      DuplicateBookmarkException ex) {
    log.warn("중복 북마크 시도: {}", ex.getMessage());

    return ResponseEntity
        .status(HttpStatus.CONFLICT)
        .body(ApiResponse.error(ex.getMessage()));
  }

  /**
   * 북마크 없음 예외 처리
   */
  @ExceptionHandler(BookmarkNotFoundException.class)
  @SuppressWarnings("NullableProblems")
  public ResponseEntity<ApiResponse<Void>> handleBookmarkNotFoundException(
      BookmarkNotFoundException ex) {
    log.warn("북마크 없음: {}", ex.getMessage());

    return ResponseEntity
        .status(HttpStatus.NOT_FOUND)
        .body(ApiResponse.error(ex.getMessage()));
  }

  /**
   * 해시태그 없음 예외 처리
   */
  @ExceptionHandler(HashtagNotFoundException.class)
  @SuppressWarnings("NullableProblems")
  public ResponseEntity<ApiResponse<Void>> handleHashtagNotFoundException(
      HashtagNotFoundException ex) {
    log.warn("해시태그 없음: {}", ex.getMessage());

    return ResponseEntity
        .status(HttpStatus.NOT_FOUND)
        .body(ApiResponse.error(ex.getMessage()));
  }

  /**
   * Bean Validation 검증 실패 시 처리
   * Controller에서 @Valid 어노테이션으로 검증 실패한 경우 발생하는 예외를 처리한다
   *
   * @param ex 검증 실패 예외 객체
   * @return 첫 번째 에러 메시지를 포함한 ApiResponse
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  @SuppressWarnings("NullableProblems")  // ApiResponse는 항상 non-null을 반환하므로 경고 억제
  public ResponseEntity<ApiResponse<Void>> handleValidationExceptions(
      MethodArgumentNotValidException ex) {

    // 첫 번째 에러 메시지만 반환
    String errorMessage = ex.getBindingResult()
        .getAllErrors()
        .stream()
        .findFirst()
        .map(ObjectError::getDefaultMessage)
        .orElse("입력값이 올바르지 않습니다.");

    log.warn("입력값 검증 실패: {}", errorMessage);

    return ResponseEntity
        .status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.error(errorMessage));
  }

  /**
   * HTTP 요청 body 읽기 실패 시 처리
   * - 요청 body가 비어있거나 필수인데 없는 경우
   * - JSON 파싱 실패 (잘못된 JSON 형식)
   * - Content-Type과 실제 body 내용이 일치하지 않는 경우
   *
   * @param ex HttpMessageNotReadableException
   * @return 사용자 친화적인 에러 메시지
   */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  @SuppressWarnings("NullableProblems")
  public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadable(
      HttpMessageNotReadableException ex) {

    String errorMessage;
    String detailMessage = ex.getMessage();

    // 에러 메시지 분석하여 사용자 친화적인 메시지 생성
    if (detailMessage != null && detailMessage.contains("Required request body is missing")) {
      errorMessage = "요청 body가 비어있습니다. JSON 형식의 데이터를 전송해주세요.";
    } else {
      errorMessage = "잘못된 요청 형식입니다. JSON 형식이 올바른지 확인해주세요.";
    }

    log.warn("HTTP 메시지 읽기 실패: {}", errorMessage);
    log.debug("상세 에러: {}", detailMessage);

    return ResponseEntity
        .status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.error(errorMessage));
  }

  /**
   * 지원하지 않는 Content-Type으로 요청한 경우 처리
   * 예: application/json을 기대하는데 application/x-www-form-urlencoded로 요청
   *
   * @param ex HttpMediaTypeNotSupportedException
   * @return 사용자 친화적인 에러 메시지
   */
  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  @SuppressWarnings("NullableProblems")
  public ResponseEntity<ApiResponse<Void>> handleHttpMediaTypeNotSupported(
      HttpMediaTypeNotSupportedException ex) {

    String errorMessage = String.format(
        "지원하지 않는 Content-Type입니다. 'Content-Type: application/json' 헤더를 추가해주세요.",
        ex.getContentType()
    );

    log.warn("지원하지 않는 Media Type: {}", ex.getContentType());

    return ResponseEntity
        .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
        .body(ApiResponse.error(errorMessage));
  }

  /**
   * 지원하지 않는 HTTP 메서드로 요청한 경우 처리
   * 예: POST만 지원하는 엔드포인트에 GET 요청
   *
   * @param ex HttpRequestMethodNotSupportedException
   * @return 사용자 친화적인 에러 메시지
   */
  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  @SuppressWarnings("NullableProblems")
  public ResponseEntity<ApiResponse<Void>> handleHttpRequestMethodNotSupported(
      HttpRequestMethodNotSupportedException ex) {

    String supportedMethods = ex.getSupportedHttpMethods() != null
        ? ex.getSupportedHttpMethods().toString()
        : "지원되는 메서드 없음";

    String errorMessage = String.format(
        "%s 메서드는 지원하지 않습니다. 지원하는 메서드: %s",
        ex.getMethod(),
        supportedMethods
    );

    log.warn("지원하지 않는 HTTP 메서드: {} (요청된 메서드), 지원: {}",
        ex.getMethod(), supportedMethods);

    return ResponseEntity
        .status(HttpStatus.METHOD_NOT_ALLOWED)
        .body(ApiResponse.error(errorMessage));
  }

  /**
   * 트랜잭션 롤백 예외 처리
   * @Transactional 메서드에서 예외를 catch해서 처리했지만 트랜잭션이 rollback-only로 마킹된 경우 발생
   *
   * @param ex UnexpectedRollbackException
   * @return 적절한 에러 메시지
   */
  @ExceptionHandler(UnexpectedRollbackException.class)
  @SuppressWarnings("NullableProblems")
  public ResponseEntity<ApiResponse<Void>> handleUnexpectedRollbackException(
      UnexpectedRollbackException ex) {

    log.warn("트랜잭션 롤백 예외 발생: {}", ex.getMessage());

    // 원인 예외를 재귀적으로 탐색
    Throwable cause = ex;
    while (cause != null) {
      log.debug("예외 체인: {}", cause.getClass().getName());

      // DataIntegrityViolationException 발견
      if (cause instanceof DataIntegrityViolationException) {
        log.warn("데이터 무결성 제약 위반 발견: {}", cause.getMessage());
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error("이미 가입된 이메일입니다."));
      }

      cause = cause.getCause();
    }

    // DataIntegrityViolationException을 찾지 못한 경우
    // (회원가입 시 중복 이메일 에러가 대부분이므로 기본 메시지 제공)
    log.warn("원인 예외를 특정할 수 없지만, 중복 이메일로 추정됨");
    return ResponseEntity
        .status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.error("이미 가입된 이메일입니다."));
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ApiResponse<Void>> handleNoResourceFoundException(
      NoResourceFoundException ex
  ) {
    log.error("No Resource Found Exception: "+ex.getMessage());
    return ResponseEntity
        .status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.error(ex.getMessage()));
  }

  /**
   * 모든 예외를 처리하는 최후의 방어선
   * 다른 ExceptionHandler에서 처리되지 않은 모든 예외를 여기서 처리한다
   *
   * @param ex 발생한 예외 객체
   * @return 서버 오류 메시지를 포함한 ApiResponse
   */
  @ExceptionHandler(Exception.class)
  @SuppressWarnings("NullableProblems")
  public ResponseEntity<ApiResponse<Void>> handleAllExceptions(Exception ex) {
    // 스택 트레이스에서 예외 발생 위치 추출
    StackTraceElement[] stackTrace = ex.getStackTrace();
    String errorLocation = "알 수 없음";

    if (stackTrace != null && stackTrace.length > 0) {
      StackTraceElement firstElement = stackTrace[0];
      errorLocation = String.format("%s.%s (line: %d)",
          firstElement.getClassName(),
          firstElement.getMethodName(),
          firstElement.getLineNumber());
    }

    // 상세한 로그 기록 (개발자용)
    log.error("=== 예상치 못한 오류 발생 ===");
    log.error("예외 타입: {}", ex.getClass().getName());
    log.error("예외 메시지: {}", ex.getMessage());
    log.error("발생 위치: {}", errorLocation);
    log.error("전체 스택 트레이스:", ex);
    log.error("===========================");

    // 클라이언트에게는 간단한 메시지만 반환 (보안상 상세 정보는 숨김)
    return ResponseEntity
        .status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiResponse.error("서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요."));
  }

  /**
   * DM 방 없음 예외 처리
   */
  @ExceptionHandler(DmRoomNotFoundException.class)
  public ResponseEntity<ApiResponse<Void>> handleDmRoomNotFoundException(DmRoomNotFoundException e) {
    log.warn("DM 방을 찾을 수 없음: {}", e.getMessage());
    return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error(e.getMessage()));
  }

  /**
   * DM 접근 권한 없음 예외 처리
   */
  @ExceptionHandler(DmAccessDeniedException.class)
  public ResponseEntity<ApiResponse<Void>> handleDmAccessDeniedException(DmAccessDeniedException e) {
    log.warn("DM 접근 권한 없음: {}", e.getMessage());
    return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(ApiResponse.error(e.getMessage()));
  }

  /**
   * DM 정책 위반 예외 처리
   * - 본인에게 DM 시도
   * - 상호 팔로우 아님
   * - 다른 방 메시지 읽음 처리 시도 등
   */
  @ExceptionHandler(DmPolicyViolationException.class)
  public ResponseEntity<ApiResponse<Void>> handleDmPolicyViolationException(DmPolicyViolationException e) {
    log.warn("DM 정책 위반: {}", e.getMessage());
    return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(e.getMessage()));
  }

  /**
   * DM 메시지 유효성 예외 처리
   */
  @ExceptionHandler(DmMessageValidationException.class)
  public ResponseEntity<ApiResponse<Void>> handleDmMessageValidationException(DmMessageValidationException e) {
    log.warn("DM 메시지 유효성 오류: {}", e.getMessage());
    return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(e.getMessage()));
  }

  /**
   * 공통 리소스 없음 예외 처리
   */
  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ApiResponse<Void>> handleResourceNotFoundException(ResourceNotFoundException e) {
    log.warn("리소스를 찾을 수 없음: {}", e.getMessage());
    return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error(e.getMessage()));
  }
}



