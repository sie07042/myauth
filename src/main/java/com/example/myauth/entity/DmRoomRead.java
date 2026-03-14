package com.example.myauth.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "dm_room_reads",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_dm_room_read_room_user", columnNames = {"room_id", "user_id"})
        },
        indexes = {
                @Index(name = "idx_dm_room_read_user_updated_at", columnList = "user_id, updated_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DmRoomRead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private DmRoom room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "last_read_message_id")
    private Long lastReadMessageId;

    @Column(name = "last_read_at")
    private LocalDateTime lastReadAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private DmRoomRead(DmRoom room, User user) {
        validate(room, user);
        this.room = room;
        this.user = user;
    }

    public static DmRoomRead of(DmRoom room, User user) {
        return DmRoomRead.builder()
                .room(room)
                .user(user)
                .build();
    }

    public void markAsRead(Long lastReadMessageId) {
        if (lastReadMessageId == null) {
            return;
        }

        if (this.lastReadMessageId == null || lastReadMessageId > this.lastReadMessageId) {
            this.lastReadMessageId = lastReadMessageId;
            this.lastReadAt = LocalDateTime.now();
        }
    }

    private void validate(DmRoom room, User user) {
        if (room == null) {
            throw new IllegalArgumentException("읽음 정보가 속할 DM 방이 없습니다.");
        }
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("읽음 정보를 저장할 사용자가 올바르지 않습니다.");
        }
        if (!room.isParticipant(user.getId())) {
            throw new IllegalArgumentException("DM 방 참여자만 읽음 정보를 가질 수 있습니다.");
        }
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
