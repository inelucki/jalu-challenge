package jalu.challenge

import com.amazonaws.services.lambda.runtime.LambdaLogger
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.*
import java.time.LocalDateTime
import java.time.ZoneOffset

private const val PUSH_EVENT_SENDER_KEY = "PUSH_EVENT_SENDER"

class EventProcessor(
    private val dbClient: DynamoDbClient,
    private val restAdapter: RestAdapter,
    private val logger: LambdaLogger,
    private val tableName: String
) {

    fun process(notification: Notification) {
        logger.log("Processing event for user ID ${notification.id}")

        if (wasNotificationAlreadyProcessed(notification)) {
            logger.log("Notification already processed. Skipping this event")
            return
        }

        sendPushEvent(notification, selectUsers())
        persistNotification(notification)
    }

    private fun wasNotificationAlreadyProcessed(notification: Notification): Boolean {
        val lookupId = dbClient.getItem(
            GetItemRequest.builder().tableName(tableName)
                .key(mapOf("id" to AttributeValue.fromN(notification.id.toString()))).build()
        )
        return lookupId.hasItem()
    }

    private fun selectUsers(): List<MutableMap<String, AttributeValue>> {
        val scanResponse = dbClient.scan(ScanRequest.builder().tableName(tableName).limit(50).build())
        val selectedUsers = scanResponse.items().toMutableSet().shuffled().apply {
            if (this.size > 3) return this.slice(0..2)
        }
        return selectedUsers
    }


    private fun sendPushEvent(notification: Notification, selectedUsers: List<MutableMap<String, AttributeValue>>) {
        val names = mutableListOf<String>()
        val ids = mutableListOf<Long>()
        selectedUsers.forEach {
            val name = it["name"]?.s()
            val id = it["id"]?.n()
            if (id != null && name != null) {
                ids.add(id.toLong())
                names.add(name)
            }
        }

        val connections: String =
            if (names.size >= 3)  "${names[0]}, ${names[1]} and ${names[2]} also joined recently."
            else if (names.size == 2) "${names[0]} and ${names[1]} also joined recently."
            else if (names.size == 1) "${names[0]} also joined recently."
            else ""

        val welcomeMessage = "Hi ${notification.name}, welcome to komoot. $connections"
        val sender = System.getenv(PUSH_EVENT_SENDER_KEY) ?: "unknown"
        val pushEvent = PushEvent(sender, notification.id, welcomeMessage, ids)

        restAdapter.sendRequest(pushEvent)
    }

    private fun persistNotification(notification: Notification): PutItemResponse {
        val expirationTimestamp =
            LocalDateTime.now().plusDays(1).toInstant(ZoneOffset.UTC).epochSecond
        val item = mutableMapOf(
            "id" to AttributeValue.fromN(notification.id.toString()),
            "name" to AttributeValue.fromS(notification.name),
            "ttl" to AttributeValue.fromN(expirationTimestamp.toString())
        )
        return dbClient.putItem(
            PutItemRequest.builder().tableName(tableName).item(item).build()
        ).also {
            logger.log("received status code from dynamodb: ${it.sdkHttpResponse().statusCode()}")
        }
    }
}