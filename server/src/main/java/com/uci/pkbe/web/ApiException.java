package com.uci.pkbe.web;

public class ApiException extends RuntimeException {

    private final String error;
    private final int status;

    public ApiException(int status, String error, String message) {
        super(message);
        this.status = status;
        this.error = error;
    }

    public String getError() {
        return error;
    }

    public int getStatus() {
        return status;
    }

    public static ApiException unauthorized() {
        return new ApiException(401, "UNAUTHORIZED", "Missing or invalid session");
    }

    public static ApiException badRequest(String message) {
        return new ApiException(400, "BAD_REQUEST", message);
    }

    public static ApiException conflict(String error, String message) {
        return new ApiException(409, error, message);
    }

    public static ApiException unprocessable(String error, String message) {
        return new ApiException(422, error, message);
    }
}
