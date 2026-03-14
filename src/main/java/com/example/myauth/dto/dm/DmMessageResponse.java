package com.example.myauth.dto.dm;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class DmMessageResponse {

    private Long messageId;
    private Long roomId;
    private Long senderId;
    private String senderName;
    private String content;
    private LocalDateTime createdAt;
    private boolean mine;
}