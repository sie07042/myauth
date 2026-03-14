package com.example.myauth.dto.dm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class DmMessageCreateRequest {

    @NotBlank(message = "메시지 내용은 비어 있을 수 없습니다.")
    @Size(max = 2000, message = "메시지 길이는 2000자를 초과할 수 없습니다.")
    private String content;
}
