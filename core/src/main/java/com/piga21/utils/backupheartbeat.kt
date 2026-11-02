package com.piga21.utils

//suspend fun sendGameRequest(opponentId: String): Boolean {
//    // 1. First, send a heartbeat
//    val isConnectionAlive = sendHeartbeatAndWaitForResponse()
//
//    if (!isConnectionAlive) {
//        showReconnectMessage()
//        return false // Abort if no connection
//    }
//
//    // 2. Only send the game request if heartbeat succeeded
//    val request = JSONObject().apply {
//        put("action", "request_game")
//        put("opponent_id", opponentId)
//    }
//    webSocket.send(request.toString())
//    return true
//}
//
//private suspend fun sendHeartbeatAndWaitForResponse(): Boolean {
//    try {
//        val heartbeat = JSONObject().apply {
//            put("action", "heartbeat")
//        }
//        webSocket.send(heartbeat.toString())
//
//        // Wait for a response (e.g., 3-second timeout)
//        return withTimeoutOrNull(3000) {
//            waitForNextMessage { msg ->
//                msg.optString("action") == "heartbeat"
//            } != null
//        } ?: false
//    } catch (e: Exception) {
//        return false
//    }
//}
