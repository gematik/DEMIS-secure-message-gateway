package de.gematik.demis.smg.test;

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

import java.util.UUID;

public class TestUtils {
  public static final String FHIR_API_VERSION = "V1";
  public static final String PROFILE_VERSION = "V2";
  public static final String MESSAGE_ID = UUID.randomUUID().toString();
  public static final String BATCH_ID = UUID.randomUUID().toString();
  public static final String AUTHORIZATION = "Bearer test-token";
  public static final String TEST_NOTIFICATION = "notification";
  public static final String DOCUMENT_ID = "0";
  public static final String ERROR_WAF_CHECK_FAILED_MSG = "{\"error\":\"WAF check failed\"}";
}
