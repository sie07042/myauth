package com.example.myauth.controller;

import com.example.myauth.config.AppProperties;
import com.example.myauth.dto.ApiResponse;
import com.example.myauth.dto.LoginRequest;
import com.example.myauth.dto.LoginResponse;
import com.example.myauth.dto.SignupRequest;
import com.example.myauth.dto.TokenRefreshRequest;
import com.example.myauth.dto.TokenRefreshResponse;
import com.example.myauth.service.AuthService;
import com.example.myauth.util.ClientTypeDetector;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api")  // 모든 API 엔드포인트에 /api 접두사 추가
@RequiredArgsConstructor
public class AuthController {
  private final AuthService authService;
  private final AppProperties appProperties;

  @GetMapping("/health")
  public ResponseEntity<ApiResponse<Void>> health() {
    return ResponseEntity.ok(ApiResponse.success("Auth Service is running"));
  }


  /**
   * 회원가입
   * 성공 시 201 Created 반환
   * 실패 시 예외 발생 (GlobalExceptionHandler에서 처리)
   */
  @PostMapping("/signup")
  public ResponseEntity<ApiResponse<Void>> signup(@Valid @RequestBody SignupRequest signupRequest) {
    log.info("다음 이메일로 회원가입 요청: {}", signupRequest.getEmail());

    // 회원가입 처리 (실패 시 예외 던짐)
    authService.registerUser(signupRequest);

    return ResponseEntity
        .status(HttpStatus.CREATED)
        .body(ApiResponse.success("회원가입이 완료되었습니다."));
  }


  /**
   * 로그인
   * 성공 시 200 OK와 함께 토큰 정보 반환
   * 실패 시 예외 발생 (GlobalExceptionHandler에서 처리)
   */
  @PostMapping("/old_login")
  public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest loginRequest) {
    log.info("로그인 요청: {}", loginRequest.getEmail());

    // 로그인 처리 (실패 시 예외 던짐)
    LoginResponse loginResponse = authService.login(loginRequest);

    return ResponseEntity.ok(ApiResponse.success("로그인 성공", loginResponse));
  }


  /**
   * 로그인 (하이브리드 방식 - 웹/모바일 구분)
   *
   *  Spring Security 표준 방식으로 로그인 처리
   * 클라이언트 타입에 따라 토큰 전송 방식을 다르게 처리:
   * - 웹 브라우저: Refresh Token을 HTTP-only 쿠키로 전송 (XSS 방어)
   * - 모바일 앱: 모든 토큰을 JSON 응답 바디로 전송
   *
   * 성공 시 200 OK와 함께 토큰 정보 반환
   * 실패 시 예외 발생 (GlobalExceptionHandler에서 처리)
   */
  @PostMapping("/login")
  public ResponseEntity<ApiResponse<LoginResponse>> loginEx(
      @Valid @RequestBody LoginRequest loginRequest,
      HttpServletRequest request,
      HttpServletResponse response
  ) {
    log.info("로그인 요청 (loginEx): {}", loginRequest.getEmail());

    // 1️⃣ 클라이언트 타입 감지
    boolean isWebClient = ClientTypeDetector.isWebClient(request);
    String clientType = ClientTypeDetector.getClientTypeString(request);
    log.info("감지된 클라이언트 타입: {}", clientType);

    // 디버깅: User-Agent 정보 로깅
    ClientTypeDetector.logUserAgent(request);

    // 2️⃣ 로그인 처리 (실패 시 예외 던짐)
    LoginResponse loginResponse = authService.loginEx(loginRequest);

    // 3️⃣ 웹 클라이언트면 Refresh Token을 쿠키로 설정
    if (isWebClient) {
      log.info("웹 클라이언트 감지 → Refresh Token을 HTTP-only 쿠키로 설정");

      // Refresh Token을 HTTP-only 쿠키로 설정
      // ResponseCookie를 사용하여 SameSite와 Domain 속성 명시
      // - SameSite=Lax: CSRF 방어 + 일반적인 웹 사용 가능
      // - Domain=localhost: 포트 무관하게 모든 localhost에서 쿠키 공유 (localhost:5173과 localhost:9080 모두 접근 가능)
      ResponseCookie refreshTokenCookie = ResponseCookie
          .from("refreshToken", loginResponse.getRefreshToken())
          .httpOnly(true)   // JavaScript 접근 불가 (XSS 방어)
          .secure(appProperties.getCookie().isSecure())  // 환경별 동적 설정 (개발: false, 프로덕션: true)
          .path("/")        // 모든 경로에서 쿠키 전송
          .maxAge(7 * 24 * 60 * 60)  // 7일 (초 단위)
          .sameSite("Lax")  // CSRF 방어 + 일반 네비게이션에서 쿠키 전송 허용
          .domain("localhost")  // 포트 무관하게 localhost 전체에서 쿠키 공유
          .build();

      log.info("쿠키 설정: HttpOnly=true, Secure={}, Path=/, MaxAge=7일, SameSite=Lax, Domain=localhost",
          appProperties.getCookie().isSecure());

      response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString());
      log.info("Refresh Token을 쿠키에 설정 완료");

      // 응답 바디에서 Refresh Token 제거 (쿠키로 전송했으므로)
      loginResponse.setRefreshToken(null);
      log.info("응답 바디에서 Refresh Token 제거 (보안 강화)");
    } else {
      // 4️⃣ 모바일 클라이언트면 Refresh Token을 JSON 응답에 포함
      log.info("모바일 클라이언트 감지 → Refresh Token을 JSON 응답에 포함");
    }

    // 5️⃣ 로그인 성공 응답 반환
    log.info("로그인 성공 (loginEx): {}, 클라이언트: {}", loginRequest.getEmail(), clientType);
    return ResponseEntity.ok(ApiResponse.success("로그인 성공", loginResponse));
  }


  /**
   * Access Token 갱신 (하이브리드 방식 - 웹/모바일 구분)
   * Refresh Token으로 새로운 Access Token을 발급받는다
   * 클라이언트 타입에 따라 Refresh Token을 다른 곳에서 읽는다:
   * - 웹 브라우저: HTTP-only 쿠키에서 Refresh Token 읽기
   * - 모바일 앱: 요청 바디에서 Refresh Token 읽기
   *
   * 성공 시 200 OK와 함께 새 Access Token 반환
   * 실패 시 예외 발생 (GlobalExceptionHandler에서 처리)
   */
  @PostMapping("/refresh")
  public ResponseEntity<ApiResponse<TokenRefreshResponse>> refresh(
      HttpServletRequest request,
      @RequestBody(required = false) @Valid TokenRefreshRequest body
  ) {
    log.info("Access Token 갱신 요청");

    // 1️⃣ 클라이언트 타입 감지
    boolean isWebClient = ClientTypeDetector.isWebClient(request);
    String clientType = ClientTypeDetector.getClientTypeString(request);
    log.info("클라이언트 타입: {}", clientType);

    // 2️⃣ 클라이언트 타입에 따라 Refresh Token 추출
    String refreshToken;
    if (isWebClient) {
      log.info("웹 클라이언트 → 쿠키에서 Refresh Token 읽기");
      refreshToken = extractRefreshTokenFromCookies(request);

      // 쿠키에 토큰이 없는 경우, 요청 바디에서 시도 (테스트/교육용)
      if (refreshToken == null) {
        log.warn("쿠키에 Refresh Token이 없음");

        // 테스트/교육용: 요청 바디에서 Refresh Token 읽기 시도
        refreshToken = extractRefreshTokenFromBody(body);
        if (refreshToken != null) {
          log.info("테스트/교육용: 웹 클라이언트이지만 요청 바디에서 Refresh Token 사용");
        } else {
          return ResponseEntity
              .status(HttpStatus.UNAUTHORIZED)
              .body(ApiResponse.error("Refresh Token이 없습니다. 다시 로그인해주세요."));
        }
      }
    } else {
      log.info("모바일 클라이언트 → 요청 바디에서 Refresh Token 읽기");
      refreshToken = extractRefreshTokenFromBody(body);

      // 요청 바디에 토큰이 없는 경우 에러 응답
      if (refreshToken == null) {
        log.warn("요청 바디에 Refresh Token이 없음");
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error("Refresh Token은 필수입니다."));
      }
    }

    // 3️⃣ Refresh Token으로 새 Access Token 발급 (실패 시 예외 던짐)
    TokenRefreshResponse refreshResponse = authService.refreshAccessToken(refreshToken);

    // 4️⃣ 응답 반환
    log.info("Access Token 갱신 성공");
    return ResponseEntity.ok(ApiResponse.success("Access Token이 갱신되었습니다", refreshResponse));
  }

  /**
   * HTTP 쿠키에서 Refresh Token을 추출한다
   *
   * @param request HTTP 요청 객체
   * @return Refresh Token (없으면 null)
   */
  private String extractRefreshTokenFromCookies(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }

    for (Cookie cookie : cookies) {
      if ("refreshToken".equals(cookie.getName())) {
        log.debug("쿠키에서 Refresh Token 발견");
        return cookie.getValue();
      }
    }

    return null;
  }

  /**
   * 요청 바디에서 Refresh Token을 추출한다
   *
   * @param body 토큰 갱신 요청 바디
   * @return Refresh Token (없거나 유효하지 않으면 null)
   */
  private String extractRefreshTokenFromBody(TokenRefreshRequest body) {
    if (body == null || body.getRefreshToken() == null || body.getRefreshToken().isBlank()) {
      return null;
    }

    log.debug("요청 바디에서 Refresh Token 발견");
    return body.getRefreshToken();
  }
}
