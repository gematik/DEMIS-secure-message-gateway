package de.gematik.demis.smg.service;

/*-
 * #%L
 * secure-message-gateway
 * %%
 * Copyright (C) 2025 - 2026 gematik GmbH
 * %%
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the
 * European Commission – subsequent versions of the EUPL (the "Licence").
 * You may not use this work except in compliance with the Licence.
 *
 * You find a copy of the Licence in the "Licence" file or at
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either expressed or implied.
 * In case of changes by gematik find details in the "Readme" file.
 *
 * See the Licence for the specific language governing permissions and limitations under the Licence.
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik,
 * find details in the "Readme" file.
 * #L%
 */

import static de.gematik.demis.smg.config.RabbitConfig.ROUTING_KEY_CONTROL;
import static de.gematik.demis.smg.config.RabbitConfig.ROUTING_KEY_SECURE;
import static de.gematik.demis.smg.constants.MessageHeaderConstants.HEADER_AUTHORIZATION;
import static de.gematik.demis.smg.constants.MessageHeaderConstants.HEADER_BATCH_ID;
import static de.gematik.demis.smg.constants.MessageHeaderConstants.HEADER_DOCUMENT_ID;
import static de.gematik.demis.smg.constants.MessageHeaderConstants.HEADER_FHIR_PACKAGE_VERSION;
import static de.gematik.demis.smg.constants.MessageHeaderConstants.HEADER_MESSAGE_ID;
import static de.gematik.demis.smg.constants.MessageHeaderConstants.HEADER_PACKAGE;
import static de.gematik.demis.smg.constants.MessageHeaderConstants.HEADER_TYPE;

import de.gematik.demis.service.base.security.crypto.AESEncryptionService;
import de.gematik.demis.smg.constants.MessageType;
import de.gematik.demis.smg.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.MessagePropertiesBuilder;
import org.springframework.stereotype.Service;

/** Service for handling incoming messages. */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

  private final AESEncryptionService encryptionService;
  private final MessageSender messageSender;

  /**
   * Generates a message with an ARS notification and sends it to the secure message queue.
   *
   * @param msg The content of the message.
   * @param messageId The identifier of the message.
   * @param batchId The identifier of the associated batch.
   * @param documentId The document identifier of the notification.
   * @param authorization The authorization header of the message.
   * @param apiVersion The FHIR API version of the message.
   * @param profile The profile associated with the message.
   */
  public void sendNotificationToSecureQueue(
      final String msg,
      final String messageId,
      final String batchId,
      final String documentId,
      final String authorization,
      final String apiVersion,
      final String profile) {
    log.info(
        "notification message: {} / batch: {} / documentId: {}", messageId, batchId, documentId);

    final byte[] encryptedMessage = encrypt(msg);
    final byte[] encryptedAuthorization = encrypt(authorization);
    final MessageProperties msgHeaders =
        MessagePropertiesBuilder.newInstance()
            .setHeader(HEADER_MESSAGE_ID, messageId)
            .setHeader(HEADER_BATCH_ID, batchId)
            .setHeader(HEADER_DOCUMENT_ID, documentId)
            .setHeader(HEADER_TYPE, MessageType.NOTIFICATION.name())
            .setHeader(HEADER_AUTHORIZATION, encryptedAuthorization)
            .setHeader(HEADER_FHIR_PACKAGE_VERSION, apiVersion)
            .setHeader(HEADER_PACKAGE, profile)
            .build();

    messageSender.send(ROUTING_KEY_SECURE, msgHeaders, encryptedMessage);
  }

  /**
   * Generates an error message and sends it to the secure message queue.
   *
   * @param msg The content of the message.
   * @param messageId The identifier of the message.
   * @param batchId The identifier of the associated batch.
   * @param documentId The document identifier of the notification.
   */
  public void sendErrorMessageToSecureQueue(
      final String msg, final String messageId, final String batchId, final String documentId) {
    log.info("error message: {} / batch: {} / documentId: {}", messageId, batchId, documentId);

    final MessageProperties msgHeaders =
        MessagePropertiesBuilder.newInstance()
            .setHeader(HEADER_MESSAGE_ID, messageId)
            .setHeader(HEADER_BATCH_ID, batchId)
            .setHeader(HEADER_DOCUMENT_ID, documentId)
            .setHeader(HEADER_TYPE, MessageType.ERROR.name())
            .build();

    messageSender.send(ROUTING_KEY_SECURE, msgHeaders, msg.getBytes());
  }

  /**
   * Generates a status message and sends it to the control message queue.
   *
   * @param msg The content of the message.
   * @param messageId The identifier of the message.
   * @param batchId The identifier of the associated batch.
   */
  public void sendStatusMessageToControlQueue(
      final String msg, final String messageId, final String batchId) {
    log.info("status message: {} / batch: {}", messageId, batchId);

    final MessageProperties msgHeaders =
        MessagePropertiesBuilder.newInstance()
            .setHeader(HEADER_MESSAGE_ID, messageId)
            .setHeader(HEADER_BATCH_ID, batchId)
            .setHeader(HEADER_TYPE, MessageType.STATUS.name())
            .build();

    messageSender.send(ROUTING_KEY_CONTROL, msgHeaders, msg.getBytes());
  }

  private byte[] encrypt(final String msg) {
    try {
      return encryptionService.encryptData(msg);
    } catch (final Exception ex) {
      throw ErrorCode.ENCRYPTION_ERROR.exception("error encrypting string", ex);
    }
  }
}
