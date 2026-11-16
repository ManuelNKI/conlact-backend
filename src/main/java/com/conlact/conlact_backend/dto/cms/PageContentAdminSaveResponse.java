package com.conlact.conlact_backend.dto.cms;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@Builder
public record PageContentAdminSaveResponse(
        @JsonProperty("ok") boolean ok,
        @JsonProperty("data") PageContentResponse data,
        @JsonProperty("mensaje") String message
) {
}
