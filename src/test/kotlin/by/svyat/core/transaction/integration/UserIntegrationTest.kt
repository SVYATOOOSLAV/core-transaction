package by.svyat.core.transaction.integration

import by.svyat.core.transaction.IntegrationTestBase
import by.svyat.core.transaction.TestApiClient
import by.svyat.core.transaction.TestDataFactory
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

class UserIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var api: TestApiClient

    @Nested
    inner class CreateUser {

        @Test
        fun `full lifecycle - create and get by id and phone`() {
            val request = TestDataFactory.userRequest(patronymic = "Иванович", email = "ivan@mail.ru")

            val createResult = mockMvc.post("/api/v1/users") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isCreated() }
                jsonPath("$.id") { exists() }
                jsonPath("$.firstName") { value("Иван") }
                jsonPath("$.lastName") { value("Иванов") }
                jsonPath("$.patronymic") { value("Иванович") }
                jsonPath("$.phoneNumber") { value("+79991234567") }
                jsonPath("$.email") { value("ivan@mail.ru") }
                jsonPath("$.createdAt") { exists() }
            }.andReturn()

            val userId = objectMapper.readTree(createResult.response.contentAsString)["id"].asLong()

            mockMvc.get("/api/v1/users/$userId").andExpect {
                status { isOk() }
                jsonPath("$.id") { value(userId) }
                jsonPath("$.firstName") { value("Иван") }
            }

            mockMvc.get("/api/v1/users/phone/+79991234567").andExpect {
                status { isOk() }
                jsonPath("$.phoneNumber") { value("+79991234567") }
            }
        }

        @Test
        fun `duplicate phone returns 409`() {
            api.createUser(phoneNumber = "+79991111111")

            mockMvc.post("/api/v1/users") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(TestDataFactory.userRequest(phoneNumber = "+79991111111"))
            }.andExpect {
                status { isConflict() }
            }
        }

        @Test
        fun `validation error for blank firstName returns 400`() {
            val request = mapOf("firstName" to "", "lastName" to "Иванов", "phoneNumber" to "+79991234567")

            mockMvc.post("/api/v1/users") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.status") { value(400) }
            }
        }
    }

    @Nested
    inner class GetUser {

        @Test
        fun `not found returns 404`() {
            mockMvc.get("/api/v1/users/999999").andExpect {
                status { isNotFound() }
            }
        }

        @Test
        fun `by phone - not found returns 404`() {
            mockMvc.get("/api/v1/users/phone/+70000000000").andExpect {
                status { isNotFound() }
            }
        }
    }
}
