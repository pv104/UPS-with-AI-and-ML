package com.a302.wms.domain.camera.service;

import com.a302.wms.domain.camera.dto.CameraResponse;
import com.a302.wms.domain.camera.entity.Camera;
import com.a302.wms.domain.camera.exception.CameraException;
import com.a302.wms.domain.camera.mapper.CameraMapper;
import com.a302.wms.domain.camera.repository.CameraRepository;
import com.a302.wms.domain.camera.resource.MultipartInputStreamFileResource;
import com.a302.wms.domain.notification.entity.Notification;
import com.a302.wms.domain.notification.service.NotificationServiceImpl;
import com.a302.wms.domain.s3.service.S3ServiceImpl;
import com.a302.wms.domain.store.repository.StoreRepository;
import com.a302.wms.domain.user.repository.UserRepository;
import com.a302.wms.global.constant.CrimePreventionEnum;
import com.a302.wms.global.constant.NotificationTypeEnum;
import com.a302.wms.global.constant.ProductConstant;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;

@Slf4j
@Service
@RequiredArgsConstructor
public class CameraServiceImpl {

  private final UserRepository userRepository;
  private final StoreRepository storeRepository;
  private final NotificationServiceImpl notificationServiceImpl;
  private final CameraRepository cameraRepository;
  private final S3ServiceImpl s3ServiceImpl;
  private final ObjectMapper objectMapper;

  @Value("${cctv-base-url}")
  private String cctvBaseUrl;

  @Transactional
  public String processVideoUpload(MultipartFile file, Long userId, Long storeId) {
    log.info("[Camera] processVideoUpload 시작 - userId={}, storeId={}, file={}",
            userId, storeId, file.getOriginalFilename());

    String crimeType = uploadVideo(file);
    String crimeValue = toCrimeValue(crimeType);

    Notification notification =
            Notification.builder()
                    .user(userRepository.findById(userId).orElse(null))
                    .store(storeRepository.findById(storeId).orElse(null))
                    .isImportant(false)
                    .isRead(false)
                    .notificationTypeEnum(NotificationTypeEnum.CRIME_PREVENTION)
                    .message(ProductConstant.DEFAULT_NOTIFICATION_CRIME_MESSAGE)
                    .build();

    notificationServiceImpl.save(notification);

    try {
      cameraRepository.save(
              Camera.builder()
                      .notification(notification)
                      .title(file.getOriginalFilename())
                      .url(s3ServiceImpl.generatePresignedUrl(file.getOriginalFilename()).downloadLink())
                      .category(crimeValue)
                      .build());
    } catch (CameraException e) {
      throw e;
    } catch (Exception e) {
      throw new CameraException.VideoProcessException(
              "Camera 엔티티 저장 실패: " + file.getOriginalFilename(), e);
    }

    log.info("[Camera] processVideoUpload 완료 - crimeValue={}", crimeValue);
    return crimeValue;
  }

  String uploadVideo(MultipartFile file) {
    log.info("[Camera] uploadVideo - file={}", file.getOriginalFilename());
    WebClient client =
            WebClient.builder()
                    .baseUrl(cctvBaseUrl)
                    .defaultHeader("Content-Type", MediaType.MULTIPART_FORM_DATA_VALUE)
                    .clientConnector(
                            new ReactorClientHttpConnector(
                                    HttpClient.create().option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 300000)))
                    .build();

    MultiValueMap<String, Object> body = createBody(file);

    String response;
    try {
      response =
              client
                      .post()
                      .contentType(MediaType.MULTIPART_FORM_DATA)
                      .body(BodyInserters.fromMultipartData(body))
                      .retrieve()
                      .bodyToMono(String.class)
                      .block();
    } catch (WebClientRequestException e) {
      throw new CameraException.MlServerException(
              "ML 서버 연결 실패: " + cctvBaseUrl, e);
    } catch (WebClientResponseException e) {
      throw new CameraException.MlServerException(
              "ML 서버 응답 오류 (HTTP " + e.getStatusCode() + ")", e);
    }

    if (response == null) {
      throw new CameraException.MlServerException("ML 서버로부터 응답이 없습니다.");
    }

    return parseCrimeType(response);
  }

  String parseCrimeType(String response) {
    try {
      JsonNode jsonObject = objectMapper.readTree(response);
      if (jsonObject == null || !jsonObject.has("class")) {
        throw new CameraException.MlResponseParseException(
                "\"class\" 키가 존재하지 않습니다. response=" + response);
      }
      return jsonObject.get("class").asText();
    } catch (CameraException e) {
      throw e;
    } catch (Exception e) {
      throw new CameraException.MlResponseParseException(response, e);
    }
  }

  String toCrimeValue(String crimeType) {
    if (crimeType == null) {
      throw new CameraException.MlResponseParseException("범죄 유형이 null 입니다.");
    }
    String value = CrimePreventionEnum.getCrimeValue(crimeType);
    if (value == null) {
      throw new CameraException.UnknownCrimeTypeException(crimeType);
    }
    return value;
  }

  MultiValueMap<String, Object> createBody(MultipartFile file) {
    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    try {
      Resource fileResource =
              new MultipartInputStreamFileResource(file.getInputStream(), file.getOriginalFilename());
      body.add("data", fileResource);
    } catch (IOException e) {
      throw new CameraException.VideoProcessException(
              "파일 스트림 생성 실패: " + file.getOriginalFilename(), e);
    }
    return body;
  }

  public CameraResponse findCameraByNotificationId(Long notificationId) {
    return CameraMapper.toCameraResponse(cameraRepository.findByNotificationId(notificationId));
  }

  public List<CameraResponse> findAllByStoreId(Long storeId) {
    return cameraRepository.findAllByStoreId(storeId);
  }
}