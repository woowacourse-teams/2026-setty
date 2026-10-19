package cloud.setty.setty.data

import cloud.setty.setty.model.Keyword
import cloud.setty.setty.network.KeywordRequest
import cloud.setty.setty.network.KeywordResponse
import cloud.setty.setty.network.SettyNetworkApi
import cloud.setty.setty.network.apiCall

class KeywordRepository(
    private val settyNetworkApi: SettyNetworkApi,
) {
    suspend fun getKeywords(): List<Keyword> =
        apiCall { settyNetworkApi.getKeywords() }.items.map { it.toKeyword() }

    suspend fun add(keyword: String) {
        apiCall { settyNetworkApi.addKeyword(KeywordRequest(keyword)) }
    }

    suspend fun remove(subscriptionId: Long) {
        apiCall { settyNetworkApi.removeKeyword(subscriptionId) }
    }
}

private fun KeywordResponse.toKeyword() = Keyword(
    id = id,
    keyword = keyword,
)
