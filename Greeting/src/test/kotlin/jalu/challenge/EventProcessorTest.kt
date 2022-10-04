package jalu.challenge

import com.amazonaws.services.lambda.runtime.LambdaLogger
import io.mockk.*
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.*
import java.net.http.HttpResponse
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.*

class EventProcessorTest {

    private val mockedDbClient = mockk<DynamoDbClient>()
    private val mockedRestAdapter = mockk<RestAdapter>()
    private val mockedLogger = mockk<LambdaLogger>()

    private val tableName = "testTable"
    private val testNotification = Notification("Marcus", 324, "2020-05-12T16:11:54.000")

    @BeforeTest
    fun setup() {
        clearAllMocks()
        every { mockedLogger.log(any<String>()) } just runs
    }

    @Test
    fun happyPath() {
        val pushEventCapture = slot<PushEvent>()
        every { mockedDbClient.getItem(expectedGetItemRequest()) } returns mockedGetItemResponse()
        every { mockedDbClient.scan(expectedScanRequest()) } returns mockedScanResponse()
        every { mockedDbClient.putItem(expectedPutItemRequest()) } returns mockedPutItemResponse()
        every { mockedRestAdapter.sendRequest(capture(pushEventCapture)) } just runs

        EventProcessor(mockedDbClient, mockedRestAdapter, mockedLogger, tableName).process(testNotification)

        verifySequence {
            mockedDbClient.getItem(expectedGetItemRequest())
            mockedDbClient.scan(expectedScanRequest())
            mockedRestAdapter.sendRequest(any())
            mockedDbClient.putItem(expectedPutItemRequest())
        }

        assertTrue {
            pushEventCapture.captured.message.contains("Marcus")
                    && pushEventCapture.captured.recent_user_ids.size ==2
        }
    }

    @Test
    fun skipProcessedEvents() {
        val populatedResponse = GetItemResponse.builder().item(emptyMap()).build()
        every { mockedDbClient.getItem(expectedGetItemRequest()) } returns populatedResponse

        EventProcessor(mockedDbClient, mockedRestAdapter, mockedLogger, tableName).process(testNotification)

        verify {
            mockedDbClient.getItem(expectedGetItemRequest())
        }
        verify(exactly = 0) {
            mockedDbClient.scan(any<ScanRequest>())
            mockedDbClient.putItem(any<PutItemRequest>())
            mockedRestAdapter.sendRequest(any())
        }
    }

    @Test
    fun ttlIsSet() {
        val putItemRequest = expectedPutItemRequest()
        every { mockedDbClient.getItem(any<GetItemRequest>()) } returns mockedGetItemResponse()
        every { mockedDbClient.scan(any<ScanRequest>()) } returns mockedScanResponse()
        every { mockedDbClient.putItem(putItemRequest) } returns mockedPutItemResponse()
        every { mockedRestAdapter.sendRequest(any()) } just runs

        EventProcessor(mockedDbClient, mockedRestAdapter, mockedLogger, tableName).process(testNotification)

        assertTrue {
            LocalDateTime.now().toInstant(ZoneOffset.UTC)
                .isBefore(
                    Instant.ofEpochSecond(putItemRequest.item()["ttl"]?.n()?.toLong() ?: 0))
        }
        verify {
            mockedDbClient.putItem(putItemRequest)
        }
    }

    @Test
    fun firstUserReceivesMessageWithoutConnections() {
        val pushEventCapture = slot<PushEvent>()
        every { mockedDbClient.getItem(expectedGetItemRequest()) } returns mockedGetItemResponse()
        every { mockedDbClient.scan(expectedScanRequest()) } returns ScanResponse.builder().build()
        every { mockedDbClient.putItem(expectedPutItemRequest()) } returns mockedPutItemResponse()
        every { mockedRestAdapter.sendRequest(capture(pushEventCapture)) } just runs

        EventProcessor(mockedDbClient, mockedRestAdapter, mockedLogger, tableName).process(testNotification)

        verify {
            mockedRestAdapter.sendRequest(any())
        }

        assertEquals("Hi Marcus, welcome to komoot.", pushEventCapture.captured.message.trim())
    }

    @Test
    fun usersToConnectToHaveMaxAmount() {
        val pushEventCapture = slot<PushEvent>()
        every { mockedDbClient.getItem(expectedGetItemRequest()) } returns mockedGetItemResponse()
        every { mockedDbClient.scan(expectedScanRequest()) } returns ScanResponse.builder()
            .count(5)
            .items(
                mutableMapOf("id" to AttributeValue.fromN("4"), "name" to AttributeValue.fromS("Hanna")),
                mutableMapOf("id" to AttributeValue.fromN("5"), "name" to AttributeValue.fromS("Tobi")),
                mutableMapOf("id" to AttributeValue.fromN("8"), "name" to AttributeValue.fromS("Frank")),
                mutableMapOf("id" to AttributeValue.fromN("22"), "name" to AttributeValue.fromS("Julia")),
                mutableMapOf("id" to AttributeValue.fromN("9"), "name" to AttributeValue.fromS("Horst")))
            .build()
        every { mockedDbClient.putItem(expectedPutItemRequest()) } returns mockedPutItemResponse()
        every { mockedRestAdapter.sendRequest(capture(pushEventCapture)) } just runs

        EventProcessor(mockedDbClient, mockedRestAdapter, mockedLogger, tableName).process(testNotification)

        verify {
            mockedRestAdapter.sendRequest(any())
        }

        assertEquals(3, pushEventCapture.captured.recent_user_ids.size)
    }

    private fun mockedGetItemResponse(): GetItemResponse {
        return GetItemResponse.builder().build()
    }

    private fun mockedScanResponse(): ScanResponse {
        return ScanResponse.builder().count(2)
            .items(
                mutableMapOf("id" to AttributeValue.fromN("4"), "name" to AttributeValue.fromS("Hanna")),
                mutableMapOf("id" to AttributeValue.fromN("5"), "name" to AttributeValue.fromS("Tobi")))
            .build()
    }

    private fun mockedPutItemResponse(): PutItemResponse {
        val mockedResponse = mockk<PutItemResponse>()
        every { mockedResponse.sdkHttpResponse().statusCode() } returns 200
        return mockedResponse
    }

    private fun expectedGetItemRequest(): GetItemRequest {
        return GetItemRequest.builder().tableName(tableName)
            .key(mapOf("id" to AttributeValue.fromN(testNotification.id.toString()))).build()
    }

    private fun expectedScanRequest(): ScanRequest {
        return ScanRequest.builder().tableName(tableName).limit(50).build()
    }

    private fun expectedPutItemRequest(): PutItemRequest {
        val expirationTimestamp =
            LocalDateTime.now().plusDays(1).toInstant(ZoneOffset.UTC).epochSecond
        val item = mutableMapOf(
            "id" to AttributeValue.fromN(testNotification.id.toString()),
            "name" to AttributeValue.fromS(testNotification.name),
            "ttl" to AttributeValue.fromN(expirationTimestamp.toString())
        )
        return PutItemRequest.builder().tableName(tableName).item(item).build()
    }

}