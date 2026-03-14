package com.example.myauth.service;


import com.example.myauth.dto.dm.*;
import com.example.myauth.entity.DmMessage;
import com.example.myauth.entity.DmRoom;
import com.example.myauth.entity.DmRoomRead;
import com.example.myauth.entity.User;
import com.example.myauth.exception.DmAccessDeniedException;
import com.example.myauth.exception.DmPolicyViolationException;
import com.example.myauth.exception.DmRoomNotFoundException;
import com.example.myauth.exception.ResourceNotFoundException;
import com.example.myauth.repository.DmMessageRepository;
import com.example.myauth.repository.DmRoomReadRepository;
import com.example.myauth.repository.DmRoomRepository;
import com.example.myauth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DmService {

    private static final int MAX_MESSAGE_LENGTH = 2000;
    private static final int DEFAULT_MESSAGE_PAGE_SIZE = 30;

    private final DmRoomRepository dmRoomRepository;
    private final DmMessageRepository dmMessageRepository;
    private final DmRoomReadRepository dmRoomReadRepository;
    private final UserRepository userRepository;
    /**
     * DM 방 생성 또는 기존 방 조회
     * - 자기 자신과의 DM 금지
     * - 대상 유저 존재 여부 확인
     * - 상호 팔로우 관계 확인
     * - 기존 방이 있으면 반환, 없으면 생성
     */
    @Transactional
    public DmRoomResponse createOrGetRoom(Long meId, Long targetUserId) {
        log.info("DM 방 생성/조회 요청: meId={}, targetUserId={}", meId, targetUserId);

        validateSelfDm(meId, targetUserId);

        User me = getUserOrThrow(meId);
        User targetUser = getUserOrThrow(targetUserId);

        Long user1Id = Math.min(meId, targetUserId);
        Long user2Id = Math.max(meId, targetUserId);

        Optional<DmRoom> existingRoom = dmRoomRepository.findByUser1_IdAndUser2_Id(user1Id, user2Id);

        if (existingRoom.isPresent()) {
            log.info("기존 DM 방 반환: roomId={}", existingRoom.get().getId());
            return toDmRoomResponse(existingRoom.get(), meId);
        }

        DmRoom room = DmRoom.of(me, targetUser);
        DmRoom savedRoom = dmRoomRepository.save(room);

        // 읽음 상태 row를 양쪽 모두 미리 생성해두면 이후 처리 편함
        dmRoomReadRepository.save(DmRoomRead.of(savedRoom, me));
        dmRoomReadRepository.save(DmRoomRead.of(savedRoom, targetUser));

        log.info("새 DM 방 생성 성공: roomId={}, meId={}, targetUserId={}", savedRoom.getId(), meId, targetUserId);
        return toDmRoomResponse(savedRoom, meId);
    }

    /**
     * 내 DM 방 목록 조회
     * - lastMessageAt DESC 정렬
     * - peer user, last message preview, unread count 포함
     */
    @Transactional(readOnly = true)
    public Page<DmRoomListItemResponse> getMyRooms(Long meId, Pageable pageable) {
        log.info("내 DM 방 목록 조회 요청: meId={}, page={}, size={}",
                meId, pageable.getPageNumber(), pageable.getPageSize());

        Pageable fixedPageable = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(
                        Sort.Order.desc("lastMessageAt"),
                        Sort.Order.desc("id")
                )
        );

        Page<DmRoom> rooms = dmRoomRepository.findMyRooms(meId, fixedPageable);

        Page<DmRoomListItemResponse> response = rooms.map(room -> {
            User peerUser = room.getPeerUser(meId);

            Long lastReadMessageId = dmRoomReadRepository.findByRoomIdAndUserId(room.getId(), meId)
                    .map(DmRoomRead::getLastReadMessageId)
                    .orElse(null);

            long unreadCount = dmMessageRepository.countUnreadMessages(room.getId(), meId, lastReadMessageId);

            return DmRoomListItemResponse.builder()
                    .roomId(room.getId())
                    .peerUserId(peerUser.getId())
                    .peerUserName(peerUser.getName())
                    .peerProfileImage(peerUser.getProfileImage())
                    .lastMessagePreview(extractLastMessagePreview(room))
                    .lastMessageAt(room.getLastMessageAt())
                    .unreadCount(unreadCount)
                    .build();
        });

        log.info("내 DM 방 목록 조회 성공: meId={}, totalElements={}", meId, response.getTotalElements());
        return response;
    }

    /**
     * 메시지 목록 조회
     * - 참여자 검증
     * - beforeId 없으면 최신부터 조회
     * - 최신순 Slice 반환
     */
    @Transactional(readOnly = true)
    public Slice<DmMessageResponse> getMessages(Long meId, Long roomId, Long beforeId, int size) {
        log.info("DM 메시지 목록 조회 요청: meId={}, roomId={}, beforeId={}, size={}",
                meId, roomId, beforeId, size);

        DmRoom room = getRoomOrThrow(roomId);
        validateRoomParticipant(room, meId);

        int pageSize = normalizeMessagePageSize(size);
        Pageable pageable = PageRequest.of(0, pageSize);

        Slice<DmMessage> messages;
        if (beforeId == null) {
            messages = dmMessageRepository.findMessagesWithSenderByRoomId(roomId, pageable);
        } else {
            messages = dmMessageRepository.findMessagesWithSenderByRoomIdAndBeforeId(roomId, beforeId, pageable);
        }

        Slice<DmMessageResponse> response = messages.map(message -> toDmMessageResponse(message, meId));

        log.info("DM 메시지 목록 조회 성공: meId={}, roomId={}, returnedSize={}, hasNext={}",
                meId, roomId, response.getNumberOfElements(), response.hasNext());

        return response;
    }

    /**
     * 메시지 전송
     * - 참여자 검증
     * - 상호 팔로우 정책 재검증
     * - 메시지 저장 후 room lastMessage 갱신
     */
    @Transactional
    public DmMessageResponse sendMessage(Long meId, Long roomId, DmMessageCreateRequest request) {
        log.info("DM 메시지 전송 요청: meId={}, roomId={}", meId, roomId);

        DmRoom room = getRoomOrThrow(roomId);
        validateRoomParticipant(room, meId);

        User sender = getUserOrThrow(meId);
        validateMessageContent(request.getContent());

        DmMessage message = DmMessage.of(room, sender, request.getContent());
        DmMessage savedMessage = dmMessageRepository.save(message);

        room.updateLastMessage(savedMessage);
        dmRoomRepository.save(room);

        log.info("DM 메시지 전송 성공: meId={}, roomId={}, messageId={}", meId, roomId, savedMessage.getId());
        return toDmMessageResponse(savedMessage, meId);
    }

    /**
     * 읽음 처리
     * - 참여자 검증
     * - 해당 room의 메시지인지 검증
     * - 기존 lastReadMessageId보다 클 때만 갱신
     */
    @Transactional
    public void markAsRead(Long meId, Long roomId, DmReadRequest request) {
        log.info("DM 읽음 처리 요청: meId={}, roomId={}, lastReadMessageId={}",
                meId, roomId, request.getLastReadMessageId());

        DmRoom room = getRoomOrThrow(roomId);
        validateRoomParticipant(room, meId);

        DmMessage message = dmMessageRepository.findById(request.getLastReadMessageId())
                .orElseThrow(() -> new ResourceNotFoundException("읽음 처리할 메시지를 찾을 수 없습니다."));

        if (!message.getRoom().getId().equals(roomId)) {
            log.warn("다른 방의 메시지로 읽음 처리 시도: meId={}, roomId={}, messageRoomId={}, messageId={}",
                    meId, roomId, message.getRoom().getId(), message.getId());
            throw new DmPolicyViolationException("해당 방의 메시지만 읽음 처리할 수 있습니다.");
        }

        DmRoomRead roomRead = dmRoomReadRepository.findByRoomIdAndUserId(roomId, meId)
                .orElseGet(() -> {
                    User user = getUserOrThrow(meId);
                    return dmRoomReadRepository.save(DmRoomRead.of(room, user));
                });

        roomRead.markAsRead(request.getLastReadMessageId());

        log.info("DM 읽음 처리 성공: meId={}, roomId={}, lastReadMessageId={}",
                meId, roomId, request.getLastReadMessageId());
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("존재하지 않는 사용자 조회 시도: userId={}", userId);
                    return new ResourceNotFoundException("사용자를 찾을 수 없습니다.");
                });
    }

    private DmRoom getRoomOrThrow(Long roomId) {
        return dmRoomRepository.findByIdWithUsersAndLastMessage(roomId)
                .orElseThrow(() -> {
                    log.warn("존재하지 않는 DM 방 조회 시도: roomId={}", roomId);
                    return new DmRoomNotFoundException("DM 방을 찾을 수 없습니다.");
                });
    }

    private void validateSelfDm(Long meId, Long targetUserId) {
        if (meId.equals(targetUserId)) {
            log.warn("본인에게 DM 시도: meId={}", meId);
            throw new DmPolicyViolationException("본인에게는 DM을 보낼 수 없습니다.");
        }
    }

    private void validateRoomParticipant(DmRoom room, Long meId) {
        if (!room.isParticipant(meId)) {
            log.warn("비참여자의 DM 방 접근 시도: meId={}, roomId={}", meId, room.getId());
            throw new DmAccessDeniedException("해당 DM 방에 접근할 권한이 없습니다.");
        }
    }

    private void validateMessageContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new DmPolicyViolationException("메시지 내용은 비어 있을 수 없습니다.");
        }

        if (content.trim().length() > MAX_MESSAGE_LENGTH) {
            throw new DmPolicyViolationException("메시지 길이는 2000자를 초과할 수 없습니다.");
        }
    }

    private int normalizeMessagePageSize(int size) {
        if (size <= 0) {
            return DEFAULT_MESSAGE_PAGE_SIZE;
        }
        return Math.min(size, 100);
    }

    private String extractLastMessagePreview(DmRoom room) {
        if (room.getLastMessage() == null || room.getLastMessage().getContent() == null) {
            return null;
        }

        String content = room.getLastMessage().getContent();
        return content.length() > 100 ? content.substring(0, 100) : content;
    }

    private DmRoomResponse toDmRoomResponse(DmRoom room, Long meId) {
        User peerUser = room.getPeerUser(meId);

        return DmRoomResponse.builder()
                .roomId(room.getId())
                .peerUserId(peerUser.getId())
                .peerUserName(peerUser.getName())
                .peerProfileImage(peerUser.getProfileImage())
                .lastMessagePreview(extractLastMessagePreview(room))
                .lastMessageAt(room.getLastMessageAt())
                .build();
    }

    private DmMessageResponse toDmMessageResponse(DmMessage message, Long meId) {
        return DmMessageResponse.builder()
                .messageId(message.getId())
                .roomId(message.getRoom().getId())
                .senderId(message.getSender().getId())
                .senderName(message.getSender().getName())
                .content(message.getContent())
                .createdAt(message.getCreatedAt())
                .mine(message.getSender().getId().equals(meId))
                .build();
    }
}
