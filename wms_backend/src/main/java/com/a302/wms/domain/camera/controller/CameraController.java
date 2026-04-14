package com.a302.wms.domain.camera.controller;

import com.a302.wms.domain.camera.service.CameraServiceImpl;
import com.a302.wms.global.constant.ResponseEnum;
import com.a302.wms.global.response.BaseExceptionResponse;
import com.a302.wms.global.response.BaseSuccessResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/cameras")
@RequiredArgsConstructor
@Slf4j
public class CameraController {

  private final CameraServiceImpl cameraServiceImpl;

  @PostMapping
  public ResponseEntity<?> uploadVideo(
          @RequestParam("file") MultipartFile file,
          @RequestParam("userId") Long userId,
          @RequestParam("storeId") Long storeId) {
    if (file.isEmpty()) {
      BaseExceptionResponse errorResponse =
              new BaseExceptionResponse(
                      false,
                      ResponseEnum.CAMERA_NOT_FOUND.getStatusCode(),
                      HttpStatus.BAD_REQUEST.value(),
                      "파일이 비었습니다");
      return ResponseEntity.badRequest().body(errorResponse);
    }
    String processedResult = cameraServiceImpl.processVideoUpload(file, userId, storeId);
    return ResponseEntity.ok(new BaseSuccessResponse<>(processedResult));
  }

  @GetMapping
  public ResponseEntity<?> getCameraByNotificationId(
          @RequestParam(value = "notificationId", required = false) Long notificationId) {
    return ResponseEntity.ok(
            new BaseSuccessResponse<>(cameraServiceImpl.findCameraByNotificationId(notificationId)));
  }

  @GetMapping("/batch")
  public ResponseEntity<?> getAllCameras(
          @RequestParam(value = "storeId", required = false) Long storeId) {
    return ResponseEntity.ok(
            new BaseSuccessResponse<>(cameraServiceImpl.findAllByStoreId(storeId)));
  }
}