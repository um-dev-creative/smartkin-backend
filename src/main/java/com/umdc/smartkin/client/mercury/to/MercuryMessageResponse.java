package com.umdc.smartkin.client.mercury.to;

import java.util.UUID;

/// What mercury's `POST /api/v1/messages` answers.
///
/// @param id      id to ask the delivery state with
/// @param status  `OPENED` = queued
/// @param message free text from mercury
public record MercuryMessageResponse(UUID id, String status, String message) {
}
