package com.dateroem.woodsales.data.model

// "implement the chat bot ... to allow it to answer questions still role
// based ... e.g. where can i do an mpesa stk push ... provide a short cut
// to that section as an alternative" - suggestedRoute/suggestedLabel are
// non-null only when the assistant's answer points somewhere specific in
// the app (and the backend has already checked it's a route this user's
// role can actually reach) - the chat screen renders a real button for
// it when present.
data class AiChatMessageRequest(val message: String)

data class AiChatReplyResponse(
    val answer: String,
    val suggestedRoute: String?,
    val suggestedLabel: String?
)
