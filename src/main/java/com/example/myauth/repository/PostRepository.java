package com.example.myauth.repository;

import com.example.myauth.entity.Post;
import com.example.myauth.entity.Visibility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 게시글 Repository
 * 게시글 조회, 저장, 수정, 삭제를 위한 데이터 접근 계층
 */
@Repository
public interface PostRepository extends JpaRepository<Post, Long> {

  // ===== 단건 조회 =====

  /**
   * 삭제되지 않은 게시글 조회
   * @param id 게시글 ID
   * @return 게시글 (Optional)
   */
  Optional<Post> findByIdAndIsDeletedFalse(Long id);

  /**
   * 게시글 상세 조회 (작성자 정보 함께 로드 - N+1 방지)
   * @param id 게시글 ID
   * @return 게시글 (작성자 포함)
   */
  @Query("SELECT p FROM Post p JOIN FETCH p.user WHERE p.id = :id AND p.isDeleted = false")
  Optional<Post> findByIdWithUser(@Param("id") Long id);

  /**
   * 게시글 상세 조회 (작성자 + 이미지 함께 로드)
   * @param id 게시글 ID
   * @return 게시글 (작성자, 이미지 포함)
   */
  @Query("SELECT DISTINCT p FROM Post p " +
         "JOIN FETCH p.user " +
         "LEFT JOIN FETCH p.images " +
         "WHERE p.id = :id AND p.isDeleted = false")
  Optional<Post> findByIdWithUserAndImages(@Param("id") Long id);

  // ===== 목록 조회 =====

  /**
   * 특정 사용자의 게시글 목록 (최신순)
   * @param userId 사용자 ID
   * @param pageable 페이지 정보
   * @return 게시글 페이지
   */
  Page<Post> findByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(
      Long userId, Pageable pageable);

  Page<Post> findByUserIdAndVisibilityAndIsDeletedFalseOrderByCreatedAtDesc(
      Long userId, Visibility visibility, Pageable pageable);

  Page<Post> findByUserIdAndVisibilityInAndIsDeletedFalseOrderByCreatedAtDesc(
      Long userId, java.util.Collection<Visibility> visibilities, Pageable pageable);

  /**
   * 공개 게시글 목록 (전체 피드용)
   * @param pageable 페이지 정보
   * @return 공개 게시글 페이지
   */
  @Query("SELECT p FROM Post p " +
         "WHERE p.isDeleted = false AND p.visibility = :visibility " +
         "ORDER BY p.createdAt DESC")
  Page<Post> findByVisibilityAndIsDeletedFalse(
      @Param("visibility") Visibility visibility, Pageable pageable);

  /**
   * 공개 게시글 목록 (작성자 정보 포함)
   * @param pageable 페이지 정보
   * @return 공개 게시글 페이지
   */
  @Query("SELECT p FROM Post p JOIN FETCH p.user " +
         "WHERE p.isDeleted = false AND p.visibility = 'PUBLIC' " +
         "ORDER BY p.createdAt DESC")
  Page<Post> findPublicPostsWithUser(Pageable pageable);

  @Query("SELECT p FROM Post p " +
         "WHERE p.isDeleted = false AND (" +
         "  p.visibility = 'PUBLIC' " +
         "  OR (:viewerId IS NOT NULL AND p.user.id = :viewerId) " +
         "  OR (:viewerId IS NOT NULL AND p.visibility = 'FOLLOWERS' AND EXISTS (" +
         "    SELECT 1 FROM Follow f WHERE f.follower.id = :viewerId AND f.following.id = p.user.id" +
         "  ))" +
         ") ORDER BY p.createdAt DESC")
  Page<Post> findVisiblePostsForViewer(@Param("viewerId") Long viewerId, Pageable pageable);

  // ===== 카운트 업데이트 =====

  /**
   * 조회수 증가
   * @param postId 게시글 ID
   */
  @Modifying
  @Query("UPDATE Post p SET p.viewCount = p.viewCount + 1 WHERE p.id = :postId")
  void incrementViewCount(@Param("postId") Long postId);

  /**
   * 좋아요 수 증가
   * @param postId 게시글 ID
   */
  @Modifying
  @Query("UPDATE Post p SET p.likeCount = p.likeCount + 1 WHERE p.id = :postId")
  void incrementLikeCount(@Param("postId") Long postId);

  /**
   * 좋아요 수 감소
   * @param postId 게시글 ID
   */
  @Modifying
  @Query("UPDATE Post p SET p.likeCount = p.likeCount - 1 WHERE p.id = :postId AND p.likeCount > 0")
  void decrementLikeCount(@Param("postId") Long postId);

  /**
   * 댓글 수 증가
   * @param postId 게시글 ID
   */
  @Modifying
  @Query("UPDATE Post p SET p.commentCount = p.commentCount + 1 WHERE p.id = :postId")
  void incrementCommentCount(@Param("postId") Long postId);

  /**
   * 댓글 수 감소
   * @param postId 게시글 ID
   */
  @Modifying
  @Query("UPDATE Post p SET p.commentCount = p.commentCount - 1 WHERE p.id = :postId AND p.commentCount > 0")
  void decrementCommentCount(@Param("postId") Long postId);

  // ===== 존재 여부 확인 =====

  /**
   * 게시글 존재 여부 확인 (삭제되지 않은 것만)
   * @param id 게시글 ID
   * @return 존재 여부
   */
  boolean existsByIdAndIsDeletedFalse(Long id);

  /**
   * 특정 사용자의 게시글인지 확인
   * @param id 게시글 ID
   * @param userId 사용자 ID
   * @return 소유 여부
   */
  boolean existsByIdAndUserIdAndIsDeletedFalse(Long id, Long userId);

  // ===== 피드 조회 =====

  /**
   * 홈 피드: 팔로잉 사용자의 게시글 조회
   * @param userId 로그인 사용자 ID
   * @param pageable 페이지 정보
   * @return 피드 게시글 페이지
   */
  @Query("SELECT p FROM Post p " +
      "WHERE p.user.id IN (SELECT f.following.id FROM Follow f WHERE f.follower.id = :userId) " +
      "AND p.isDeleted = false " +
      "AND (p.visibility = 'PUBLIC' OR p.visibility = 'FOLLOWERS') " +
      "ORDER BY p.createdAt DESC")
  Page<Post> findHomeFeed(@Param("userId") Long userId, Pageable pageable);

  /**
   * 홈 피드: 팔로잉 사용자 + 본인 게시글 조회
   * @param userId 로그인 사용자 ID
   * @param pageable 페이지 정보
   * @return 피드 게시글 페이지
   */
  @Query("SELECT p FROM Post p " +
      "WHERE (p.user.id IN (SELECT f.following.id FROM Follow f WHERE f.follower.id = :userId) " +
      "       OR p.user.id = :userId) " +
      "AND p.isDeleted = false " +
      "AND (p.visibility = 'PUBLIC' OR p.visibility = 'FOLLOWERS' OR p.user.id = :userId) " +
      "ORDER BY p.createdAt DESC")
  Page<Post> findHomeFeedWithMyPosts(@Param("userId") Long userId, Pageable pageable);

  /**
   * 탐색 피드: 공개 게시글 최신순
   * @param pageable 페이지 정보
   * @return 공개 게시글 페이지
   */
  @Query("SELECT p FROM Post p " +
      "WHERE p.isDeleted = false AND p.visibility = 'PUBLIC' " +
      "ORDER BY p.createdAt DESC")
  Page<Post> findPublicPostsOrderByCreatedAt(Pageable pageable);

  /**
   * 탐색 피드: 공개 게시글 인기순 (좋아요 순)
   * @param pageable 페이지 정보
   * @return 공개 게시글 페이지
   */
  @Query("SELECT p FROM Post p " +
      "WHERE p.isDeleted = false AND p.visibility = 'PUBLIC' " +
      "ORDER BY p.likeCount DESC, p.createdAt DESC")
  Page<Post> findPublicPostsOrderByLikeCount(Pageable pageable);

  /**
   * 탐색 피드: 공개 게시글 조회수순
   * @param pageable 페이지 정보
   * @return 공개 게시글 페이지
   */
  @Query("SELECT p FROM Post p " +
      "WHERE p.isDeleted = false AND p.visibility = 'PUBLIC' " +
      "ORDER BY p.viewCount DESC, p.createdAt DESC")
  Page<Post> findPublicPostsOrderByViewCount(Pageable pageable);

  /**
   * 추천 피드: 팔로우하지 않는 사용자의 인기 게시글
   * @param userId 로그인 사용자 ID
   * @param pageable 페이지 정보
   * @return 추천 게시글 페이지
   */
  @Query("SELECT p FROM Post p " +
      "WHERE p.user.id NOT IN (SELECT f.following.id FROM Follow f WHERE f.follower.id = :userId) " +
      "AND p.user.id <> :userId " +
      "AND p.isDeleted = false " +
      "AND p.visibility = 'PUBLIC' " +
      "ORDER BY p.likeCount DESC, p.createdAt DESC")
  Page<Post> findRecommendedPosts(@Param("userId") Long userId, Pageable pageable);
}
