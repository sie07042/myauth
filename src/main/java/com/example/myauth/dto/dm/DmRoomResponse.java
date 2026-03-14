package com.example.myauth.dto.dm;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class DmRoomResponse {

    private Long roomId;
    private Long peerUserId;
    private String peerUserName;
    private String peerProfileImage;
    private String lastMessagePreview;
    private LocalDateTime lastMessageAt;
}