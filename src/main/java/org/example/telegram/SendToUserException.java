package org.example.telegram;

public class SendToUserException extends Exception {
    public SendToUserException(String message) {
        super(message);
    }
}