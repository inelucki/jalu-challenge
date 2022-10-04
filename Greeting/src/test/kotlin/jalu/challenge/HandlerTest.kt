package jalu.challenge

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.LambdaLogger
import com.amazonaws.services.lambda.runtime.events.SNSEvent
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlin.test.Test
import kotlin.test.assertEquals

class HandlerTest {

    @Test
    fun testHappyPath() {
        val jsonInput = """{"name": "Marcus","id": 1589278470,"created_at": "2020-05-12T16:11:54.000"}"""
        val testEvent = SNSEvent().withRecords(listOf(SNSEvent.SNSRecord().withSns(SNSEvent.SNS().withMessage(jsonInput))))
        val mockedContext = mockk<Context>()
        val mockedLogger = mockk<LambdaLogger>()
        every { mockedContext.logger } returns mockedLogger
        every { mockedContext.awsRequestId } returns "1"
        every { mockedLogger.log(any<String>()) } just runs

        val actual = Handler().handleRequest(testEvent, mockedContext)

        assertEquals("event 1 processed", actual)
    }

}