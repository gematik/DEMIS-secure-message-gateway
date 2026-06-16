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

import static de.gematik.demis.smg.constants.MessageHeaderConstants.HEADER_AUTHORIZATION;
import static de.gematik.demis.smg.constants.MessageHeaderConstants.HEADER_BATCH_ID;
import static de.gematik.demis.smg.constants.MessageHeaderConstants.HEADER_DOCUMENT_ID;
import static de.gematik.demis.smg.constants.MessageHeaderConstants.HEADER_FHIR_PACKAGE_VERSION;
import static de.gematik.demis.smg.constants.MessageHeaderConstants.HEADER_MESSAGE_ID;
import static de.gematik.demis.smg.constants.MessageHeaderConstants.HEADER_PACKAGE;
import static de.gematik.demis.smg.test.TestUtils.AUTHORIZATION;
import static de.gematik.demis.smg.test.TestUtils.BATCH_ID;
import static de.gematik.demis.smg.test.TestUtils.DOCUMENT_ID;
import static de.gematik.demis.smg.test.TestUtils.FHIR_API_VERSION;
import static de.gematik.demis.smg.test.TestUtils.MESSAGE_ID;
import static de.gematik.demis.smg.test.TestUtils.PROFILE_VERSION;
import static de.gematik.demis.smg.test.TestUtils.TEST_NOTIFICATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

import de.gematik.demis.service.base.error.rest.api.ErrorDTO;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;

/**
 * In contrast to SecureMessageGatewaySystemTest this test mocks RabbitMq to test integrative error
 * cases.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "no-rabbitmq"})
@AutoConfigureTestRestTemplate
@Slf4j
class SecureMessageGatewayIntegrationTest {

  private static final String ENDPOINT_PROCESS_NOTIFICATION = "/process-notification";

  @MockitoBean RabbitTemplate rabbitTemplate;
  @Autowired private TestRestTemplate restTemplate;
  @Autowired private JsonMapper jsonMapper;

  private static HttpHeaders validNotificationHeaders() {
    final HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.add(HEADER_MESSAGE_ID, MESSAGE_ID);
    headers.add(HEADER_BATCH_ID, BATCH_ID);
    headers.add(HEADER_DOCUMENT_ID, DOCUMENT_ID);
    headers.add(HEADER_AUTHORIZATION, AUTHORIZATION);
    headers.add(HEADER_FHIR_PACKAGE_VERSION, FHIR_API_VERSION);
    headers.add(HEADER_PACKAGE, PROFILE_VERSION);
    return headers;
  }

  @Test
  void success() {
    mockCorrelationConfirm(true);

    final ResponseEntity<String> response =
        callRestEndpoint(
            ENDPOINT_PROCESS_NOTIFICATION, validNotificationHeaders(), TEST_NOTIFICATION);

    assertThat(response.getStatusCode().value()).isEqualTo(202);
    assertThat(response.getBody()).isNull();
  }

  @Test
  void rabbitMqNack() {
    mockCorrelationConfirm(false);

    final ResponseEntity<String> response =
        callRestEndpoint(
            ENDPOINT_PROCESS_NOTIFICATION, validNotificationHeaders(), TEST_NOTIFICATION);

    assertThat(response.getStatusCode().value()).isEqualTo(500);
    assertThat(response.getBody()).isNotNull();

    final ErrorDTO errorDTO = readErrorDto(response);
    assertThat(errorDTO.errorCode()).isEqualTo("NOT_ACKNOWLEDGED");
  }

  @Test
  void rabbitMqError() {
    doThrow(AmqpException.class)
        .when(rabbitTemplate)
        .send(any(), any(), any(), any(CorrelationData.class));

    final ResponseEntity<String> response =
        callRestEndpoint(
            ENDPOINT_PROCESS_NOTIFICATION, validNotificationHeaders(), TEST_NOTIFICATION);

    assertThat(response.getStatusCode().value()).isEqualTo(500);
    assertThat(response.getBody()).isNotNull();

    final ErrorDTO errorDTO = readErrorDto(response);
    assertThat(errorDTO.errorCode()).isEqualTo("RABBIT_MQ_ERROR");
  }

  private ResponseEntity<String> callRestEndpoint(
      final String endpoint, final HttpHeaders headers, final String body) {
    final HttpEntity<String> request = new HttpEntity<>(body, headers);
    final ResponseEntity<String> response =
        restTemplate.postForEntity(endpoint, request, String.class);

    log.info(
        "Response: Status={}, Headers={}, Body={}",
        response.getStatusCode(),
        response.getHeaders(),
        response.getBody());

    return response;
  }

  private void mockCorrelationConfirm(final boolean ack) {
    doAnswer(
            invocation -> {
              final CorrelationData correlationData = invocation.getArgument(3);
              if (correlationData != null) {
                correlationData.getFuture().complete(new CorrelationData.Confirm(ack, null));
              }
              return null;
            })
        .when(rabbitTemplate)
        .send(any(), any(), any(), any(CorrelationData.class));
  }

  private ErrorDTO readErrorDto(final ResponseEntity<String> response) {
    return jsonMapper.readValue(response.getBody(), ErrorDTO.class);
  }
}
