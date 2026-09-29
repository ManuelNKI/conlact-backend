package com.conlact.conlact_backend.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidAssociationImageUrlValidator implements ConstraintValidator<ValidAssociationImageUrl, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // Use @NotBlank or @NotNull for null check
        }
        return AssociationImageUrlValidator.isValid(value);
    }
}
