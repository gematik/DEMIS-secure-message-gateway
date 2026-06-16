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

import de.gematik.demis.smg.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** REST Controller for handling message requests. */
@Slf4j
@RestController
@RequiredArgsConstructor
public class MessageController implements IMessageController {

  private final MessageService messageService;

  @Override
  public ResponseEntity<Void> processNotification(
      String msg,
      String messageId,
      String batchId,
      String documentId,
      String authorization,
      String apiVersion,
      String profile) {
    messageService.sendNotificationToSecureQueue(
        msg, messageId, batchId, documentId, authorization, apiVersion, profile);
    return ResponseEntity.accepted().build();
  }

  @Override
  public ResponseEntity<Void> processErrorMessage(
      String msg, String messageId, String batchId, String documentId) {
    messageService.sendErrorMessageToSecureQueue(msg, messageId, batchId, documentId);
    return ResponseEntity.accepted().build();
  }

  @Override
  public ResponseEntity<Void> processStatusMessage(String msg, String messageId, String batchId) {
    messageService.sendStatusMessageToControlQueue(msg, messageId, batchId);
    return ResponseEntity.accepted().build();
  }
}
