package com.example.myauth.dto.dm;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class DmRoomCreateRequest {

    @NotNull(message = "대상 사용자 ID는 필수입니다.")
    private Long targetUserId;
}