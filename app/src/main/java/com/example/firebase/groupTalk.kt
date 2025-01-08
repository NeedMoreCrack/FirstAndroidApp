package com.example.firebase

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class ChatActivity : AppCompatActivity() {
    private lateinit var textView2: TextView
    private val db = FirebaseFirestore.getInstance()

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.group_talk) // 確保對應的佈局檔案名稱正確

        // 取得 TextView
        textView2 = findViewById(R.id.textView2)

        // 傳送按鈕
        val sentButton = findViewById<ImageButton>(R.id.sentButton)
        sentButton.setOnClickListener() {
            Log.d("sentButton","按下傳送按鈕了")
        }

        // 呼叫方法獲取聊天資訊
        fetchChatData()

        val logoutButton = findViewById<Button>(R.id.logOut)
        logoutButton.setOnClickListener {
            showLogoutDialog() // 顯示登出確認對話框
        }
    }

    private fun fetchChatData() {
        db.collection("Notes").document("group")
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val dataMap = document.data ?: emptyMap()
                    val allText = dataMap.entries.joinToString(separator = "\n") { "${it.key}: ${it.value}" }
                    textView2.text = allText
                    Log.d("Firestore", "Document Data: $dataMap")
                } else {
                    Log.d("Firestore", "No such document")
                }
            }
            .addOnFailureListener { exception ->
                Log.w("Firestore", "Error getting document.", exception)
            }
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

    private fun fetchChatMessages(groupId: String) {
        val messagesRef = db.collection("Groups").document(groupId).collection("messages")
        messagesRef.orderBy("timestamp") // 按時間順序排序
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.w("Firestore", "Listen failed.", error)
                    return@addSnapshotListener
                }
                val chatContent = StringBuilder()
                for (doc in snapshots!!) {
                    val sender = doc.getString("sender") ?: "Unknown"
                    val content = doc.getString("content") ?: ""
                    chatContent.append("$sender: $content\\n")
                }
                textView2.text = chatContent.toString()
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


}