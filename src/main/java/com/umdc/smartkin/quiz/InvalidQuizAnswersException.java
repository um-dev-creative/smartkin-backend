package com.umdc.smartkin.quiz;

/// The submitted answers do not match the quiz (unknown question or option, or a question answered twice).
public class InvalidQuizAnswersException extends RuntimeException {

    /// Creates the exception.
    ///
    /// @param message what is wrong with the answers
    public InvalidQuizAnswersException(String message) {
        super(message);
    }
}
