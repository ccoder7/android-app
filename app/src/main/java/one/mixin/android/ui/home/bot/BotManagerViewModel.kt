package one.mixin.android.ui.home.bot

import androidx.lifecycle.ViewModel
import one.mixin.android.repository.UserRepository
import javax.inject.Inject

class BotManagerViewModel @Inject internal constructor(val userRepository: UserRepository) : ViewModel() {

    suspend fun getNotTopApps(appIds: List<String>) = userRepository.getNotTopApps(appIds)

    suspend fun findAppById(appId: String) = userRepository.findAppById(appId)

    suspend fun findUserByAppId(appId: String) = userRepository.findUserByAppId(appId)
}
