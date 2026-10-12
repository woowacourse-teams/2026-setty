package com.aksworns22.setty.data

import com.aksworns22.setty.model.Keyword
import com.aksworns22.setty.network.KeywordRequest
import com.aksworns22.setty.network.KeywordResponse
import com.aksworns22.setty.network.SettyNetworkApi
import com.aksworns22.setty.network.apiCall

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
