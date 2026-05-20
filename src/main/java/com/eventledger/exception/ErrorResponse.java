package com.eventledger.exception;

import java.util.List;

public class ErrorResponse {

    private int status;
    private String error;
    private List<String> messages;

    public ErrorResponse(int status, String error, List<String> messages) {
        this.status = status;
        this.error = error;
        this.messages = messages;
    }

    public int getStatus() { return status; }
    public String getError() { return error; }
    public List<String> getMessages() { return messages; }
}
