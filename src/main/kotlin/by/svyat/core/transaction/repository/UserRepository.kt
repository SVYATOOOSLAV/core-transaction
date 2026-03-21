package by.svyat.core.transaction.repository

import org.springframework.data.jpa.repository.JpaRepository
import by.svyat.core.transaction.entity.UserEntity

interface UserRepository : JpaRepository<UserEntity, Long> {
    fun findByPhoneNumber(phoneNumber: String): UserEntity?
}
