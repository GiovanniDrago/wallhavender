package xyz.attacktive.wallhavend

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import xyz.attacktive.wallhavend.data.prefs.ApiKeyStore

/** In-memory [ApiKeyStore] so repository tests can drive the key without the Android Keystore. */
class FakeApiKeyStore(initialApiKey: String = "") : ApiKeyStore {
	private val _apiKey = MutableStateFlow(initialApiKey)

	override val apiKey: StateFlow<String> = _apiKey.asStateFlow()

	override fun set(apiKey: String) {
		_apiKey.value = apiKey
	}

	override fun clear() {
		_apiKey.value = ""
	}
}
