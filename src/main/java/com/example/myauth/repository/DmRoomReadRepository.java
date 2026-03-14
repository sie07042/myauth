package com.example.myauth.repository;

import com.example.myauth.entity.DmRoomRead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DmRoomReadRepository extends JpaRepository<DmRoomRead, Long> {

    Optional<DmRoomRead> findByRoomIdAndUserId(Long roomId, Long userId);
}