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

import static de.gematik.demis.smg.constants.MessageHeaderConstants.HEADER_MESSAGE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

import de.gematik.demis.service.base.error.ServiceException;
import de.gematik.demis.smg.error.ErrorCode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.MessagePropertiesBuilder;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@ExtendWith(MockitoExtension.class)
class MessageSenderTest {

  private static final String ROUTING_KEY = "my-test-routing-key";
  private static final String MESSAGE_BODY = "my test payload";
  private static final byte[] MESSAGE_BODY_BYTES = MESSAGE_BODY.getBytes();
  private static final String EXPECTED_EXCHANGE = "bulk.exchange";
  private static final MessageProperties VALID_HEADERS = headers(UUID.randomUUID().toString());

  @Mock private RabbitTemplate rabbitTemplate;
  @InjectMocks private MessageSender underTest;

  private static MessageProperties headers(final String messageId) {
    return MessagePropertiesBuilder.newInstance().setHeader(HEADER_MESSAGE_ID, messageId).build();
  }

  @Test
  void success() {
    final String messageId = UUID.randomUUID().toString();
    final MessageProperties messageProperties = headers(messageId);

    mockCorrelationConfirm(true, null);

    underTest.send(ROUTING_KEY, messageProperties, MESSAGE_BODY_BYTES);

    final ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.captor();
    final ArgumentCaptor<CorrelationData> correlationDataCaptor = ArgumentCaptor.captor();

    verify(rabbitTemplate)
        .send(
            eq(EXPECTED_EXCHANGE),
            eq(ROUTING_KEY),
            messageCaptor.capture(),
            correlationDataCaptor.capture());

    final Message message = messageCaptor.getValue();
    assertThat(message).isNotNull();
    assertThat(new String(message.getBody())).isEqualTo(MESSAGE_BODY);
    assertThat(message.getMessageProperties()).isEqualTo(messageProperties);

    final CorrelationData correlationData = correlationDataCaptor.getValue();
    assertThat(correlationData).isNotNull();
    assertThat(correlationData.getId()).isEqualTo(messageId);
  }

  @Test
  void missingMessageIdShouldThrowException() {
    final MessageProperties headers = headers(null);
    assertThatThrownBy(() -> underTest.send(ROUTING_KEY, headers, MESSAGE_BODY_BYTES))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("missing message id");
  }

  @Test
  void brokenConnectionShouldThrowException() {
    doThrow(AmqpException.class)
        .when(rabbitTemplate)
        .send(any(), any(), any(), any(CorrelationData.class));

    assertThatThrownBy(() -> underTest.send(ROUTING_KEY, VALID_HEADERS, MESSAGE_BODY_BYTES))
        .isInstanceOfSatisfying(
            ServiceException.class,
            ex -> assertServiceException(ex, ErrorCode.RABBIT_MQ_ERROR, "error sending message"));
  }

  @Test
  void brokerNackShouldThrowException() {
    mockCorrelationConfirm(false, null);

    assertThatThrownBy(() -> underTest.send(ROUTING_KEY, VALID_HEADERS, MESSAGE_BODY_BYTES))
        .isInstanceOfSatisfying(
            ServiceException.class,
            ex ->
                assertServiceException(ex, ErrorCode.NOT_ACKNOWLEDGED, "message nack due to null"));
  }

  @Test
  @Timeout(5)
  void brokerTimeoutShouldThrowException() {
    assertThatThrownBy(() -> underTest.send(ROUTING_KEY, VALID_HEADERS, MESSAGE_BODY_BYTES))
        .isInstanceOfSatisfying(
            ServiceException.class,
            ex -> assertServiceException(ex, ErrorCode.ACKNOWLEDGMENT_TIMEOUT, "no ack in 3 sec"));
  }

  @Test
  void noQueueBindingShouldThrowException() {
    mockCorrelationConfirm(
        true,
        new ReturnedMessage(
            Mockito.mock(Message.class), 322, "NO_ROUTE", EXPECTED_EXCHANGE, ROUTING_KEY));

    assertThatThrownBy(() -> underTest.send(ROUTING_KEY, VALID_HEADERS, MESSAGE_BODY_BYTES))
        .isInstanceOfSatisfying(
            ServiceException.class,
            ex ->
                assertServiceException(
                    ex,
                    ErrorCode.QUEUE_NOT_BOUND,
                    "message with routing key 'my-test-routing-key' returned due to NO_ROUTE"));
  }

  @Test
  void asyncExceptionThrowException() {
    doAnswer(
            invocation -> {
              final CorrelationData correlationData = invocation.getArgument(3);
              correlationData
                  .getFuture()
                  .completeExceptionally(new IllegalArgumentException("just for test"));
              return null;
            })
        .when(rabbitTemplate)
        .send(any(), any(), any(), any(CorrelationData.class));

    assertThatThrownBy(() -> underTest.send(ROUTING_KEY, VALID_HEADERS, MESSAGE_BODY_BYTES))
        .isInstanceOfSatisfying(
            ServiceException.class,
            ex ->
                assertServiceException(
                    ex,
                    ErrorCode.UNEXPECTED_EXCEPTION,
                    "exception while waiting for message confirmation"));
  }

  private void mockCorrelationConfirm(final boolean ack, final ReturnedMessage returned) {
    doAnswer(
            invocation -> {
              final CorrelationData correlationData = invocation.getArgument(3);
              if (correlationData != null) {
                correlationData.setReturned(returned);
                correlationData.getFuture().complete(new CorrelationData.Confirm(ack, null));
              }
              return null;
            })
        .when(rabbitTemplate)
        .send(any(), any(), any(), any(CorrelationData.class));
  }

  private void assertServiceException(
      final ServiceException ex, final ErrorCode expectedCode, final String expectedMessage) {
    assertThat(ex)
        .extracting(
            ServiceException::getErrorCode,
            ServiceException::getResponseStatus,
            ServiceException::getMessage)
        .containsExactly(expectedCode.name(), INTERNAL_SERVER_ERROR, expectedMessage);
  }
}
