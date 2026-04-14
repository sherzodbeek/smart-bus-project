package com.smartbus.booking.messaging;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

@Configuration
public class PaymentDeclinedKafkaConsumerConfiguration {

  @Bean
  ConsumerFactory<String, PaymentDeclinedEvent> paymentDeclinedConsumerFactory(
      @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
      @Value("${smartbus.messaging.payment-declined-consumer-group}") String groupId
  ) {
    Map<String, Object> properties = new HashMap<>();
    properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    properties.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
    properties.put(JsonDeserializer.TRUSTED_PACKAGES, "com.smartbus.booking.messaging");
    properties.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
    properties.put(JsonDeserializer.VALUE_DEFAULT_TYPE, PaymentDeclinedEvent.class.getName());
    return new DefaultKafkaConsumerFactory<>(properties);
  }

  @Bean
  ConcurrentKafkaListenerContainerFactory<String, PaymentDeclinedEvent> paymentDeclinedListenerContainerFactory(
      ConsumerFactory<String, PaymentDeclinedEvent> paymentDeclinedConsumerFactory
  ) {
    ConcurrentKafkaListenerContainerFactory<String, PaymentDeclinedEvent> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(paymentDeclinedConsumerFactory);
    return factory;
  }
}
