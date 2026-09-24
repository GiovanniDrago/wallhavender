package xyz.attacktive.wallhavend

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import xyz.attacktive.wallhavend.data.api.WallhavenApiService

class WallhavenApiServiceTest {
	private val server = MockWebServer()
	private lateinit var service: WallhavenApiService

	@Before
	fun setUp() {
		server.start()
		service = Retrofit.Builder()
			.baseUrl(server.url("/"))
			.addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType()))
			.build()
			.create(WallhavenApiService::class.java)
	}

	@After
	fun tearDown() {
		server.shutdown()
	}

	@Test
	fun `the api key travels in the X-API-Key header, never in the query string`() = runTest {
		server.enqueue(MockResponse().setResponseCode(401))

		runCatching {
			service.search(query = null, categories = "111", purity = "100", ratios = null, atleast = null, sorting = "date_added", seed = null, topRange = null, page = null, colors = null, apiKey = "secret")
		}

		val request = server.takeRequest()

		assertEquals("secret", request.getHeader("X-API-Key"))
		assertNull(request.requestUrl?.queryParameter("apikey"))
	}
}
