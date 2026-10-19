package cloud.setty.setty.network

class ApiException(
    val code: String,
    override val message: String,
) : Exception(message)
