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
import static de.gematik.demis.smg.constants.MessageType.ERROR;
import static de.gematik.demis.smg.constants.MessageType.NOTIFICATION;
import static de.gematik.demis.smg.constants.MessageType.STATUS;
import static de.gematik.demis.smg.test.TestUtils.AUTHORIZATION;
import static de.gematik.demis.smg.test.TestUtils.BATCH_ID;
import static de.gematik.demis.smg.test.TestUtils.DOCUMENT_ID;
import static de.gematik.demis.smg.test.TestUtils.ERROR_WAF_CHECK_FAILED_MSG;
import static de.gematik.demis.smg.test.TestUtils.FHIR_API_VERSION;
import static de.gematik.demis.smg.test.TestUtils.MESSAGE_ID;
import static de.gematik.demis.smg.test.TestUtils.PROFILE_VERSION;
import static de.gematik.demis.smg.test.TestUtils.TEST_NOTIFICATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.gematik.demis.service.base.security.crypto.AESEncryptionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.MessageProperties;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

  @Mock private MessageSender messageSender;
  @Mock private AESEncryptionService encryptionService;
  @Captor private ArgumentCaptor<MessageProperties> messagePropertiesCaptor;
  @Captor private ArgumentCaptor<byte[]> messageBodyCaptor;
  @InjectMocks private MessageService underTest;

  @Test
  void shouldSendValidNotificationCryptedToSecureMessageQueue() {
    final byte[] encryptedMessage = "encrypedMessage".getBytes();
    final byte[] encryptedAuthorization = "jwt".getBytes();

    when(encryptionService.encryptData(TEST_NOTIFICATION)).thenReturn(encryptedMessage);
    when(encryptionService.encryptData(AUTHORIZATION)).thenReturn(encryptedAuthorization);

    underTest.sendNotificationToSecureQueue(
        TEST_NOTIFICATION,
        MESSAGE_ID,
        BATCH_ID,
        DOCUMENT_ID,
        AUTHORIZATION,
        FHIR_API_VERSION,
        PROFILE_VERSION);

    verify(encryptionService, times(2)).encryptData(any());
    verify(messageSender)
        .send(
            eq(ROUTING_KEY_SECURE), messagePropertiesCaptor.capture(), messageBodyCaptor.capture());

    final MessageProperties props = messagePropertiesCaptor.getValue();
    final byte[] sentPayload = messageBodyCaptor.getValue();
    assertAll(
        () -> assertThat(sentPayload).isEqualTo(encryptedMessage),
        () -> assertThat((Object) props.getHeader(HEADER_BATCH_ID)).isEqualTo(BATCH_ID),
        () -> assertThat((Object) props.getHeader(HEADER_DOCUMENT_ID)).isEqualTo(DOCUMENT_ID),
        () ->
            assertThat((Object) props.getHeader(HEADER_AUTHORIZATION))
                .isEqualTo(encryptedAuthorization),
        () ->
            assertThat((Object) props.getHeader(HEADER_FHIR_PACKAGE_VERSION))
                .isEqualTo(FHIR_API_VERSION),
        () -> assertThat((Object) props.getHeader(HEADER_PACKAGE)).isEqualTo(PROFILE_VERSION),
        () -> assertThat((Object) props.getHeader(HEADER_TYPE)).isEqualTo(NOTIFICATION.name()));
  }

  @Test
  void shouldSendCorrectHeaderAndNoNotificationIfTypeError() {
    underTest.sendErrorMessageToSecureQueue(
        ERROR_WAF_CHECK_FAILED_MSG, MESSAGE_ID, BATCH_ID, DOCUMENT_ID);

    verifyNoInteractions(encryptionService);
    verify(messageSender)
        .send(
            eq(ROUTING_KEY_SECURE), messagePropertiesCaptor.capture(), messageBodyCaptor.capture());

    final MessageProperties props = messagePropertiesCaptor.getValue();
    final byte[] sentPayload = messageBodyCaptor.getValue();
    assertAll(
        () -> assertThat(new String(sentPayload)).isEqualTo(ERROR_WAF_CHECK_FAILED_MSG),
        () -> assertThat((Object) props.getHeader(HEADER_BATCH_ID)).isEqualTo(BATCH_ID),
        () -> assertThat((Object) props.getHeader(HEADER_DOCUMENT_ID)).isEqualTo(DOCUMENT_ID),
        () ->
            assertThat(props.getHeaders())
                .doesNotContainKeys(
                    HEADER_AUTHORIZATION, HEADER_FHIR_PACKAGE_VERSION, HEADER_PACKAGE),
        () -> assertThat((Object) props.getHeader(HEADER_TYPE)).isEqualTo(ERROR.name()));
  }

  @Test
  void shouldSendStatusMessageToControlQueue() {
    underTest.sendStatusMessageToControlQueue(TEST_NOTIFICATION, MESSAGE_ID, BATCH_ID);

    verifyNoInteractions(encryptionService);
    verify(messageSender)
        .send(
            eq(ROUTING_KEY_CONTROL),
            messagePropertiesCaptor.capture(),
            messageBodyCaptor.capture());

    final MessageProperties props = messagePropertiesCaptor.getValue();
    final byte[] sentPayload = messageBodyCaptor.getValue();
    assertAll(
        () -> assertThat(new String(sentPayload)).hasToString(TEST_NOTIFICATION),
        () -> assertThat((Object) props.getHeader(HEADER_MESSAGE_ID)).isEqualTo(MESSAGE_ID),
        () -> assertThat((Object) props.getHeader(HEADER_BATCH_ID)).isEqualTo(BATCH_ID),
        () -> assertThat((Object) props.getHeader(HEADER_TYPE)).isEqualTo(STATUS.name()));
  }
}
