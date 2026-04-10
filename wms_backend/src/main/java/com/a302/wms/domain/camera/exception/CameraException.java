package com.a302.wms.domain.camera.exception;

import com.a302.wms.global.constant.ResponseEnum;
import lombok.Getter;

@Getter
public class CameraException extends RuntimeException {

    private final ResponseEnum responseEnum;
    private final String exceptionMessage;

    public CameraException(ResponseEnum responseEnum, String exceptionMessage) {
        super(exceptionMessage);
        this.responseEnum = responseEnum;
        this.exceptionMessage = exceptionMessage;
    }

    public CameraException(ResponseEnum responseEnum, String exceptionMessage, Throwable cause) {
        super(exceptionMessage, cause);
        this.responseEnum = responseEnum;
        this.exceptionMessage = exceptionMessage;
    }

    public static class MlServerException extends CameraException {
        public MlServerException(String detail, Throwable cause) {
            super(ResponseEnum.CAMERA_ML_SERVER_ERROR,
                    ResponseEnum.CAMERA_ML_SERVER_ERROR.getMessage() + " " + detail, cause);
        }
        public MlServerException(String detail) {
            super(ResponseEnum.CAMERA_ML_SERVER_ERROR,
                    ResponseEnum.CAMERA_ML_SERVER_ERROR.getMessage() + " " + detail);
        }
    }

    public static class MlResponseParseException extends CameraException {
        public MlResponseParseException(String rawResponse, Throwable cause) {
            super(ResponseEnum.CAMERA_ML_RESPONSE_PARSE_ERROR,
                    ResponseEnum.CAMERA_ML_RESPONSE_PARSE_ERROR.getMessage() + " raw=" + rawResponse, cause);
        }
        public MlResponseParseException(String detail) {
            super(ResponseEnum.CAMERA_ML_RESPONSE_PARSE_ERROR,
                    ResponseEnum.CAMERA_ML_RESPONSE_PARSE_ERROR.getMessage() + " " + detail);
        }
    }

    public static class UnknownCrimeTypeException extends CameraException {
        public UnknownCrimeTypeException(String crimeType) {
            super(ResponseEnum.CAMERA_UNKNOWN_CRIME_TYPE,
                    ResponseEnum.CAMERA_UNKNOWN_CRIME_TYPE.getMessage() + " type=" + crimeType);
        }
    }

    public static class VideoProcessException extends CameraException {
        public VideoProcessException(String detail, Throwable cause) {
            super(ResponseEnum.CAMERA_VIDEO_PROCESS_ERROR,
                    ResponseEnum.CAMERA_VIDEO_PROCESS_ERROR.getMessage() + " " + detail, cause);
        }
    }
}