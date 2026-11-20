package com.bugfarm.lunch.admin.web;

import com.bugfarm.lunch.admin.menu.DuplicateNameException;
import com.bugfarm.lunch.admin.menu.InvalidNameException;
import com.bugfarm.lunch.admin.menu.NotFoundException;
import java.util.Comparator;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps admin API errors to {@code {error}} bodies. Scoped to the admin package only. */
@RestControllerAdvice(basePackages = "com.bugfarm.lunch.admin")
public class ApiErrors {

    public record Error(String error) {}

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Error invalid(MethodArgumentNotValidException e) {
        FieldError first = e.getBindingResult().getFieldErrors().stream()
                .min(Comparator.comparing(FieldError::getField))
                .orElse(null);
        return new Error(first == null ? "Invalid request" : first.getField() + ": " + first.getDefaultMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Error unreadable(HttpMessageNotReadableException e) {
        return new Error("Invalid request body");
    }

    @ExceptionHandler(InvalidNameException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Error invalidName(InvalidNameException e) {
        return new Error(e.getMessage());
    }

    @ExceptionHandler(DuplicateNameException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Error duplicate(DuplicateNameException e) {
        return new Error("Name already exists");
    }

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Error notFound(NotFoundException e) {
        return new Error("Not found");
    }
}
