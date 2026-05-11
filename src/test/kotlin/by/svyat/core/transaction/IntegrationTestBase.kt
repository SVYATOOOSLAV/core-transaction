package by.svyat.core.transaction

import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import by.svyat.core.transaction.repository.AccountRepository
import by.svyat.core.transaction.repository.CardRepository
import by.svyat.core.transaction.repository.OutboxConsumerOffsetRepository
import by.svyat.core.transaction.repository.OutboxMessageRepository
import by.svyat.core.transaction.repository.OutboxPartitionLockRepository
import by.svyat.core.transaction.repository.TransactionRepository
import by.svyat.core.transaction.repository.UserRepository
import by.svyat.core.transaction.security.JwtService
import com.fasterxml.jackson.databind.ObjectMapper
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
abstract class IntegrationTestBase {

    @Autowired
    private lateinit var txRepository: TransactionRepository

    @Autowired
    private lateinit var cardRepository: CardRepository

    @Autowired
    private lateinit var accRepository: AccountRepository

    @Autowired
    private lateinit var usrRepository: UserRepository

    @Autowired
    private lateinit var outboxMessageRepository: OutboxMessageRepository

    @Autowired
    private lateinit var outboxConsumerOffsetRepository: OutboxConsumerOffsetRepository

    @Autowired
    private lateinit var outboxPartitionLockRepository: OutboxPartitionLockRepository

    @Autowired
    protected lateinit var jwtService: JwtService

    @Autowired
    protected lateinit var mockMvc: MockMvc

    @Autowired
    protected lateinit var objectMapper: ObjectMapper

    protected var authToken: String = ""

    @BeforeEach
    fun cleanDatabase() {
        outboxMessageRepository.deleteAll()
        outboxConsumerOffsetRepository.deleteAll()
        outboxPartitionLockRepository.deleteAll()
        txRepository.deleteAll()
        cardRepository.deleteAll()
        accRepository.deleteAll()
        usrRepository.deleteAll()
        authToken = jwtService.generateToken("test-gateway", listOf("GATEWAY"))
    }

    protected fun authGet(url: String): ResultActionsDsl {
        return mockMvc.get(url) {
            header("Authorization", "Bearer $authToken")
        }
    }

    protected fun authPost(url: String, body: Any): ResultActionsDsl {
        return mockMvc.post(url) {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(body)
            header("Authorization", "Bearer $authToken")
        }
    }

    companion object {
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("transaction_test_db")
            .withUsername("test")
            .withPassword("test")
            .apply { start() }

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("spring.jpa.hibernate.ddl-auto") { "validate" }
            registry.add("spring.liquibase.enabled") { "true" }
        }
    }
}
