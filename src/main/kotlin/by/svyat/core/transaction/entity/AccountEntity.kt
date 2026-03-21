package by.svyat.core.transaction.entity

import jakarta.persistence.*
import by.svyat.core.transaction.entity.enums.AccountType
import java.math.BigDecimal
import java.time.OffsetDateTime

@Entity
@Table(name = "accounts")
class AccountEntity(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: UserEntity,

    @Column(name = "account_number", nullable = false, unique = true, length = 20)
    val accountNumber: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 30)
    val accountType: AccountType,

    @Column(name = "currency", nullable = false, length = 3)
    val currency: String = "RUB",

    @Column(name = "balance", nullable = false, precision = 19, scale = 4)
    var balance: BigDecimal = BigDecimal.ZERO,

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now(),

    @OneToMany(mappedBy = "account", fetch = FetchType.LAZY)
    val cards: MutableList<CardEntity> = mutableListOf()
)
