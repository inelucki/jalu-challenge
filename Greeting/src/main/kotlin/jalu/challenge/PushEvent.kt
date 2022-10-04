package jalu.challenge

data class PushEvent(
    val sender: String,
    val receiver: Long,
    val message: String,
    val recent_user_ids: List<Long>
)
