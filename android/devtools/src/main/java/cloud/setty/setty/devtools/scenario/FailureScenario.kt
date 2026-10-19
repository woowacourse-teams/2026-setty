package cloud.setty.setty.devtools.scenario

enum class FailureScenario(
    val method: String,
    val path: String,
    val statusCode: Int,
) {
    LISTINGS_SERVER_ERROR(
        method = "GET",
        path = "/api/listings",
        statusCode = 500
    );

    fun matches(method: String, path: String): Boolean =
        this.method == method && path.trimEnd('/').endsWith(this.path)
}
