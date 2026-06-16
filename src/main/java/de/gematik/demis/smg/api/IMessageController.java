package de.gematik.demis.smg.api;

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

import static de.gematik.demis.smg.constants.MessageHeaderConstants.HEADER_AUTHORIZATION;
import static de.gematik.demis.smg.constants.MessageHeaderConstants.HEADER_BATCH_ID;
import static de.gematik.demis.smg.constants.MessageHeaderConstants.HEADER_DOCUMENT_ID;
import static de.gematik.demis.smg.constants.MessageHeaderConstants.HEADER_FHIR_PACKAGE_VERSION;
import static de.gematik.demis.smg.constants.MessageHeaderConstants.HEADER_MESSAGE_ID;
import static de.gematik.demis.smg.constants.MessageHeaderConstants.HEADER_PACKAGE;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/** REST Controller for handling message requests. */
public interface IMessageController {

  String PROCESS_NOTIFICATION_URL = "/process-notification";
  String PROCESS_ERROR_URL = "/process-error";
  String PROCESS_STATUS_URL = "/process-status";

  /**
   * Processes an ARS notification.
   *
   * @param msg The notification
   * @param messageId The identifier of the message
   * @param batchId The batch id of the message
   * @param documentId The document id of the message
   * @param authorization The authorization header of the message
   * @param apiVersion The FHIR API version of the message
   * @param profile The associated profile of the message
   * @return ResponseEntity with status code 202 (Accepted) if the message was received
   *     successfully, otherwise an appropriate error status code
   */
  @PostMapping(path = PROCESS_NOTIFICATION_URL, consumes = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<Void> processNotification(
      @RequestBody String msg,
      @RequestHeader(value = HEADER_MESSAGE_ID) String messageId,
      @RequestHeader(value = HEADER_BATCH_ID) String batchId,
      @RequestHeader(value = HEADER_DOCUMENT_ID) String documentId,
      @RequestHeader(value = HEADER_AUTHORIZATION) String authorization,
      @RequestHeader(value = HEADER_FHIR_PACKAGE_VERSION) String apiVersion,
      @RequestHeader(value = HEADER_PACKAGE) String profile);

  /**
   * Processes an error message.
   *
   * @param msg The error message
   * @param messageId The identifier of the message
   * @param batchId The batch id of the message
   * @param documentId The document id of the message
   * @return ResponseEntity with status code 202 (Accepted) if the message was received
   *     successfully, otherwise an appropriate error status code
   */
  @PostMapping(path = PROCESS_ERROR_URL, consumes = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<Void> processErrorMessage(
      @RequestBody String msg,
      @RequestHeader(value = HEADER_MESSAGE_ID) String messageId,
      @RequestHeader(value = HEADER_BATCH_ID) String batchId,
      @RequestHeader(value = HEADER_DOCUMENT_ID) String documentId);

  /**
   * Processes a status message.
   *
   * @param msg The notification
   * @param messageId The identifier of the message
   * @param batchId The batch id of the message
   * @return ResponseEntity with status code 202 (Accepted) if the message was received
   *     successfully, otherwise an appropriate error status code
   */
  @PostMapping(path = PROCESS_STATUS_URL, consumes = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<Void> processStatusMessage(
      @RequestBody String msg,
      @RequestHeader(value = HEADER_MESSAGE_ID) String messageId,
      @RequestHeader(value = HEADER_BATCH_ID) String batchId);
}
