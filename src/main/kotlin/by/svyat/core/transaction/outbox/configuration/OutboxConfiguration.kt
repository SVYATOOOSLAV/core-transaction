package by.svyat.core.transaction.outbox.configuration

import by.svyat.core.transaction.outbox.OutboxEventHandler
import mu.KotlinLogging
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

private val log = KotlinLogging.logger {}

@Configuration
class OutboxConfiguration {

    /**
     * Реестр хэндлеров outbox-событий: aggregateType.name → handler.
     *
     * Spring автоматически инжектит все бины [OutboxEventHandler] в список.
     * Каждый хэндлер декларирует поддерживаемые типы агрегатов через
     * [OutboxEventHandler.supportedAggregateTypes], по которым строится маршрутизация.
     */
    @Bean
    fun outboxHandlerRegistry(handlers: List<OutboxEventHandler>): Map<String, OutboxEventHandler> {
        val registry = mutableMapOf<String, OutboxEventHandler>()

        for (handler in handlers) {
            for (aggregateType in handler.supportedAggregateTypes()) {
                val previous = registry.put(aggregateType.name, handler)
                if (previous != null) {
                    log.warn {
                        "Duplicate handler for aggregateType=${aggregateType.name}: " +
                                "${previous::class.simpleName} replaced by ${handler::class.simpleName}"
                    }
                }
                log.info { "Outbox handler registered: ${handler::class.simpleName} -> ${aggregateType.name}" }
            }
        }

        return registry
    }
}
