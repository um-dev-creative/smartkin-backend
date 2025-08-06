package com.umdc.smartkin.kafka.producer;

import com.umdc.smartkin.kafka.to.EmailMessageTO;
import org.springframework.http.HttpStatus;

public interface EmailMessageProducerService {


    default void sendMessage(EmailMessageTO emailMessageTO) {
        throw new UnsupportedOperationException(HttpStatus.NOT_IMPLEMENTED.getReasonPhrase());
    }
}
