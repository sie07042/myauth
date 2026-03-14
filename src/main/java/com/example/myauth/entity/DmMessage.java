package com.example.myauth.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "dm_messages",
        indexes = {
                @Index(name = "idx_dm_message_room_id_id", columnList = "room_id, id"),
                @Index(name = "idx_dm_message_sender_created_at", columnList = "sender_id, created_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DmMessage {

    private static final int MAX_CONTENT_LENGTH = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private DmRoom room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Lob
    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private DmMessage(DmRoom room, User sender, String content) {
        validate(room, sender, content);
        this.room = room;
        this.sender = sender;
        this.content = content.trim();
        this.isDeleted = false;
    }

    public static DmMessage of(DmRoom room, User sender, String content) {
        return DmMessage.builder()
                .room(room)
                .sender(sender)
                .content(content)
                .build();
    }

    public void markDeleted() {
        this.isDeleted = true;
    }

    private void validate(DmRoom room, User sender, String content) {
        if (room == null) {
            throw new IllegalArgumentException("메시지가 속할 DM 방이 없습니다.");
        }
        if (sender == null || sender.getId() == null) {
            throw new IllegalArgumentException("메시지 발신자가 올바르지 않습니다.");
        }
        if (!room.isParticipant(sender.getId())) {
            throw new IllegalArgumentException("DM 방 참여자만 메시지를 보낼 수 있습니다.");
        }
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("메시지 내용은 비어 있을 수 없습니다.");
        }
        if (content.trim().length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("메시지 길이는 2000자를 초과할 수 없습니다.");
        }
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}