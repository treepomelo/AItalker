package com.cn.app.product;

import com.cn.app.msg.Result;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.UUID;

@Order(-10)
@RestControllerAdvice
public class ApiProblemHandler {
    @ExceptionHandler(ApiProblem.class)
    public ResponseEntity<Result> handle(ApiProblem e) {
        return ResponseEntity.status(e.status).body(Result.error(e.getMessage(), e.status)
                .set("errorCode", e.errorCode).set("requestId", UUID.randomUUID().toString()));
    }
}
