package com.example.demo.common.exception;

public class ConflictException extends BusinessException {
  public ConflictException(String message) {
    super(ErrorCode.CONFLICT, message);
  }
}
