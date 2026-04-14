package com.a302.wms.domain.camera.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.a302.wms.domain.camera.exception.CameraException;
import com.a302.wms.domain.camera.repository.CameraRepository;
import com.a302.wms.domain.notification.service.NotificationServiceImpl;
import com.a302.wms.domain.s3.service.S3ServiceImpl;
import com.a302.wms.domain.store.repository.StoreRepository;
import com.a302.wms.domain.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;

class CameraServiceImplTest {

    private CameraServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CameraServiceImpl(
                mock(UserRepository.class),
                mock(StoreRepository.class),
                mock(NotificationServiceImpl.class),
                mock(CameraRepository.class),
                mock(S3ServiceImpl.class),
                new ObjectMapper()
        );
    }

    @Nested
    @DisplayName("parseCrimeType - ML 서버 JSON 응답 파싱")
    class ParseCrimeType {

        @Test
        @DisplayName("정상 JSON에서 class 값 추출")
        void extractsClassFromValidJson() {
            assertThat(service.parseCrimeType("{\"class\": \"theft\"}"))
                    .isEqualTo("theft");
        }

        @Test
        @DisplayName("class 값에 대소문자가 섞여 있어도 그대로 반환")
        void preservesOriginalCasing() {
            assertThat(service.parseCrimeType("{\"class\": \"FIRE\"}"))
                    .isEqualTo("FIRE");
        }

        @Test
        @DisplayName("추가 필드가 있어도 class 값만 추출")
        void ignoresExtraFields() {
            assertThat(service.parseCrimeType(
                    "{\"class\": \"smoke\", \"confidence\": 0.95, \"timestamp\": 123}"))
                    .isEqualTo("smoke");
        }

        @Test
        @DisplayName("class 키가 없으면 MlResponseParseException 예외 발생")
        void throwsWhenClassKeyMissing() {
            assertThatThrownBy(() -> service.parseCrimeType("{\"result\": \"theft\"}"))
                    .isInstanceOf(CameraException.MlResponseParseException.class)
                    .hasMessageContaining("\"class\" 키가 존재하지 않습니다");
        }

        @Test
        @DisplayName("빈 JSON 객체이면 MlResponseParseException 예외 발생")
        void throwsOnEmptyJsonObject() {
            assertThatThrownBy(() -> service.parseCrimeType("{}"))
                    .isInstanceOf(CameraException.MlResponseParseException.class);
        }

        @Test
        @DisplayName("유효하지 않은 JSON이면 MlResponseParseException 예외 발생")
        void throwsOnInvalidJson() {
            assertThatThrownBy(() -> service.parseCrimeType("not-a-json"))
                    .isInstanceOf(CameraException.MlResponseParseException.class);
        }

        @Test
        @DisplayName("빈 문자열이면 MlResponseParseException 예외 발생")
        void throwsOnEmptyString() {
            assertThatThrownBy(() -> service.parseCrimeType(""))
                    .isInstanceOf(CameraException.MlResponseParseException.class);
        }
    }

    @Nested
    @DisplayName("toCrimeValue - 범죄 유형 키 : 한글 매핑")
    class ToCrimeValue {

        @Test
        @DisplayName("theft : 절도")
        void mapsTheft() {
            assertThat(service.toCrimeValue("theft")).isEqualTo("절도");
        }

        @Test
        @DisplayName("broken : 파손")
        void mapsBroken() {
            assertThat(service.toCrimeValue("broken")).isEqualTo("파손");
        }

        @Test
        @DisplayName("fire : 방화")
        void mapsFire() {
            assertThat(service.toCrimeValue("fire")).isEqualTo("방화");
        }

        @Test
        @DisplayName("smoke : 흡연")
        void mapsSmoke() {
            assertThat(service.toCrimeValue("smoke")).isEqualTo("흡연");
        }

        @Test
        @DisplayName("fall : 실신")
        void mapsFall() {
            assertThat(service.toCrimeValue("fall")).isEqualTo("실신");
        }

        @Test
        @DisplayName("대소문자를 구분하지 않음 (THEFT : 절도)")
        void caseInsensitive() {
            assertThat(service.toCrimeValue("THEFT")).isEqualTo("절도");
        }

        @Test
        @DisplayName("정의되지 않은 범죄 유형이면 UnknownCrimeTypeException 예외 발생")
        void throwsOnUnknownType() {
            assertThatThrownBy(() -> service.toCrimeValue("unknown_crime"))
                    .isInstanceOf(CameraException.UnknownCrimeTypeException.class)
                    .hasMessageContaining("unknown_crime");
        }

        @Test
        @DisplayName("null이면 MlResponseParseException 예외 발생")
        void throwsOnNull() {
            assertThatThrownBy(() -> service.toCrimeValue(null))
                    .isInstanceOf(CameraException.MlResponseParseException.class);
        }
    }

    @Nested
    @DisplayName("createBody - 멀티파트 폼 데이터 생성")
    class CreateBody {

        @Test
        @DisplayName("정상 파일로 data 키가 포함된 바디를 생성한다")
        void createsBodyWithDataKey() throws Exception {
            MultipartFile file = mockFile("test.mp4", new byte[]{1, 2, 3});
            MultiValueMap<String, Object> body = service.createBody(file);
            assertThat(body).containsKey("data");
            assertThat(body.get("data")).hasSize(1);
        }

        @Test
        @DisplayName("파일 스트림 오류 시 VideoProcessException 예외 발생")
        void throwsOnStreamError() throws Exception {
            MultipartFile file = mock(MultipartFile.class);
            when(file.getOriginalFilename()).thenReturn("bad.mp4");
            when(file.getInputStream()).thenThrow(new IOException("disk error"));
            assertThatThrownBy(() -> service.createBody(file))
                    .isInstanceOf(CameraException.VideoProcessException.class)
                    .hasMessageContaining("bad.mp4");
        }
    }

    @Nested
    @DisplayName("parseCrimeType : toCrimeValue 통합 흐름")
    class EndToEndParsing {

        @Test
        @DisplayName("ML 서버 응답 JSON -> 한글 범죄 유형 변환")
        void fullPipelineSuccess() {
            String crimeType = service.parseCrimeType("{\"class\": \"theft\"}");
            String crimeValue = service.toCrimeValue(crimeType);
            assertThat(crimeValue).isEqualTo("절도");
        }

        @Test
        @DisplayName("ML 서버에서 Unknown 유형 반환시, 파싱 성공 / 매핑 실패")
        void parseSucceedsButMappingFails() {
            String crimeType = service.parseCrimeType("{\"class\": \"arson\"}");
            assertThatThrownBy(() -> service.toCrimeValue(crimeType))
                    .isInstanceOf(CameraException.UnknownCrimeTypeException.class)
                    .hasMessageContaining("arson");
        }
    }

    private static MultipartFile mockFile(String filename, byte[] content) throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn(filename);
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(content));
        return file;
    }
}