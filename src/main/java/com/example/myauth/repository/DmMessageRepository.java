package com.example.myauth.repository;

import com.example.myauth.entity.DmMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface DmMessageRepository extends JpaRepository<DmMessage, Long> {

    Slice<DmMessage> findByRoomIdOrderByIdDesc(Long roomId, Pageable pageable);

    Slice<DmMessage> findByRoomIdAndIdLessThanOrderByIdDesc(Long roomId, Long beforeId, Pageable pageable);

    @Query("""
            select count(m)
            from DmMessage m
            where m.room.id = :roomId
              and (:lastReadMessageId is null or m.id > :lastReadMessageId)
              and m.sender.id <> :userId
              and m.isDeleted = false
            """)
    long countUnreadMessages(Long roomId, Long userId, Long lastReadMessageId);

    @Query("""
            select m
            from DmMessage m
            join fetch m.sender
            where m.id = :messageId
            """)
    java.util.Optional<DmMessage> findByIdWithSender(Long messageId);

    @Query("""
            select m
            from DmMessage m
            join fetch m.sender
            where m.room.id = :roomId
            order by m.id desc
            """)
    Slice<DmMessage> findMessagesWithSenderByRoomId(Long roomId, Pageable pageable);

    @Query("""
            select m
            from DmMessage m
            join fetch m.sender
            where m.room.id = :roomId
              and m.id < :beforeId
            order by m.id desc
            """)
    Slice<DmMessage> findMessagesWithSenderByRoomIdAndBeforeId(Long roomId, Long beforeId, Pageable pageable);
}
