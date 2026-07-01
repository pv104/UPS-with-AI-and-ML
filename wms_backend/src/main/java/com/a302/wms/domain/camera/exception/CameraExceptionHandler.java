package com.a302.wms.domain.camera.exception;

import com.a302.wms.global.response.BaseExceptionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class CameraExceptionHandler {

    @ExceptionHandler(CameraException.class)
    public BaseExceptionResponse cameraException(CameraException e) {
        log.error("[Camera] {} - {}", e.getResponseEnum(), e.getExceptionMessage(), e);
        return new BaseExceptionResponse(e.getResponseEnum());
    }
}