package com.example.myauth.service;

import com.example.myauth.dto.ImageUploadResponse;
import com.example.myauth.dto.post.*;
import com.example.myauth.entity.*;
import com.example.myauth.exception.PostNotFoundException;
import com.example.myauth.exception.UnauthorizedAccessException;
import com.example.myauth.repository.FollowRepository;
import com.example.myauth.repository.PostImageRepository;
import com.example.myauth.repository.PostRepository;
import com.example.myauth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 게시글 서비스
 * 게시글 CRUD 및 관련 비즈니스 로직 처리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostService {

  private final PostRepository postRepository;
  private final PostImageRepository postImageRepository;
  private final FollowRepository followRepository;
  private final UserRepository userRepository;
  private final ImageStorageService imageStorageService;
  private final HashtagService hashtagService;
  private final MentionService mentionService;

  // ===== 게시글 작성 =====

  /**
   * 게시글 작성 (이미지 없음)
   *
   * @param userId 작성자 ID
   * @param request 게시글 작성 요청
   * @return 생성된 게시글 응답
   */
  @Transactional
  public PostResponse createPost(Long userId, PostCreateRequest request) {
    return createPost(userId, request, null);
  }

  /**
   * 게시글 작성 (이미지 포함)
   *
   * @param userId 작성자 ID
   * @param request 게시글 작성 요청
   * @param images 첨부 이미지 목록
   * @return 생성된 게시글 응답
   */
  @Transactional
  public PostResponse createPost(Long userId, PostCreateRequest request, List<MultipartFile> images) {
    log.info("게시글 작성 시작 - userId: {}, 이미지 개수: {}",
        userId, images != null ? images.size() : 0);

    // 1. 사용자 조회
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

    // 2. 게시글 엔티티 생성
    Post post = Post.builder()
        .user(user)
        .content(request.getContent())
        .visibility(request.getVisibility())
        .build();

    // 3. 게시글 저장
    Post savedPost = postRepository.save(post);
    Long postId = savedPost.getId();

    // 4. 이미지 업로드 및 저장
    if (images != null && !images.isEmpty()) {
      savePostImages(savedPost, images);
    }

    // 5. 해시태그 처리 (본문에서 해시태그 추출 및 연결)
    hashtagService.linkHashtagsToPost(savedPost, request.getContent());

    // 6. 멘션 처리 (본문에서 멘션 추출 및 저장)
    mentionService.processPostMentions(request.getContent(), postId, userId);

    log.info("게시글 작성 완료 - postId: {}", postId);

    // 5. 응답 반환 (이미지 포함하여 다시 조회)
    savedPost = postRepository.findByIdWithUserAndImages(postId)
        .orElseThrow(() -> new PostNotFoundException(postId));

    return PostResponse.from(savedPost);
  }

  /**
   * 게시글 이미지 저장
   */
  private void savePostImages(Post post, List<MultipartFile> images) {
    List<PostImage> postImages = new ArrayList<>();

    for (int i = 0; i < images.size(); i++) {
      MultipartFile file = images.get(i);

      // 이미지 업로드
      ImageUploadResponse uploadResponse = imageStorageService.store(file);

      // PostImage 엔티티 생성
      PostImage postImage = PostImage.builder()
          .post(post)
          .imageUrl(uploadResponse.getImageUrl())
          .thumbnailUrl(uploadResponse.getImageUrl()) // 썸네일은 추후 구현
          .sortOrder(i)
          .fileSize(uploadResponse.getFileSize().intValue())
          .mediaType(MediaType.IMAGE)
          .build();

      postImages.add(postImage);
    }

    // 이미지 일괄 저장
    postImageRepository.saveAll(postImages);
    log.info("게시글 이미지 저장 완료 - postId: {}, 이미지 개수: {}", post.getId(), postImages.size());
  }

  // ===== 게시글 수정 =====

  /**
   * 게시글 수정
   *
   * @param userId 요청 사용자 ID
   * @param postId 게시글 ID
   * @param request 수정 요청
   * @return 수정된 게시글 응답
   */
  @Transactional
  public PostResponse updatePost(Long userId, Long postId, PostUpdateRequest request) {
    log.info("게시글 수정 시작 - userId: {}, postId: {}", userId, postId);

    // 1. 게시글 조회
    Post post = postRepository.findByIdWithUserAndImages(postId)
        .orElseThrow(() -> new PostNotFoundException(postId));

    // 2. 권한 확인 (작성자 본인인지)
    if (!post.getUser().getId().equals(userId)) {
      throw new UnauthorizedAccessException("게시글을 수정할 권한이 없습니다.");
    }

    // 3. 필드 업데이트 (null이 아닌 경우만)
    if (request.getContent() != null) {
      post.setContent(request.getContent());

      // 3-1. 해시태그 업데이트
      hashtagService.updatePostHashtags(post, request.getContent());

      // 3-2. 멘션 업데이트
      mentionService.updatePostMentions(request.getContent(), postId, userId);
    }
    if (request.getVisibility() != null) {
      post.setVisibility(request.getVisibility());
    }

    // 4. 저장 (DynamicUpdate로 변경된 필드만 UPDATE)
    post = postRepository.save(post);

    log.info("게시글 수정 완료 - postId: {}", postId);

    return PostResponse.from(post);
  }

  // ===== 게시글 삭제 =====

  /**
   * 게시글 삭제 (Soft Delete)
   *
   * @param userId 요청 사용자 ID
   * @param postId 게시글 ID
   */
  @Transactional
  public void deletePost(Long userId, Long postId) {
    log.info("게시글 삭제 시작 - userId: {}, postId: {}", userId, postId);

    // 1. 게시글 조회
    Post post = postRepository.findByIdAndIsDeletedFalse(postId)
        .orElseThrow(() -> new PostNotFoundException(postId));

    // 2. 권한 확인 (작성자 본인인지)
    if (!post.getUser().getId().equals(userId)) {
      throw new UnauthorizedAccessException("게시글을 삭제할 권한이 없습니다.");
    }

    // 3. Soft Delete 처리
    post.softDelete();
    postRepository.save(post);

    log.info("게시글 삭제 완료 (Soft Delete) - postId: {}", postId);
  }

  // ===== 게시글 조회 =====

  /**
   * 게시글 상세 조회
   *
   * @param userId 요청 사용자 ID (조회수 증가 및 좋아요/북마크 여부 확인용)
   * @param postId 게시글 ID
   * @return 게시글 상세 응답
   */
  @Transactional
  public PostResponse getPost(Long userId, Long postId) {
    log.info("게시글 상세 조회 - userId: {}, postId: {}", userId, postId);

    // 1. 게시글 조회 (작성자, 이미지 함께 로드)
    Post post = postRepository.findByIdWithUserAndImages(postId)
        .orElseThrow(() -> new PostNotFoundException(postId));

    // 2. 공개 범위 확인
    if (!canViewPost(userId, post)) {
      throw new UnauthorizedAccessException("이 게시글을 볼 수 있는 권한이 없습니다.");
    }

    // 3. 조회수 증가 (작성자 본인이 아닌 경우에만)
    if (!post.getUser().getId().equals(userId)) {
      postRepository.incrementViewCount(postId);
    }

    // 4. 좋아요/북마크 여부 확인 (TODO: Phase 2-3에서 구현)
    boolean isLiked = false;
    boolean isBookmarked = false;

    return PostResponse.from(post, isLiked, isBookmarked);
  }

  /**
   * 게시글 공개 범위 확인
   */
  private boolean canViewPost(Long userId, Post post) {
    // 작성자 본인은 항상 볼 수 있음
    if (post.getUser().getId().equals(userId)) {
      return true;
    }

    // 공개 범위에 따른 접근 권한 확인
    switch (post.getVisibility()) {
      case PUBLIC:
        return true;
      case PRIVATE:
        return false; // 작성자만 볼 수 있음 (위에서 이미 확인)
      case FOLLOWERS:
        // TODO: 팔로우 여부 확인 (Phase 3에서 구현)
        return true; // 임시로 허용
      default:
        return false;
    }
  }

  // ===== 게시글 목록 조회 =====

  /**
   * 공개 게시글 목록 조회 (피드)
   *
   * @param pageable 페이지 정보
   * @return 게시글 목록 페이지
   */
  @Transactional(readOnly = true)
  public Page<PostListResponse> getPublicPosts(Pageable pageable) {
    log.info("공개 게시글 목록 조회 - page: {}, size: {}",
        pageable.getPageNumber(), pageable.getPageSize());

    Page<Post> posts = postRepository.findByVisibilityAndIsDeletedFalse(
        Visibility.PUBLIC, pageable);

    return posts.map(PostListResponse::from);
  }

  /**
   * 특정 사용자의 게시글 목록 조회
   *
   * @param userId 조회할 사용자 ID
   * @param pageable 페이지 정보
   * @return 게시글 목록 페이지
   */
  @Transactional(readOnly = true)
  public Page<PostListResponse> getPostsByUser(Long userId, Pageable pageable) {
    return getPostsByUser(null, userId, pageable);
  }

  @Transactional(readOnly = true)
  public Page<PostListResponse> getPostsByUser(Long viewerId, Long userId, Pageable pageable) {
    log.info("사용자별 게시글 목록 조회 - userId: {}, page: {}",
        userId, pageable.getPageNumber());

    Page<Post> posts;

    if (viewerId != null && viewerId.equals(userId)) {
      posts = postRepository.findByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(userId, pageable);
    } else if (viewerId != null && followRepository.existsByFollowerIdAndFollowingId(viewerId, userId)) {
      posts = postRepository.findByUserIdAndVisibilityInAndIsDeletedFalseOrderByCreatedAtDesc(
          userId,
          Arrays.asList(Visibility.PUBLIC, Visibility.FOLLOWERS),
          pageable
      );
    } else {
      posts = postRepository.findByUserIdAndVisibilityAndIsDeletedFalseOrderByCreatedAtDesc(
          userId,
          Visibility.PUBLIC,
          pageable
      );
    }

    return posts.map(PostListResponse::from);
  }

  /**
   * 내 게시글 목록 조회
   *
   * @param userId 로그인한 사용자 ID
   * @param pageable 페이지 정보
   * @return 게시글 목록 페이지
   */
  @Transactional(readOnly = true)
  public Page<PostListResponse> getMyPosts(Long userId, Pageable pageable) {
    return getPostsByUser(userId, pageable);
  }
}
