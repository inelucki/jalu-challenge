package jalu.challenge

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.events.SNSEvent
import com.google.gson.Gson
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

private const val AWS_REGION_KEY = "AWS_REGION"
private const val TABLE_NAME_KEY = "TABLE"

class Handler : RequestHandler<SNSEvent, String> {
    override fun handleRequest(event: SNSEvent, context: Context): String {
        val logger = context.logger
        try {
            val gson = Gson()
            val dynamoDbClient = DynamoDbClient.builder().region(Region.of(System.getenv(AWS_REGION_KEY))).build()
            val restAdapter = RestAdapter(logger)

            val tableName = System.getenv(TABLE_NAME_KEY)
            val eventProcessor = EventProcessor(dynamoDbClient, restAdapter, logger, tableName)

            event.records
                .map { record -> gson.fromJson(record.sns.message, Notification::class.java) }
                .forEach { notification -> eventProcessor.process(notification) }

        } catch (exception: Exception) {
            logger.log("an ERROR occurred. message: ${exception.message} and stacktrace: ${exception.stackTraceToString()}")
        }

        return "event ${context.awsRequestId} processed"
    }
}