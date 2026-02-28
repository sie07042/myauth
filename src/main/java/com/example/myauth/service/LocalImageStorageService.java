package com.example.myauth.service;

import com.example.myauth.dto.ImageUploadResponse;
import com.example.myauth.exception.FileStorageException;
import com.example.myauth.exception.InvalidFileException;
import com.example.myauth.exception.InvalidFileException.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * 로컬 파일 시스템에 이미지를 저장하는 서비스
 * 프로덕션 환경에서는 AWS S3 등으로 교체 권장
 */
@Service
@Primary  // ⭐ 여러 구현체가 있을 때 이것을 우선적으로 주입
@Slf4j
public class LocalImageStorageService implements ImageStorageService {

  /** 허용되는 이미지 MIME 타입 목록 */
  private static final List<String> ALLOWED_IMAGE_TYPES = Arrays.asList(
      "image/jpeg",
      "image/jpg",
      "image/png",
      "image/gif",
      "image/webp"
  );

  /** 최대 파일 크기 (10MB) */
  private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

  /** 업로드된 파일이 저장될 디렉토리 경로 */
  private final Path uploadPath;

  /** 파일에 접근할 수 있는 베이스 URL */
  private final String baseUrl;

  /**
   * 생성자 - application.yml의 설정값을 주입받아 초기화
   *
   * @param uploadDir 파일 저장 디렉토리 (예: ./uploads)
   * @param baseUrl 파일 접근 베이스 URL (예: http://localhost:9080/uploads)
   */
  public LocalImageStorageService(
      @Value("${file.upload.dir:./uploads}") String uploadDir,
      @Value("${file.upload.base-url:http://localhost:9080/uploads}") String baseUrl
  ) {
    this.uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
    this.baseUrl = baseUrl;

    try {
      // 업로드 디렉토리가 없으면 생성
      Files.createDirectories(this.uploadPath);
      log.info("이미지 업로드 디렉토리 초기화 완료: {}", this.uploadPath);
    } catch (IOException e) {
      log.error("업로드 디렉토리 생성 실패: {}", this.uploadPath, e);
      throw new FileStorageException("업로드 디렉토리를 생성할 수 없습니다.", e);
    }
  }

  /**
   * 이미지 파일을 저장하고 접근 가능한 URL을 반환
   *
   * @param file 업로드된 이미지 파일
   * @return 저장된 이미지 정보 (URL 포함)
   * @throws RuntimeException 파일 저장 실패 시
   */
  @Override
  public ImageUploadResponse store(MultipartFile file) {
    log.info("이미지 업로드 시작 - 파일명: {}, 크기: {} bytes", file.getOriginalFilename(), file.getSize());

    // 1️⃣ 파일 검증
    validateFile(file);

    // 2️⃣ 고유한 파일명 생성 (UUID 사용)
    String originalFileName = file.getOriginalFilename();
    String fileExtension = getFileExtension(originalFileName);
    String fileName = UUID.randomUUID().toString() + fileExtension;

    try {
      // 3️⃣ 파일 저장 경로 생성
      Path targetPath = this.uploadPath.resolve(fileName);

      // 4️⃣ 파일 저장
      Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
      log.info("이미지 저장 완료 - 파일명: {}, 경로: {}", fileName, targetPath);

      // 5️⃣ 접근 가능한 URL 생성

      String imageUrl = baseUrl + "/" + fileName;
//      String imageUrl = "/uploads/" + fileName;

      // 6️⃣ 응답 DTO 생성
      return ImageUploadResponse.builder()
          .imageUrl(imageUrl)
          .fileName(fileName)
          .originalFileName(originalFileName)
          .fileSize(file.getSize())
          .contentType(file.getContentType())
          .build();

    } catch (IOException e) {
      log.error("이미지 저장 실패 - 파일명: {}", originalFileName, e);
      throw new FileStorageException("이미지 저장에 실패했습니다.", e);
    }
  }

  /**
   * 저장된 이미지 파일을 삭제
   *
   * @param fileName 삭제할 파일명
   * @throws RuntimeException 파일 삭제 실패 시
   */
  @Override
  public void delete(String fileName) {
    try {
      Path filePath = this.uploadPath.resolve(fileName).normalize();

      // 보안: 업로드 디렉토리 밖의 파일 삭제 방지
      if (!filePath.startsWith(this.uploadPath)) {
        log.warn("잘못된 파일 경로로 삭제 시도: {}", fileName);
        throw new InvalidFileException(ErrorCode.INVALID_PATH, "잘못된 파일 경로입니다.");
      }

      Files.deleteIfExists(filePath);
      log.info("이미지 삭제 완료 - 파일명: {}", fileName);

    } catch (IOException e) {
      log.error("이미지 삭제 실패 - 파일명: {}", fileName, e);
      throw new FileStorageException("이미지 삭제에 실패했습니다.", e);
    }
  }

  /**
   * 파일 검증 (타입, 크기, null 체크)
   *
   * @param file 검증할 파일
   * @throws RuntimeException 검증 실패 시
   */
  private void validateFile(MultipartFile file) {
    // null 체크
    if (file == null || file.isEmpty()) {
      throw new InvalidFileException(ErrorCode.EMPTY_FILE, "파일이 비어있습니다.");
    }

    // 파일 크기 검증
    if (file.getSize() > MAX_FILE_SIZE) {
      throw new InvalidFileException(ErrorCode.FILE_TOO_LARGE,
          "파일 크기가 너무 큽니다. 최대 10MB까지 업로드 가능합니다.");
    }

    // 파일 타입 검증
    String contentType = file.getContentType();
    if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase())) {
      throw new InvalidFileException(ErrorCode.UNSUPPORTED_TYPE,
          "지원하지 않는 파일 형식입니다. (지원: JPEG, PNG, GIF, WEBP)");
    }

    // 파일명 검증 (경로 조작 방지)
    String originalFileName = file.getOriginalFilename();
    if (originalFileName == null || originalFileName.contains("..")) {
      throw new InvalidFileException(ErrorCode.INVALID_FILENAME, "잘못된 파일명입니다.");
    }
  }

  /**
   * 파일 확장자 추출
   *
   * @param fileName 파일명
   * @return 확장자 (예: .jpg)
   */
  private String getFileExtension(String fileName) {
    if (fileName == null) {
      return "";
    }

    int lastDotIndex = fileName.lastIndexOf('.');
    if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
      return fileName.substring(lastDotIndex);
    }

    return "";
  }
}
