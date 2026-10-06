package com.conlact.conlact_backend.dto.recipe;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RecipeStepResponse {

    private Integer step;

    private String instruction;
}