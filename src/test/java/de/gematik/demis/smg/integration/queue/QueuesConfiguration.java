package de.gematik.demis.smg.integration.queue;

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

import static de.gematik.demis.smg.config.RabbitConfig.CONTROL_QUEUE;
import static de.gematik.demis.smg.config.RabbitConfig.EXCHANGE;
import static de.gematik.demis.smg.config.RabbitConfig.ROUTING_KEY_CONTROL;
import static de.gematik.demis.smg.config.RabbitConfig.ROUTING_KEY_SECURE;
import static de.gematik.demis.smg.config.RabbitConfig.SECURE_QUEUE;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("test")
/** Base class for queue configurations providing common queue creation methods. */
public class QueuesConfiguration {

  /**
   * Creates a durable quorum queue with the given name.
   *
   * @param name The name of the queue.
   * @return The created durable quorum queue.
   */
  protected Queue createDurableQueue(final String name) {
    return QueueBuilder.durable(name).quorum().build();
  }

  @Bean
  public DirectExchange bulkExchange() {
    return new DirectExchange(EXCHANGE, true, false);
  }

  @Bean
  public Queue secureQueue() {
    return createDurableQueue(SECURE_QUEUE);
  }

  @Bean
  public Binding secureBinding(final Queue secureQueue, final DirectExchange bulkExchange) {
    return BindingBuilder.bind(secureQueue).to(bulkExchange).with(ROUTING_KEY_SECURE);
  }

  @Bean
  public Queue controlQueue() {
    return createDurableQueue(CONTROL_QUEUE);
  }

  @Bean
  public Binding controlBinding(final Queue controlQueue, final DirectExchange bulkExchange) {
    return BindingBuilder.bind(controlQueue).to(bulkExchange).with(ROUTING_KEY_CONTROL);
  }
}
