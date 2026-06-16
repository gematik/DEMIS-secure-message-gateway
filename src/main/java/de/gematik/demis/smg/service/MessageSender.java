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

import static de.gematik.demis.smg.config.RabbitConfig.EXCHANGE;
import static de.gematik.demis.smg.constants.MessageHeaderConstants.HEADER_MESSAGE_ID;
import static java.util.Objects.requireNonNull;

import de.gematik.demis.smg.error.ErrorCode;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/** Service to send messages to rabbitmq */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageSender {

  private static final int TIMEOUT_SECONDS_BROKER_ACKNOWLEDGEMENT = 3;

  private final RabbitTemplate rabbitTemplate;

  /**
   * Sends a message to the RabbitMQ exchange using the given routing key. The message body and
   * properties are wrapped into an AMQP {@link Message} and published via the {@link
   * RabbitTemplate}. After publishing, the method blocks until the broker acknowledges the message
   * or a timeout occurs.
   *
   * @param routingKey the routing key used to route the message to the appropriate queue
   * @param messageProperties the AMQP message properties (e.g. headers) to attach to the message
   * @param messageBody the raw byte content of the message to send
   * @throws de.gematik.demis.service.base.error.ServiceException if the message could not be sent,
   *     was not acknowledged, timed out, or the queue is not bound
   */
  public void send(
      final String routingKey,
      final MessageProperties messageProperties,
      final byte[] messageBody) {
    final CorrelationData correlationData =
        new CorrelationData(
            requireNonNull(messageProperties.getHeader(HEADER_MESSAGE_ID), "missing message id"));
    final Message message =
        MessageBuilder.withBody(messageBody).andProperties(messageProperties).build();
    try {
      rabbitTemplate.send(EXCHANGE, routingKey, message, correlationData);
    } catch (final Exception ex) {
      throw ErrorCode.RABBIT_MQ_ERROR.exception("error sending message", ex);
    }
    waitForConfirm(correlationData);
  }

  private void waitForConfirm(final CorrelationData correlationData) {
    try {
      final CorrelationData.Confirm confirm =
          correlationData.getFuture().get(TIMEOUT_SECONDS_BROKER_ACKNOWLEDGEMENT, TimeUnit.SECONDS);

      if (!confirm.ack()) {
        throw ErrorCode.NOT_ACKNOWLEDGED.exception("message nack due to " + confirm.reason());
      }

      final ReturnedMessage returned = correlationData.getReturned();
      if (returned != null) {
        throw ErrorCode.QUEUE_NOT_BOUND.exception(
            "message with routing key '"
                + returned.getRoutingKey()
                + "' returned due to "
                + returned.getReplyText());
      }

      log.debug("Message successfully confirmed by broker");
    } catch (final InterruptedException _) {
      Thread.currentThread().interrupt();
      throw ErrorCode.UNEXPECTED_EXCEPTION.exception("thread interrupted");
    } catch (final TimeoutException _) {
      throw ErrorCode.ACKNOWLEDGMENT_TIMEOUT.exception(
          "no ack in " + TIMEOUT_SECONDS_BROKER_ACKNOWLEDGEMENT + " sec");
    } catch (final ExecutionException ex) {
      throw ErrorCode.UNEXPECTED_EXCEPTION.exception(
          "exception while waiting for message confirmation", ex);
    }
  }
}
