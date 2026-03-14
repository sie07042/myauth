package com.example.myauth.repository;

import com.example.myauth.entity.DmRoom;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DmRoomRepository extends JpaRepository<DmRoom, Long> {

    Optional<DmRoom> findByUser1IdAndUser2Id(Long user1Id, Long user2Id);

    @Query("""
            select r
            from DmRoom r
            where r.user1.id = :meId or r.user2.id = :meId
            order by
                case when r.lastMessageAt is null then 1 else 0 end,
                r.lastMessageAt desc,
                r.id desc
            """)
    Page<DmRoom> findMyRooms(Long meId, Pageable pageable);

    @Query("""
            select r
            from DmRoom r
            join fetch r.user1
            join fetch r.user2
            left join fetch r.lastMessage
            where r.id = :roomId
            """)
    Optional<DmRoom> findByIdWithUsersAndLastMessage(Long roomId);

    @Query("""
            select case when count(r) > 0 then true else false end
            from DmRoom r
            where r.id = :roomId
              and (r.user1.id = :userId or r.user2.id = :userId)
            """)
    boolean existsByIdAndParticipant(Long roomId, Long userId);

    Optional<DmRoom> findByUser1_IdAndUser2_Id(Long user1Id, Long user2Id);
}