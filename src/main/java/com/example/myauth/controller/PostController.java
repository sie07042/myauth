package com.example.myauth.controller;

import com.example.myauth.dto.ApiResponse;
import com.example.myauth.dto.post.*;
import com.example.myauth.entity.User;
import com.example.myauth.service.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 게시글 컨트롤러
 * 게시글 CRUD API 엔드포인트 제공
 *
 * 【API 목록】
 * - POST   /api/posts           : 게시글 작성
 * - PUT    /api/posts/{id}      : 게시글 수정
 * - DELETE /api/posts/{id}      : 게시글 삭제
 * - GET    /api/posts/{id}      : 게시글 상세 조회
 * - GET    /api/posts           : 공개 게시글 목록
 * - GET    /api/posts/me        : 내 게시글 목록
 * - GET    /api/users/{userId}/posts : 특정 사용자 게시글 목록
 */
@Slf4j
@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

  private final PostService postService;

  // ===== 게시글 작성 =====

  /**
   * 게시글 작성 (텍스트만)
   *
   * POST /api/posts
   * Content-Type: application/json
   *
   * 【요청 예시】
   * {
   *   "content": "오늘 맛있는 저녁 먹었어요!",
   *   "visibility": "PUBLIC"
   * }
   */
  @PostMapping
  public ResponseEntity<ApiResponse<PostResponse>> createPost(
      @AuthenticationPrincipal User user,
      @Valid @RequestBody PostCreateRequest request
  ) {
    log.info("게시글 작성 요청 - userId: {}", user.getId());

    PostResponse response = postService.createPost(user.getId(), request);

    return ResponseEntity
        .status(HttpStatus.CREATED)
        .body(ApiResponse.success("게시글이 작성되었습니다.", response));
  }

  /**
   * 게시글 작성 (이미지 포함)
   *
   * POST /api/posts/with-images
   * Content-Type: multipart/form-data
   *
   * 【요청 파라미터】
   * - post: JSON 형식의 게시글 정보 (PostCreateRequest)
   * - images: 이미지 파일들 (선택)
   */
  @PostMapping(value = "/with-images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ApiResponse<PostResponse>> createPostWithImages(
      @AuthenticationPrincipal User user,
      @Valid @RequestPart("post") PostCreateRequest request,
      @RequestPart(value = "images", required = false) List<MultipartFile> images
  ) {
    log.info("게시글 작성 요청 (이미지 포함) - userId: {}, 이미지 개수: {}",
        user.getId(), images != null ? images.size() : 0);

    PostResponse response = postService.createPost(user.getId(), request, images);

    return ResponseEntity
        .status(HttpStatus.CREATED)
        .body(ApiResponse.success("게시글이 작성되었습니다.", response));
  }

  // ===== 게시글 수정 =====

  /**
   * 게시글 수정
   *
   * PUT /api/posts/{id}
   *
   * 【권한】
   * - 작성자 본인만 수정 가능
   */
  @PutMapping("/{id}")
  public ResponseEntity<ApiResponse<PostResponse>> updatePost(
      @AuthenticationPrincipal User user,
      @PathVariable Long id,
      @Valid @RequestBody PostUpdateRequest request
  ) {
    log.info("게시글 수정 요청 - userId: {}, postId: {}", user.getId(), id);

    PostResponse response = postService.updatePost(user.getId(), id, request);

    return ResponseEntity.ok(ApiResponse.success("게시글이 수정되었습니다.", response));
  }

  // ===== 게시글 삭제 =====

  /**
   * 게시글 삭제 (Soft Delete)
   *
   * DELETE /api/posts/{id}
   *
   * 【권한】
   * - 작성자 본인만 삭제 가능
   */
  @DeleteMapping("/{id}")
  public ResponseEntity<ApiResponse<Void>> deletePost(
      @AuthenticationPrincipal User user,
      @PathVariable Long id
  ) {
    log.info("게시글 삭제 요청 - userId: {}, postId: {}", user.getId(), id);

    postService.deletePost(user.getId(), id);

    return ResponseEntity.ok(ApiResponse.success("게시글이 삭제되었습니다.", null));
  }

  // ===== 게시글 조회 =====

  /**
   * 게시글 상세 조회
   *
   * GET /api/posts/{id}
   *
   * 【응답】
   * - 게시글 정보 (작성자, 이미지 포함)
   * - 좋아요/북마크 여부 (로그인 사용자 기준)
   * - 조회수 자동 증가 (작성자 본인 제외)
   */
  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<PostResponse>> getPost(
      @AuthenticationPrincipal User user,
      @PathVariable Long id
  ) {
    log.info("게시글 상세 조회 요청 - userId: {}, postId: {}", user.getId(), id);

    PostResponse response = postService.getPost(user.getId(), id);

    return ResponseEntity.ok(ApiResponse.success("게시글 조회 성공", response));
  }

  // ===== 게시글 목록 조회 =====

  /**
   * 공개 게시글 목록 조회 (피드)
   *
   * GET /api/posts?page=0&size=10
   *
   * 【쿼리 파라미터】
   * - page: 페이지 번호 (0부터 시작, 기본값 0)
   * - size: 페이지 크기 (기본값 10, 최대 50)
   */
  @GetMapping
  public ResponseEntity<ApiResponse<Page<PostListResponse>>> getPosts(
      @AuthenticationPrincipal User user,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size
  ) {
    // 페이지 크기 제한
    if (size > 50) size = 50;

    Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
    Page<PostListResponse> posts = postService.getVisiblePosts(
        user != null ? user.getId() : null,
        pageable
    );

    return ResponseEntity.ok(ApiResponse.success("게시글 목록 조회 성공", posts));
  }

  /**
   * 내 게시글 목록 조회
   *
   * GET /api/posts/me?page=0&size=10
   */
  @GetMapping("/me")
  public ResponseEntity<ApiResponse<Page<PostListResponse>>> getMyPosts(
      @AuthenticationPrincipal User user,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size
  ) {
    if (size > 50) size = 50;

    Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
    Page<PostListResponse> posts = postService.getMyPosts(user.getId(), pageable);

    return ResponseEntity.ok(ApiResponse.success("내 게시글 목록 조회 성공", posts));
  }

  /**
   * 특정 사용자의 게시글 목록 조회
   *
   * GET /api/users/{userId}/posts?page=0&size=10
   *
   * 【참고】
   * - 이 엔드포인트는 UserController에 위치할 수도 있음
   * - 여기서는 게시글 관련이므로 PostController에 배치
   */
  @GetMapping("/user/{userId}")
  public ResponseEntity<ApiResponse<Page<PostListResponse>>> getUserPosts(
      @AuthenticationPrincipal User user,
      @PathVariable Long userId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size
  ) {
    if (size > 50) size = 50;

    Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
    Page<PostListResponse> posts = postService.getPostsByUser(
        user != null ? user.getId() : null,
        userId,
        pageable
    );

    return ResponseEntity.ok(ApiResponse.success("사용자 게시글 목록 조회 성공", posts));
  }
}
