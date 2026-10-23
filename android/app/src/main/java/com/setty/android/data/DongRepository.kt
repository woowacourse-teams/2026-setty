package com.setty.android.data

import com.setty.android.model.Dong
import com.setty.android.network.DongResponse
import com.setty.android.network.SettyNetworkApi
import com.setty.android.network.apiCall
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DongRepository(
    private val settyNetworkApi: SettyNetworkApi,
) {
    private val mutex = Mutex()
    private var cachedDongs: List<Dong>? = null

    /** 서울 행정동 목록은 거의 바뀌지 않으므로 앱이 떠 있는 동안 한 번만 불러온다. */
    suspend fun getDongs(): List<Dong> = mutex.withLock {
        cachedDongs ?: apiCall { settyNetworkApi.getDongs() }.items
            .map { it.toDong() }
            .also { cachedDongs = it }
    }
}

private fun DongResponse.toDong() = Dong(
    code = code,
    name = name,
    district = district,
)
