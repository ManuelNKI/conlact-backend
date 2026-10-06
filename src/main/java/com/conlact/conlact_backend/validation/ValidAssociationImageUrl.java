package com.conlact.conlact_backend.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = ValidAssociationImageUrlValidator.class)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE, ElementType.CONSTRUCTOR, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidAssociationImageUrl {

    String message() default "La URL debe ser HTTPS válida y apuntar a una imagen (.jpg, .jpeg, .png, .webp) con un máximo de 2048 caracteres";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
