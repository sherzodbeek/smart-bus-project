package com.smartbus.notification.messaging;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

@EnableKafka
@Configuration
public class KafkaConsumerConfiguration {

  private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfiguration.class);

  @Value("${spring.kafka.bootstrap-servers}")
  private String bootstrapServers;

  @Value("${smartbus.messaging.booking-consumer-group}")
  private String consumerGroup;

  @Bean
  ConsumerFactory<String, BookingConfirmedEvent> bookingConfirmedConsumerFactory() {
    Map<String, Object> props = new HashMap<>();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroup);
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
    props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.smartbus.notification.messaging");
    props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
    props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, BookingConfirmedEvent.class.getName());
    return new DefaultKafkaConsumerFactory<>(props);
  }

  @Bean
  ConcurrentKafkaListenerContainerFactory<String, BookingConfirmedEvent> kafkaListenerContainerFactory() {
    ConcurrentKafkaListenerContainerFactory<String, BookingConfirmedEvent> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(bookingConfirmedConsumerFactory());
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
    factory.setCommonErrorHandler(new DefaultErrorHandler(
        (record, exception) -> log.error(
            "kafkaConsumerFailed topic={} partition={} offset={} key={} exceptionType={} message={}",
            record.topic(),
            record.partition(),
            record.offset(),
            record.key(),
            exception.getClass().getSimpleName(),
            exception.getMessage()
        ),
        new FixedBackOff(1000L, 2)
    ));
    return factory;
  }
}
