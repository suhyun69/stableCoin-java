package com.example.demo.common.exception;
public class ServiceUnavailableException extends BusinessException {
    public ServiceUnavailableException(String message,Throwable cause) {super(ErrorCode.UNAVAILABLE,message,cause);}
}
