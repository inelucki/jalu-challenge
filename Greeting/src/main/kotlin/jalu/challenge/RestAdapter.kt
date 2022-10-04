package jalu.challenge

import com.amazonaws.services.lambda.runtime.LambdaLogger
import com.google.gson.Gson
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

private const val BACKEND_URL_KEY = "BACKEND_URL"

class RestAdapter(
    private val logger: LambdaLogger
) {

    fun sendRequest(pushEvent: PushEvent) {
        val payload = Gson().toJson(pushEvent)
        logger.log("sending this payload to the backend: $payload")

        val url = System.getenv(BACKEND_URL_KEY)
        val httpClient = HttpClient.newHttpClient()
        val request = HttpRequest.newBuilder(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build()

        val httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.discarding())
        logger.log("received status code from backend: ${httpResponse.statusCode()}")
    }
}