package com.example.myauth.exception;

public class DmPolicyViolationException extends RuntimeException {
  public DmPolicyViolationException(String message) {
    super(message);
  }
}