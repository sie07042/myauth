package com.example.myauth.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "dm_rooms",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_dm_room_user_pair", columnNames = {"user1_id", "user2_id"})
        },
        indexes = {
                @Index(name = "idx_dm_room_user1", columnList = "user1_id"),
                @Index(name = "idx_dm_room_user2", columnList = "user2_id"),
                @Index(name = "idx_dm_room_last_message_at", columnList = "last_message_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DmRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 항상 작은 userId를 user1에 저장
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user1_id", nullable = false)
    private User user1;

    /**
     * 항상 큰 userId를 user2에 저장
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user2_id", nullable = false)
    private User user2;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_message_id")
    private DmMessage lastMessage;

    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private DmRoom(User user1, User user2) {
        validateUsers(user1, user2);

        if (user1.getId() < user2.getId()) {
            this.user1 = user1;
            this.user2 = user2;
        } else {
            this.user1 = user2;
            this.user2 = user1;
        }
    }

    public static DmRoom of(User userA, User userB) {
        return DmRoom.builder()
                .user1(userA)
                .user2(userB)
                .build();
    }

    public boolean isParticipant(Long userId) {
        return user1.getId().equals(userId) || user2.getId().equals(userId);
    }

    public User getPeerUser(Long myUserId) {
        if (user1.getId().equals(myUserId)) {
            return user2;
        }
        if (user2.getId().equals(myUserId)) {
            return user1;
        }
        throw new IllegalArgumentException("해당 사용자는 이 DM 방의 참여자가 아닙니다.");
    }

    public void updateLastMessage(DmMessage lastMessage) {
        this.lastMessage = lastMessage;
        this.lastMessageAt = lastMessage.getCreatedAt();
    }

    private void validateUsers(User user1, User user2) {
        if (user1 == null || user2 == null) {
            throw new IllegalArgumentException("DM 방 생성에 필요한 사용자가 없습니다.");
        }
        if (user1.getId() == null || user2.getId() == null) {
            throw new IllegalArgumentException("저장되지 않은 User 엔티티로 DM 방을 생성할 수 없습니다.");
        }
        if (user1.getId().equals(user2.getId())) {
            throw new IllegalArgumentException("본인과의 DM 방은 생성할 수 없습니다.");
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
