package com.example.myauth.controller;


import com.example.myauth.dto.ApiResponse;
import com.example.myauth.dto.dm.*;
import com.example.myauth.entity.User;
import com.example.myauth.service.DmService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/dm")
@RequiredArgsConstructor
public class DmController {

    private final DmService dmService;

    /**
     * 대화방 생성 또는 기존 방 조회
     * 성공 시 200 OK 반환
     * 실패 시 예외 발생 (GlobalExceptionHandler에서 처리)
     */
    @PostMapping("/rooms")
    public ResponseEntity<ApiResponse<DmRoomResponse>> createOrGetRoom(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody DmRoomCreateRequest request
    ) {
        log.info("DM 방 생성/조회 요청: requesterId={}, targetUserId={}", user.getId(), request.getTargetUserId());

        DmRoomResponse response = dmService.createOrGetRoom(user.getId(), request.getTargetUserId());

        log.info("DM 방 생성/조회 성공: requesterId={}, targetUserId={}, roomId={}",
                user.getId(), request.getTargetUserId(), response.getRoomId());

        return ResponseEntity.ok(ApiResponse.success("DM 방 조회/생성 성공", response));
    }

    /**
     * 내 대화방 목록 조회
     * 성공 시 200 OK 반환
     * 실패 시 예외 발생 (GlobalExceptionHandler에서 처리)
     */
    @GetMapping("/rooms")
    public ResponseEntity<ApiResponse<Page<DmRoomListItemResponse>>> getMyRooms(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        log.info("내 DM 방 목록 조회 요청: userId={}, page={}, size={}", user.getId(), page, size);

        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Order.desc("lastMessageAt"),
                        Sort.Order.desc("id")
                )
        );

        Page<DmRoomListItemResponse> response = dmService.getMyRooms(user.getId(), pageable);

        log.info("내 DM 방 목록 조회 성공: userId={}, totalElements={}", user.getId(), response.getTotalElements());

        return ResponseEntity.ok(ApiResponse.success("내 DM 방 목록 조회 성공", response));
    }

    /**
     * 특정 대화방 메시지 목록 조회
     * beforeId가 없으면 최신 메시지부터 조회
     * 성공 시 200 OK 반환
     * 실패 시 예외 발생 (GlobalExceptionHandler에서 처리)
     */
    @GetMapping("/rooms/{roomId}/messages")
    public ResponseEntity<ApiResponse<Slice<DmMessageResponse>>> getMessages(
            @AuthenticationPrincipal User user,
            @PathVariable Long roomId,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(defaultValue = "30") int size
    ) {
        log.info("DM 메시지 목록 조회 요청: userId={}, roomId={}, beforeId={}, size={}",
                user.getId(), roomId, beforeId, size);

        Slice<DmMessageResponse> response = dmService.getMessages(user.getId(), roomId, beforeId, size);

        log.info("DM 메시지 목록 조회 성공: userId={}, roomId={}, returnedSize={}, hasNext={}",
                user.getId(), roomId, response.getNumberOfElements(), response.hasNext());

        return ResponseEntity.ok(ApiResponse.success("DM 메시지 목록 조회 성공", response));
    }

    /**
     * 메시지 전송
     * 성공 시 200 OK 반환
     * 실패 시 예외 발생 (GlobalExceptionHandler에서 처리)
     */
    @PostMapping("/rooms/{roomId}/messages")
    public ResponseEntity<ApiResponse<DmMessageResponse>> sendMessage(
            @AuthenticationPrincipal User user,
            @PathVariable Long roomId,
            @Valid @RequestBody DmMessageCreateRequest request
    ) {
        log.info("DM 메시지 전송 요청: userId={}, roomId={}", user.getId(), roomId);

        DmMessageResponse response = dmService.sendMessage(user.getId(), roomId, request);

        log.info("DM 메시지 전송 성공: userId={}, roomId={}, messageId={}",
                user.getId(), roomId, response.getMessageId());

        return ResponseEntity.ok(ApiResponse.success("DM 메시지 전송 성공", response));
    }

    /**
     * 읽음 처리
     * 성공 시 200 OK 반환
     * 실패 시 예외 발생 (GlobalExceptionHandler에서 처리)
     */
    @PostMapping("/rooms/{roomId}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @AuthenticationPrincipal User user,
            @PathVariable Long roomId,
            @Valid @RequestBody DmReadRequest request
    ) {
        log.info("DM 읽음 처리 요청: userId={}, roomId={}, lastReadMessageId={}",
                user.getId(), roomId, request.getLastReadMessageId());

        dmService.markAsRead(user.getId(), roomId, request);

        log.info("DM 읽음 처리 성공: userId={}, roomId={}, lastReadMessageId={}",
                user.getId(), roomId, request.getLastReadMessageId());

        return ResponseEntity.ok(ApiResponse.success("DM 읽음 처리 성공"));
    }
}