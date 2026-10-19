package cloud.setty.setty.model

data class Member(
    val id: Long,
    val loginId: String,
    val phoneNumber: String,
    val address: String,
    val dongCode: String?,
)
