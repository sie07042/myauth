package com.example.myauth.exception;

public class DmAccessDeniedException extends RuntimeException {
    public DmAccessDeniedException(String message) {
        super(message);
    }
}