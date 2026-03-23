package by.svyat.core.transaction.configuration

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.support.serializer.JsonSerializer

@Configuration
class KafkaProducerConfig(
    private val kafkaProperties: KafkaProperties,
    private val objectMapper: ObjectMapper
) {

    @Bean
    fun producerFactory(): ProducerFactory<String, Any> {
        val props = mutableMapOf<String, Any>(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to kafkaProperties.bootstrapServers,
            ProducerConfig.ACKS_CONFIG to (kafkaProperties.producer.acks ?: "all"),
            ProducerConfig.RETRIES_CONFIG to (kafkaProperties.producer.retries ?: 3)
        )
        val jsonSerializer = JsonSerializer<Any>(objectMapper)
        return DefaultKafkaProducerFactory(props, StringSerializer(), jsonSerializer)
    }

    @Bean
    fun kafkaTemplate(producerFactory: ProducerFactory<String, Any>): KafkaTemplate<String, Any> {
        return KafkaTemplate(producerFactory)
    }
}
