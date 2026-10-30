package com.setty.android.data

import com.setty.android.model.Keyword
import com.setty.android.network.KeywordRequest
import com.setty.android.network.KeywordResponse
import com.setty.android.network.SettyNetworkApi
import com.setty.android.network.apiCall

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
