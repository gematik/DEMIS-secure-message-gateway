package de.gematik.demis.smg;

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

import static de.gematik.demis.smg.test.TestUtils.AUTHORIZATION;
import static de.gematik.demis.smg.test.TestUtils.BATCH_ID;
import static de.gematik.demis.smg.test.TestUtils.DOCUMENT_ID;
import static de.gematik.demis.smg.test.TestUtils.FHIR_API_VERSION;
import static de.gematik.demis.smg.test.TestUtils.MESSAGE_ID;
import static de.gematik.demis.smg.test.TestUtils.PROFILE_VERSION;

import de.gematik.demis.smg.api.MessageController;
import de.gematik.demis.smg.service.MessageSender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles({"test", "no-rabbitmq"})
class SecureMessageGatewayApplicationTest {

  @MockitoBean private MessageSender messageSender;
  @Autowired private MessageController messageController;

  @Test
  void contextLoads() {
    messageController.processNotification(
        "someMsg",
        MESSAGE_ID,
        BATCH_ID,
        DOCUMENT_ID,
        AUTHORIZATION,
        FHIR_API_VERSION,
        PROFILE_VERSION);
  }
}
