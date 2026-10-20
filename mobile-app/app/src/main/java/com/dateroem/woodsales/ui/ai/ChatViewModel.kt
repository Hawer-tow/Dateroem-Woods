package com.dateroem.woodsales.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dateroem.woodsales.data.model.AiChatMessageRequest
import com.dateroem.woodsales.data.network.ApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import com.dateroem.woodsales.ui.common.friendlyError

data class ChatMessage(
    val role: String,
    val content: String,
    val suggestedRoute: String? = null,
    val suggestedLabel: String? = null,
    // "you can offer a category on conversation you click it you can see
    // conversation on this date to prevent a pile of conversations" - the
    // raw "yyyy-MM-dd'T'HH:mm:ss" the backend stores it as; dateKey below
    // is what grouping/filtering actually uses.
    val createdAt: String? = null
) {
    val dateKey: String get() = createdAt?.take(10) ?: ""
}

sealed interface ChatUiState {
    data object Loading : ChatUiState
    data class Loaded(val messages: List<ChatMessage>) : ChatUiState
    data class Error(val message: String) : ChatUiState
}

// "categorized by weeks if we are in this month it will compile the in
// weeks then after that if its over 1 month its not compiled in months.
// a total of 6 month conversation are kept apart from that more than that
// are retired not deleted just cannot be seen" - one browsable bucket:
// this month's weeks each get their own, older months are one bucket each,
// nothing older than 6 months back is listed at all (still in the
// database - see AiChatService.history() - just not offered here).
data class ChatDateGroup(val label: String, val messageCount: Int, val dateKeys: Set<String>)

private val DATE_KEY_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.US)
private val MONTH_LABEL_FORMAT = SimpleDateFormat("MMMM yyyy", Locale.US)
private val DAY_LABEL_FORMAT = SimpleDateFormat("MMM d", Locale.US)
private const val MONTHS_KEPT = 6 // this month + 5 before it

// "implement the chat bot ... to allow it to answer questions still role
// based but still the normal question and answer" - loads whatever
// conversation this user already has (so it survives closing the app),
// then each new message is appended optimistically before the reply
// comes back, same "show it immediately, reconcile on response" feel as
// a normal messaging app.
class ChatViewModel(private val api: ApiService, private val actorUserId: Int) : ViewModel() {

    private val _uiState = MutableStateFlow<ChatUiState>(ChatUiState.Loading)
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    private val _sendError = MutableStateFlow<String?>(null)
    val sendError: StateFlow<String?> = _sendError.asStateFlow()

    // null = today's live conversation (default); set = browsing one past
    // bucket (a week or a month), read-only, picked from dateGroups().
    private val _viewingBucket = MutableStateFlow<ChatDateGroup?>(null)
    val viewingBucket: StateFlow<ChatDateGroup?> = _viewingBucket.asStateFlow()

    init {
        loadHistory()
    }

    fun loadHistory() {
        _uiState.value = ChatUiState.Loading
        viewModelScope.launch {
            _uiState.value = try {
                val rows = api.aiChatHistory(actorUserId)
                ChatUiState.Loaded(rows.map { row ->
                    ChatMessage(
                        role = row["role"] as? String ?: "assistant",
                        content = row["content"] as? String ?: "",
                        suggestedRoute = row["suggestedRoute"] as? String,
                        suggestedLabel = row["suggestedLabel"] as? String,
                        createdAt = row["createdAt"] as? String
                    )
                })
            } catch (e: Exception) {
                ChatUiState.Error(friendlyError(e, "Couldn't load this right now"))
            }
        }
    }

    // This month's own days folded into weeks (most recent week first);
    // the 5 months before that each collapsed into one bucket; anything
    // older is left out entirely - "retired not deleted just cannot be
    // seen" (the rows are untouched server-side, this is purely what the
    // picker offers).
    fun dateGroups(): List<ChatDateGroup> {
        val messages = (_uiState.value as? ChatUiState.Loaded)?.messages ?: return emptyList()
        val now = Calendar.getInstance()
        val currentMonthIndex = now.get(Calendar.YEAR) * 12 + now.get(Calendar.MONTH)
        val currentWeekOfMonth = now.get(Calendar.WEEK_OF_MONTH)

        data class Bucket(var label: String, val sortKey: Long, val dateKeys: MutableSet<String> = mutableSetOf(), var count: Int = 0)
        val buckets = LinkedHashMap<String, Bucket>()

        for (message in messages) {
            if (message.dateKey.isEmpty()) continue
            val date = runCatching { DATE_KEY_FORMAT.parse(message.dateKey) }.getOrNull() ?: continue
            val cal = Calendar.getInstance().apply { time = date }
            val monthIndex = cal.get(Calendar.YEAR) * 12 + cal.get(Calendar.MONTH)
            val monthsBack = currentMonthIndex - monthIndex
            if (monthsBack !in 0 until MONTHS_KEPT) continue // retired - out of the picker

            val key: String
            val label: String
            val sortKey: Long
            if (monthsBack == 0) {
                val weekOfMonth = cal.get(Calendar.WEEK_OF_MONTH)
                key = "week:$monthIndex:$weekOfMonth"
                label = weekLabel(cal, weekOfMonth, currentWeekOfMonth)
                // Weeks sort above every month bucket, most recent week first.
                sortKey = 1_000_000L + weekOfMonth
            } else {
                key = "month:$monthIndex"
                label = MONTH_LABEL_FORMAT.format(date)
                sortKey = monthIndex.toLong()
            }
            val bucket = buckets.getOrPut(key) { Bucket(label, sortKey) }
            bucket.dateKeys.add(message.dateKey)
            bucket.count++
        }

        return buckets.values
            .sortedByDescending { it.sortKey }
            .map { ChatDateGroup(it.label, it.count, it.dateKeys) }
    }

    private fun weekLabel(cal: Calendar, weekOfMonth: Int, currentWeekOfMonth: Int): String {
        return when (weekOfMonth) {
            currentWeekOfMonth -> "This week"
            currentWeekOfMonth - 1 -> "Last week"
            else -> {
                val weekStart = (cal.clone() as Calendar).apply { set(Calendar.DAY_OF_WEEK, firstDayOfWeek) }
                val weekEnd = (weekStart.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 6) }
                "${DAY_LABEL_FORMAT.format(weekStart.time)} - ${DAY_LABEL_FORMAT.format(weekEnd.time)}"
            }
        }
    }

    fun viewBucket(group: ChatDateGroup?) { _viewingBucket.value = group }

    fun sendMessage(text: String) {
        if (text.isBlank() || _sending.value) return
        // Sending always returns to the live view - answering from inside
        // a past week/month wouldn't make sense.
        _viewingBucket.value = null

        val before = (_uiState.value as? ChatUiState.Loaded)?.messages ?: emptyList()
        _uiState.value = ChatUiState.Loaded(before + ChatMessage(role = "user", content = text))
        _sending.value = true
        _sendError.value = null

        viewModelScope.launch {
            try {
                val reply = api.sendAiChatMessage(AiChatMessageRequest(text), actorUserId)
                val current = (_uiState.value as? ChatUiState.Loaded)?.messages ?: emptyList()
                _uiState.value = ChatUiState.Loaded(
                    current + ChatMessage(
                        role = "assistant",
                        content = reply.answer,
                        suggestedRoute = reply.suggestedRoute,
                        suggestedLabel = reply.suggestedLabel
                    )
                )
            } catch (e: Exception) {
                _sendError.value = describeError(e)
            } finally {
                _sending.value = false
            }
        }
    }

    private fun describeError(e: Exception): String {
        if (e is HttpException) {
            val body = e.response()?.errorBody()?.string()
            return friendlyError(e, "Something went wrong. Please try again.", body)
        }
        return friendlyError(e, "Couldn't send message")
    }

    class Factory(private val api: ApiService, private val actorUserId: Int) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ChatViewModel(api, actorUserId) as T
    }
}
