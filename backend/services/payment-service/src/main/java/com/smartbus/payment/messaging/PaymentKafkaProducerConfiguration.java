package com.smartbus.payment.messaging;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

@Configuration
public class PaymentKafkaProducerConfiguration {

  @Bean
  ProducerFactory<String, PaymentDeclinedEvent> paymentDeclinedProducerFactory(
      @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers
  ) {
    Map<String, Object> properties = new HashMap<>();
    properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
    properties.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
    return new DefaultKafkaProducerFactory<>(properties);
  }

  @Bean
  KafkaTemplate<String, PaymentDeclinedEvent> paymentDeclinedKafkaTemplate(
      ProducerFactory<String, PaymentDeclinedEvent> paymentDeclinedProducerFactory
  ) {
    return new KafkaTemplate<>(paymentDeclinedProducerFactory);
  }
}
