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

import static de.gematik.demis.smg.api.IMessageController.PROCESS_ERROR_URL;
import static de.gematik.demis.smg.api.IMessageController.PROCESS_NOTIFICATION_URL;
import static de.gematik.demis.smg.api.IMessageController.PROCESS_STATUS_URL;
import static de.gematik.demis.smg.constants.MessageHeaderConstants.HEADER_AUTHORIZATION;
import static de.gematik.demis.smg.constants.MessageHeaderConstants.HEADER_BATCH_ID;
import static de.gematik.demis.smg.constants.MessageHeaderConstants.HEADER_DOCUMENT_ID;
import static de.gematik.demis.smg.constants.MessageHeaderConstants.HEADER_MESSAGE_ID;
import static de.gematik.demis.smg.test.TestUtils.AUTHORIZATION;
import static de.gematik.demis.smg.test.TestUtils.BATCH_ID;
import static de.gematik.demis.smg.test.TestUtils.DOCUMENT_ID;
import static de.gematik.demis.smg.test.TestUtils.FHIR_API_VERSION;
import static de.gematik.demis.smg.test.TestUtils.MESSAGE_ID;
import static de.gematik.demis.smg.test.TestUtils.PROFILE_VERSION;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.gematik.demis.smg.service.MessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class MessageControllerIT {

  private final ObjectMapper objectMapper = new ObjectMapper();
  @Mock private MessageService messageService;
  @InjectMocks private MessageController messageController;
  private MockMvc mockMvc;

  @BeforeEach
  void setup() {
    mockMvc = MockMvcBuilders.standaloneSetup(messageController).build();
  }

  @Test
  void shouldProcessMessage_whenValidMessageIsReceived() throws Exception {

    mockMvc
        .perform(
            post(PROCESS_NOTIFICATION_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HEADER_MESSAGE_ID, MESSAGE_ID)
                .header(HEADER_BATCH_ID, BATCH_ID)
                .header(HEADER_DOCUMENT_ID, DOCUMENT_ID)
                .header(HEADER_AUTHORIZATION, AUTHORIZATION)
                .header("x-fhir-package-version", FHIR_API_VERSION)
                .header("x-fhir-package", PROFILE_VERSION)
                .content(objectMapper.writeValueAsString("")))
        .andExpect(status().isAccepted());

    verify(messageService)
        .sendNotificationToSecureQueue(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void shouldProcessMessage_whenErrorMessageIsReceived() throws Exception {

    mockMvc
        .perform(
            post(PROCESS_ERROR_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HEADER_MESSAGE_ID, MESSAGE_ID)
                .header(HEADER_BATCH_ID, BATCH_ID)
                .header(HEADER_DOCUMENT_ID, DOCUMENT_ID)
                .content(objectMapper.writeValueAsString("")))
        .andExpect(status().isAccepted());

    verify(messageService).sendErrorMessageToSecureQueue(any(), any(), any(), any());
  }

  @Test
  void shouldProcessMessage_whenValidControlMessageIsReceived() throws Exception {
    mockMvc
        .perform(
            post(PROCESS_STATUS_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HEADER_MESSAGE_ID, MESSAGE_ID)
                .header(HEADER_BATCH_ID, BATCH_ID)
                .content("{ \"someKey\": \"someValue\" }"))
        .andExpect(status().isAccepted());

    verify(messageService).sendStatusMessageToControlQueue(any(), any(), any());
  }
}
