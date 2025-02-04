package com.example.firebase
import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
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
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 首先創建一個新的 utility 類別 (AppUtils.kt)
object AppUtils {
    fun isAppInForeground(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val appProcesses = activityManager.runningAppProcesses ?: return false
        return appProcesses.any {
            it.processName == context.packageName &&
                    it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        }
    }
}

class ChatActivity : AppCompatActivity() {
    private val db = FirebaseFirestore.getInstance()
    private lateinit var recyclerView: RecyclerView
    private lateinit var sentButton: ImageButton
    private lateinit var inputMessage: EditText
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var username: String

    // FCM 相關設定
    private val fcm = FirebaseMessaging.getInstance()
    companion object {
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1001
        const val CHANNEL_ID = "chat_notifications"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializeFirebase()
        setContentView(R.layout.group_talk)

        // 請求通知權限
        requestNotificationPermission()

        initializeViews()
        setupNotificationChannel()
        setupRecyclerView()
        setupListeners()

        // 訂閱消息更新
        subscribeToMessages()
    }

    private fun subscribeToMessages() {
        // 監聽一般消息
        db.collection("Groups")
            .document("group")
            .collection("messages")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(1)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("Firestore", "Listen failed.", e)
                    return@addSnapshotListener
                }

                snapshots?.documentChanges?.forEach { change ->
                    if (change.type == DocumentChange.Type.ADDED) {
                        val message = change.document
                        val sender = message.getString("sender") ?: return@forEach
                        val content = message.getString("content") ?: return@forEach

                        if (sender != username && !AppUtils.isAppInForeground(this)) {
                            NotificationHelper.showNotification(
                                this,
                                CHANNEL_ID,
                                sender,
                                content
                            )
                        }
                    }
                }
            }
    }

    private fun initializeFirebase() {
        FirebaseApp.initializeApp(this)

        // 獲取 FCM token
        fcm.token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("FCM", "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }
            val token = task.result
            Log.d("FCM", "FCM Token: $token")
        }

        // 訂閱群組話題
        fcm.subscribeToTopic("chat_group")
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("FCM", "Subscribed to chat group")
                } else {
                    Log.e("FCM", "Failed to subscribe", task.exception)
                }
            }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST_CODE
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            NOTIFICATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d("Permissions", "Notification permission granted")
                } else {
                    Log.d("Permissions", "Notification permission denied")
                    // 可以在這裡提示用戶為什麼需要通知權限
                }
            }
        }
    }

    private fun initializeViews() {
        recyclerView = findViewById(R.id.chatRecyclerView)
        sentButton = findViewById(R.id.sentButton)
        inputMessage = findViewById(R.id.messageInput)

        username = intent.getStringExtra("username") ?: "Unknown"
        Log.d("ChatActivity", "Current logged-in user: $username")
    }

    private fun setupNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.channel_description)
                enableLights(true)
                enableVibration(true)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun setupRecyclerView() {
        val messages = mutableListOf<Message>()
        chatAdapter = ChatAdapter(messages, username)
        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@ChatActivity)
            adapter = chatAdapter
        }
        "group".fetchChatMessages()
    }

    private fun setupListeners() {
        setupSendButton()
        setupLogoutButton()
        setupInputMessageListener()
        addKeyboardListener()
    }

    private fun setupSendButton() {
        sentButton.setOnClickListener {
            val messageText = inputMessage.text.toString().trim()
            if (messageText.isEmpty()) {
                Toast.makeText(this, "請輸入訊息", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            "group".sendMessage(username, messageText)
            inputMessage.text.clear()
            scrollToBottom()
        }
    }

    private fun setupLogoutButton() {
        findViewById<Button>(R.id.logOut).setOnClickListener {
            showLogoutDialog()
        }
    }

    private fun setupInputMessageListener() {
        inputMessage.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) scrollToBottom()
        }
    }

    private fun addKeyboardListener() {
        val rootView = findViewById<View>(android.R.id.content)
        rootView.viewTreeObserver.addOnGlobalLayoutListener {
            val rect = Rect()
            rootView.getWindowVisibleDisplayFrame(rect)
            val screenHeight = rootView.rootView.height
            val keypadHeight = screenHeight - rect.bottom
            if (keypadHeight > screenHeight * 0.2) {
                scrollToBottom()
            }
        }
    }

    private fun scrollToBottom() {
        recyclerView.post {
            chatAdapter.itemCount.takeIf { it > 0 }?.let { count ->
                recyclerView.scrollToPosition(count - 1)
            }
        }
    }

    private fun String.fetchChatMessages() {
        db.collection("Groups").document(this).collection("messages")
            .orderBy("timestamp")
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.w("Firestore", "Listen failed.", error)
                    return@addSnapshotListener
                }

                val fetchedMessages = snapshots?.mapNotNull { doc ->
                    Message(
                        sender = doc.getString("sender") ?: return@mapNotNull null,
                        content = doc.getString("content") ?: return@mapNotNull null,
                        timestamp = doc.getTimestamp("timestamp") ?: Timestamp.now()
                    )
                } ?: emptyList()

                chatAdapter.updateMessages(fetchedMessages)
                scrollToBottom()
            }
    }

    private fun String.sendMessage(sender: String, content: String) {
        // 在發送訊息時同時更新兩個集合
        val message = hashMapOf(
            "sender" to sender,
            "content" to content,
            "timestamp" to Timestamp.now()
        )

        // 1. 新增訊息到聊天記錄
        db.collection("Groups")
            .document(this)
            .collection("messages")
            .add(message)
            .addOnSuccessListener { documentRef ->
                // 2. 新增通知訊息到 notifications 集合
                val notification = hashMapOf(
                    "messageId" to documentRef.id,
                    "sender" to sender,
                    "content" to content,
                    "timestamp" to Timestamp.now(),
                    "processed" to false
                )

                db.collection("notifications")
                    .add(notification)
                    .addOnFailureListener { e ->
                        Log.e("Firestore", "Error adding notification", e)
                    }
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error sending message", e)
                Toast.makeText(this@ChatActivity, "發送訊息失敗", Toast.LENGTH_SHORT).show()
            }
    }

    private fun logout() {
        // 取消訂閱聊天室的通知
        FirebaseMessaging.getInstance().unsubscribeFromTopic("chat_group")
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("FCM", "Unsubscribed from chat_group topic")
                } else {
                    Log.e("FCM", "Failed to unsubscribe", task.exception)
                }
            }

        // 更新 Firestore 中的 FCM Token
        val prefs = getSharedPreferences("LoginPrefs", MODE_PRIVATE)
        val username = prefs.getString("username", null)
        if (username != null) {
            FirebaseFirestore.getInstance()
                .collection("account")
                .document(username)
                .update("fcmToken", null)
                .addOnFailureListener { e ->
                    Log.e("FCM", "Error removing token", e)
                }
        }

        // 清除所有的 App 資料，還原為剛安裝的狀態
        (getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.clearApplicationUserData()
    }

    private fun showLogoutDialog() {
        AlertDialog.Builder(this).apply {
            setTitle("登出")
            setMessage("確定要登出嗎？")
            setPositiveButton("確定") { dialog, _ ->
                logout()
                dialog.dismiss()
            }
            setNegativeButton("取消") { dialog, _ ->
                dialog.dismiss()
            }
            create().show()
        }
    }
}

data class Message(
    val sender: String,
    val content: String,
    val timestamp: Timestamp
) {
    fun getFormattedTime(): String {
        return SimpleDateFormat("HH:mm", Locale.getDefault())
            .format(timestamp.toDate())
    }
}

class ChatAdapter(
    private var messages: MutableList<Message>,
    private val currentUser: String
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_RIGHT = 1
        private const val VIEW_TYPE_LEFT = 2
    }

    override fun getItemViewType(position: Int): Int =
        if (messages[position].sender == currentUser) VIEW_TYPE_RIGHT else VIEW_TYPE_LEFT

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_RIGHT -> RightMessageViewHolder(
                inflater.inflate(R.layout.item_message_right, parent, false)
            )
            else -> LeftMessageViewHolder(
                inflater.inflate(R.layout.item_message_left, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        when (holder) {
            is RightMessageViewHolder -> holder.bind(message)
            is LeftMessageViewHolder -> holder.bind(message)
        }
    }

    override fun getItemCount(): Int = messages.size

    @SuppressLint("NotifyDataSetChanged")
    fun updateMessages(newMessages: List<Message>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

    abstract class BaseMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageContent: TextView = itemView.findViewById(R.id.message_content)
        private val messageTime: TextView = itemView.findViewById(R.id.message_time)
        private val messageSender: TextView = itemView.findViewById(R.id.message_sender)

        fun bind(message: Message) {
            messageContent.text = message.content
            messageTime.text = message.getFormattedTime()
            messageSender.text = message.sender
        }
    }

    class RightMessageViewHolder(itemView: View) : BaseMessageViewHolder(itemView)
    class LeftMessageViewHolder(itemView: View) : BaseMessageViewHolder(itemView)
}

fun formatTimestamp(timestamp: String): String {
    // 將時間戳轉換為 Long 型別
    val timeInMillis = timestamp.toLongOrNull() ?: System.currentTimeMillis()

    // 使用 SimpleDateFormat 格式化時間
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    val date = Date(timeInMillis)
    return dateFormat.format(date)
}

object NotificationHelper {
    fun showNotification(
        context: Context,
        channelId: String,
        sender: String,
        content: String
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 創建開啟聊天室的 Intent
        val intent = Intent(context, ChatActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(sender)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content)) // ✅ 確保顯示完整內容
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}

// FCM Service 處理通知
class MyFirebaseMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // 如果應用程式在前景，不顯示通知
        if (AppUtils.isAppInForeground(this)) return

        // 處理資料訊息
        if (remoteMessage.data.isNotEmpty()) {
            val sender = remoteMessage.data["sender"] ?: "未知發送者"
            val content = remoteMessage.data["content"] ?: ""
            val timestamp = remoteMessage.data["timestamp"] ?: System.currentTimeMillis().toString()
            // 將時間戳轉換為可讀格式
            val formattedTime = formatTimestamp(timestamp)

            // 在通知中顯示發送時間
            val notificationContent = "$content\n發送時間：$formattedTime"

            NotificationHelper.showNotification(
                this,
                ChatActivity.CHANNEL_ID,
                sender,
                notificationContent
            )
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // 更新 token 到 Firestore
        updateTokenToFirestore(token)
    }

    private fun updateTokenToFirestore(token: String) {
        // 這裡需要取得當前用戶ID
        val prefs = getSharedPreferences("LoginPrefs", MODE_PRIVATE)
        val username = prefs.getString("username", null) ?: return

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(username)
            .update("fcmToken", token)
            .addOnFailureListener { e ->
                Log.e("FCM", "Error updating token", e)
            }
    }
}

