package com.example.firebase

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore

class ChatActivity : AppCompatActivity() {
    private val db = FirebaseFirestore.getInstance() // 初始化 Firebase Firestore
    private lateinit var recyclerView: RecyclerView
    private lateinit var sentButton: ImageButton
    private lateinit var inputMessage: EditText
    private lateinit var chatAdapter: ChatAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.group_talk) // 確保對應的佈局檔案名稱正確

        // 初始化視圖
        recyclerView = findViewById(R.id.chatRecyclerView)
        sentButton = findViewById(R.id.sentButton)
        inputMessage = findViewById(R.id.messageInput)

        val username = intent.getStringExtra("username") ?: "Unknown"
        Log.d("ChatActivity", "Current logged-in user: $username")

        // 設置 RecyclerView
        val messages = mutableListOf<Message>()
        chatAdapter = ChatAdapter(messages, username)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = chatAdapter

        // 實時監聽並獲取聊天訊息
        fetchChatMessages("group")

        // 設置傳送按鈕點擊事件
        sentButton.setOnClickListener {
            Log.d("sentButton", "按下傳送按鈕了")
            val messageText = inputMessage.text.toString().trim()

            // 檢查訊息是否為空
            if (messageText.isEmpty()) {
                Toast.makeText(this, "請輸入訊息", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 發送訊息
            sendMessage("group", username, messageText)

            // 清空輸入框
            inputMessage.text.clear()

            // 滾動到最新訊息
            scrollToBottom()
        }


        // 設置登出按鈕
        val logoutButton = findViewById<Button>(R.id.logOut)
        logoutButton.setOnClickListener {
            showLogoutDialog() // 顯示登出確認對話框
        }

        // 當輸入框獲得焦點時，自動滾動到最新訊息
        inputMessage.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                scrollToBottom()
            }
        }

        // 添加鍵盤彈出監聽
        addKeyboardListener()
    }

    private fun addKeyboardListener() {
        val rootView = findViewById<View>(android.R.id.content)
        rootView.viewTreeObserver.addOnGlobalLayoutListener {
            val rect = Rect()
            rootView.getWindowVisibleDisplayFrame(rect)
            val screenHeight = rootView.rootView.height
            val keypadHeight = screenHeight - rect.bottom

            // 如果鍵盤高度超過屏幕高度的 20%，表示鍵盤彈出
            if (keypadHeight > screenHeight * 0.2) {
                scrollToBottom()
            }
        }
    }

    // 設定 RecyclerView 自動滾動
    private fun scrollToBottom() {
        recyclerView.post {
            recyclerView.scrollToPosition(chatAdapter.itemCount - 1)
        }
    }

    // 滾動到最新訊息
    private fun scrollToBottom(recyclerView: RecyclerView, adapter: ChatAdapter) {
        recyclerView.post {
            recyclerView.scrollToPosition(adapter.itemCount - 1)
        }
    }

    private fun fetchChatMessages(groupId: String) {
        val messagesRef = db.collection("Groups").document(groupId).collection("messages")
            .orderBy("timestamp")

        messagesRef.addSnapshotListener { snapshots, error ->
            if (error != null) {
                Log.w("Firestore", "Listen failed.", error)
                return@addSnapshotListener
            }

            val fetchedMessages = snapshots?.documents?.map { doc ->
                Message(
                    sender = doc.getString("sender") ?: "Unknown",
                    content = doc.getString("content") ?: "",
                    timestamp = doc.getTimestamp("timestamp") ?: com.google.firebase.Timestamp.now()
                )
            } ?: emptyList()

            // 更新 RecyclerView 的資料
            val recyclerView = findViewById<RecyclerView>(R.id.chatRecyclerView)
            val adapter = recyclerView.adapter as ChatAdapter
            if (adapter != null) {
                adapter.updateMessages(fetchedMessages)
                scrollToBottom(recyclerView, adapter)
            } else {
                Log.w("Firestore", "Adapter is null, cannot update messages.")
            }
        }
    }

    private fun sendMessage(groupId: String, sender: String, content: String) {
        val message = hashMapOf(
            "sender" to sender,
            "content" to content,
            "timestamp" to com.google.firebase.Timestamp.now()
        )
        db.collection("Groups").document(groupId).collection("messages")
            .add(message)
            .addOnSuccessListener { Log.d("Firestore", "Message sent successfully!") }
            .addOnFailureListener { e -> Log.w("Firestore", "Error sending message", e) }
    }

    private fun logout() {
        val sharedPreferences = getSharedPreferences("LoginPrefs", MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.clear() // 清除所有儲存的資料
        editor.apply()

        // 跳轉回登入頁面
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun showLogoutDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("登出")
        builder.setMessage("確定要登出嗎？")

        // 設定「確定」按鈕
        builder.setPositiveButton("確定") { dialog, _ ->
            logout() // 執行登出操作
            dialog.dismiss() // 關閉對話框
        }

        // 設定「取消」按鈕
        builder.setNegativeButton("取消") { dialog, _ ->
            dialog.dismiss() // 關閉對話框
        }

        // 顯示對話框
        builder.create().show()
    }


}

data class Message(
    val sender: String,
    val content: String,
    val timestamp: com.google.firebase.Timestamp
) {
    fun getFormattedTime(): String {
        val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        val date = timestamp.toDate()
        return sdf.format(date)
    }
}

class ChatAdapter(private var messages: MutableList<Message>, private val currentUser: String) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_RIGHT = 1
        private const val VIEW_TYPE_LEFT = 2
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].sender == currentUser) VIEW_TYPE_RIGHT else VIEW_TYPE_LEFT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_RIGHT) {
            val view = inflater.inflate(R.layout.item_message_right, parent, false)
            RightMessageViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_message_left, parent, false)
            LeftMessageViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        if (holder is RightMessageViewHolder) {
            holder.bind(message)
        } else if (holder is LeftMessageViewHolder) {
            holder.bind(message)
        }
    }

    override fun getItemCount(): Int = messages.size

    fun updateMessages(newMessages: List<Message>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

    class RightMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageContent: TextView = itemView.findViewById(R.id.message_content)
        private val messageTime: TextView = itemView.findViewById(R.id.message_time)
        private val messageSender: TextView = itemView.findViewById(R.id.message_sender)

        fun bind(message: Message) {
            messageContent.text = message.content
            messageTime.text = message.getFormattedTime()
            messageSender.text = message.sender
        }
    }

    class LeftMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageContent: TextView = itemView.findViewById(R.id.message_content)
        private val messageTime: TextView = itemView.findViewById(R.id.message_time)
        private val messageSender: TextView = itemView.findViewById(R.id.message_sender)

        fun bind(message: Message) {
            messageContent.text = message.content
            messageTime.text = message.getFormattedTime()
            messageSender.text = message.sender
        }
    }
}
