package edu.JavaCourse.CurrencyConverterBot.telegram;

public class SendToUserException extends Exception {
    public SendToUserException(String message) {
        super(message);
    }
}