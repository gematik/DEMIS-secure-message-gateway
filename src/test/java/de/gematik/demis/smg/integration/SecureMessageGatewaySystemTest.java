package de.gematik.demis.smg.integration;

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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.gematik.demis.service.base.error.ServiceException;
import de.gematik.demis.service.base.security.crypto.AESEncryptionService;
import de.gematik.demis.smg.integration.queue.TestControlMessageListener;
import de.gematik.demis.smg.integration.queue.TestSecureMessageListener;
import de.gematik.demis.smg.service.MessageSender;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.MessagePropertiesBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles({"test"})
@Testcontainers
class SecureMessageGatewaySystemTest {

  @Container
  private static final RabbitMQContainer RABBIT_MQ_CONTAINER =
      new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.7.25-management-alpine"));

  @Autowired private TestSecureMessageListener testSecureMessageListener;
  @Autowired private TestControlMessageListener testControlMessageListener;

  @Autowired private MockMvc mvc;

  @Autowired private AESEncryptionService encryptionService;
  @Autowired private MessageSender messageSender;

  @DynamicPropertySource
  static void registerProperties(final DynamicPropertyRegistry registry) {
    registry.add("spring.rabbitmq.host", RABBIT_MQ_CONTAINER::getHost);
    registry.add("spring.rabbitmq.port", RABBIT_MQ_CONTAINER::getAmqpPort);
    registry.add("spring.rabbitmq.username", RABBIT_MQ_CONTAINER::getAdminUsername);
    registry.add("spring.rabbitmq.password", RABBIT_MQ_CONTAINER::getAdminPassword);
    registry.add("spring.rabbitmq.virtual-host", () -> "/");
  }

  @AfterEach
  void clearMessages() {
    testSecureMessageListener.clearMessages();
    testControlMessageListener.clearMessages();
  }

  @Test
  @SneakyThrows
  void shouldSendNotificationCorrectly() {
    testSecureMessageListener.clearMessages();
    mvc.perform(
            post(PROCESS_NOTIFICATION_URL)
                .contentType(APPLICATION_JSON)
                .header(HEADER_MESSAGE_ID, MESSAGE_ID)
                .header(HEADER_BATCH_ID, BATCH_ID)
                .header(HEADER_DOCUMENT_ID, DOCUMENT_ID)
                .header(HEADER_AUTHORIZATION, AUTHORIZATION)
                .header(HEADER_FHIR_PACKAGE_VERSION, FHIR_API_VERSION)
                .header(HEADER_PACKAGE, PROFILE_VERSION)
                .content(TEST_NOTIFICATION))
        .andExpect(status().is2xxSuccessful());

    await()
        .atMost(5, TimeUnit.SECONDS)
        .until(() -> testSecureMessageListener.getMessages().size() == 1);

    final List<Message> messages = testSecureMessageListener.getMessages();
    final Message receivedMessage = messages.getFirst();
    final String decryptedMessage = encryptionService.decryptData(receivedMessage.getBody());
    final Map<String, Object> messageHeaders = receivedMessage.getMessageProperties().getHeaders();
    assertAll(
        () -> assertThat(decryptedMessage).isNotBlank(),
        () -> assertThat(decryptedMessage).isEqualTo(TEST_NOTIFICATION),
        () -> assertThat(messageHeaders).containsEntry(HEADER_MESSAGE_ID, MESSAGE_ID),
        () -> assertThat(messageHeaders).containsEntry(HEADER_BATCH_ID, BATCH_ID),
        () ->
            assertThat(messageHeaders).containsEntry(HEADER_FHIR_PACKAGE_VERSION, FHIR_API_VERSION),
        () -> assertThat(messageHeaders).containsEntry(HEADER_PACKAGE, PROFILE_VERSION),
        () ->
            assertThat(messageHeaders)
                .containsKey(HEADER_AUTHORIZATION)
                .extracting(map -> map.get(HEADER_AUTHORIZATION))
                .satisfies(
                    encrypted ->
                        assertThat(encryptionService.decryptData((byte[]) encrypted))
                            .isEqualTo(AUTHORIZATION)),
        () -> assertThat(messageHeaders).containsEntry(HEADER_DOCUMENT_ID, DOCUMENT_ID),
        () -> assertThat(messageHeaders).containsEntry(HEADER_TYPE, NOTIFICATION.name()));
  }

  @Test
  @SneakyThrows
  void shouldSendErrorMessageCorrectly() {
    testSecureMessageListener.clearMessages();
    mvc.perform(
            post(PROCESS_ERROR_URL)
                .contentType(APPLICATION_JSON)
                .header(HEADER_MESSAGE_ID, MESSAGE_ID)
                .header(HEADER_BATCH_ID, BATCH_ID)
                .header(HEADER_DOCUMENT_ID, DOCUMENT_ID)
                .content(ERROR_WAF_CHECK_FAILED_MSG))
        .andExpect(status().is2xxSuccessful());

    await()
        .atMost(5, TimeUnit.SECONDS)
        .until(() -> testSecureMessageListener.getMessages().size() == 1);

    final List<Message> messages = testSecureMessageListener.getMessages();
    final Message receivedMessage = messages.getFirst();
    final String message = new String(receivedMessage.getBody());
    final Map<String, Object> messageHeaders = receivedMessage.getMessageProperties().getHeaders();
    assertAll(
        () -> assertThat(message).isNotBlank(),
        () -> assertThat(message).isEqualTo(ERROR_WAF_CHECK_FAILED_MSG),
        () -> assertThat(messageHeaders).containsEntry(HEADER_MESSAGE_ID, MESSAGE_ID),
        () -> assertThat(messageHeaders).containsEntry(HEADER_BATCH_ID, BATCH_ID),
        () -> assertThat(messageHeaders).containsEntry(HEADER_DOCUMENT_ID, DOCUMENT_ID),
        () ->
            assertThat(messageHeaders)
                .doesNotContainKeys(HEADER_PACKAGE, HEADER_AUTHORIZATION, FHIR_API_VERSION),
        () -> assertThat(messageHeaders).containsEntry(HEADER_TYPE, ERROR.name()));
  }

  @Test
  @SneakyThrows
  void shouldSendStatusMessage() {
    testControlMessageListener.clearMessages();
    mvc.perform(
            post(PROCESS_STATUS_URL)
                .contentType(APPLICATION_JSON)
                .header(HEADER_MESSAGE_ID, MESSAGE_ID)
                .header(HEADER_BATCH_ID, BATCH_ID)
                .content(TEST_NOTIFICATION))
        .andExpect(status().is2xxSuccessful());

    await()
        .atMost(5, TimeUnit.SECONDS)
        .until(() -> testControlMessageListener.getMessages().size() == 1);

    final List<Message> messages = testControlMessageListener.getMessages();
    final Message receivedMessage = messages.getFirst();
    final String messageBody = new String(receivedMessage.getBody());
    final Map<String, Object> messageHeaders = receivedMessage.getMessageProperties().getHeaders();
    assertAll(
        () -> assertThat(messageBody).isNotBlank(),
        () -> assertThat(messageBody).isEqualTo(TEST_NOTIFICATION),
        () -> assertThat(messageHeaders).containsEntry(HEADER_MESSAGE_ID, MESSAGE_ID),
        () -> assertThat(messageHeaders).containsEntry(HEADER_BATCH_ID, BATCH_ID),
        () -> assertThat(messageHeaders).containsEntry(HEADER_TYPE, STATUS.name()));
  }

  @Test
  void shouldThrowExceptionIfNoQueueIsBound() {
    final MessageProperties messageProperties =
        MessagePropertiesBuilder.newInstance()
            .setHeader(HEADER_MESSAGE_ID, UUID.randomUUID().toString())
            .build();

    assertThatThrownBy(
            () ->
                messageSender.send(
                    "NO_QUEUE_IS_BOUND_TO_THIS_KEY",
                    messageProperties,
                    "does not matter".getBytes()))
        .isInstanceOfSatisfying(
            ServiceException.class,
            ex ->
                assertThat(ex)
                    .extracting(
                        ServiceException::getErrorCode,
                        ServiceException::getResponseStatus,
                        ServiceException::getMessage)
                    .containsExactly(
                        "QUEUE_NOT_BOUND",
                        INTERNAL_SERVER_ERROR,
                        "message with routing key 'NO_QUEUE_IS_BOUND_TO_THIS_KEY' returned due to NO_ROUTE"));
  }
}
