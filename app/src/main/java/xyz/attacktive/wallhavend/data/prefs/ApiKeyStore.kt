package xyz.attacktive.wallhavend.data.prefs

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import xyz.attacktive.wallhavend.util.AppLogger

/**
 * Persists the optional Wallhaven API key outside the backup-eligible settings DataStore.
 *
 * Exposes the key as a flow so the settings stream stays reactive when the key changes, and relies
 * on the backup rules excluding the backing preferences file to keep the key out of Android's
 * cloud backup and device transfer.
 */
interface ApiKeyStore {
	val apiKey: StateFlow<String>

	fun set(apiKey: String)

	fun clear()
}

/**
 * Encrypts the API key with an AES-256-GCM key that lives in the Android Keystore and persists only
 * the ciphertext. When the Keystore is unavailable, the key is kept in memory for the session
 * instead of being written to disk in the clear, so the user re-enters it after a restart.
 */
class SecureApiKeyStore(context: Context, logger: AppLogger) : ApiKeyStore {
	private val storage = createApiKeyStorage(context, logger)
	private val _apiKey = MutableStateFlow(storage.read())

	override val apiKey: StateFlow<String> = _apiKey.asStateFlow()

	override fun set(apiKey: String) {
		if (apiKey.isEmpty()) {
			clear()
			return
		}

		storage.write(apiKey)
		_apiKey.value = apiKey
	}

	override fun clear() {
		storage.clear()
		_apiKey.value = ""
	}
}

private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val KEYSTORE_ALIAS = "wallhavend_api_key"
private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val GCM_TAG_LENGTH_BITS = 128
private const val GCM_IV_LENGTH_BYTES = 12
private const val PREFERENCES_NAME = "wallhavend_secure_prefs"
private const val CIPHERTEXT_KEY = "api_key_ciphertext"
private const val TAG = "SecureApiKeyStore"

private fun createApiKeyStorage(context: Context, logger: AppLogger): ApiKeyStorage = runCatching {
	KeystoreApiKeyStorage(context)
}.getOrElse { exception ->
	logger.error(TAG, "Android Keystore unavailable, keeping the API key in memory only", exception)
	InMemoryApiKeyStorage()
}

private interface ApiKeyStorage {
	fun read(): String

	fun write(apiKey: String)

	fun clear()
}

private class KeystoreApiKeyStorage(context: Context) : ApiKeyStorage {
	private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
	private val secretKey = getOrCreateSecretKey()

	override fun read(): String {
		val stored = preferences.getString(CIPHERTEXT_KEY, null) ?: return ""

		return runCatching {
			decrypt(stored)
		}.getOrElse {
			// A ciphertext the Keystore key can no longer unwrap is dead weight, so drop it rather than fail every read.
			clear()
			""
		}
	}

	override fun write(apiKey: String) {
		preferences.edit().putString(CIPHERTEXT_KEY, encrypt(apiKey)).apply()
	}

	override fun clear() {
		preferences.edit().remove(CIPHERTEXT_KEY).apply()
	}

	private fun encrypt(apiKey: String): String {
		val cipher = Cipher.getInstance(TRANSFORMATION)
		cipher.init(Cipher.ENCRYPT_MODE, secretKey)
		val ciphertext = cipher.doFinal(apiKey.toByteArray(Charsets.UTF_8))

		return Base64.getEncoder().encodeToString(cipher.iv + ciphertext)
	}

	private fun decrypt(stored: String): String {
		val decoded = Base64.getDecoder().decode(stored)
		val iv = decoded.copyOfRange(0, GCM_IV_LENGTH_BYTES)
		val ciphertext = decoded.copyOfRange(GCM_IV_LENGTH_BYTES, decoded.size)

		val cipher = Cipher.getInstance(TRANSFORMATION)
		cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

		return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
	}

	private fun getOrCreateSecretKey(): SecretKey {
		val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
		val existing = keyStore.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry

		if (existing != null) {
			return existing.secretKey
		}

		val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
		val specification = KeyGenParameterSpec.Builder(KEYSTORE_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
			.setBlockModes(KeyProperties.BLOCK_MODE_GCM)
			.setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
			.setKeySize(256)
			.build()
		generator.init(specification)

		return generator.generateKey()
	}
}

private class InMemoryApiKeyStorage : ApiKeyStorage {
	private var storedApiKey = ""

	override fun read() = storedApiKey

	override fun write(apiKey: String) {
		storedApiKey = apiKey
	}

	override fun clear() {
		storedApiKey = ""
	}
}
