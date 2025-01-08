package com.example.firebase

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Hashtable

class MainActivity : AppCompatActivity() {
    private lateinit var loginButton: Button
    private val db by lazy { FirebaseFirestore.getInstance() } // 延遲初始化，優化資源使用

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkLoginStatus()
        setContentView(R.layout.login_page)

        val account = findViewById<TextView>(R.id.account)
        val pw = findViewById<TextView>(R.id.password)
        val loginReturn = findViewById<TextView>(R.id.loginReturn)

        loginButton = findViewById(R.id.loginButton)
        loginButton.setOnClickListener {
            val accountText = account.text.toString()
            val passwordText = pw.text.toString()

            if (accountText.isEmpty() || passwordText.isEmpty()) {
                loginReturn.text = "請輸入帳號和密碼！"
                return@setOnClickListener
            }

            checkLogin(accountText, passwordText)
        }
    }

    private fun getAccountData(callback: (Hashtable<String, String>) -> Unit) {
        val userArr = Hashtable<String, String>()
        db.collection("account")
            .get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    val passwordValue = document.getString("password") ?: "Unknown"
                    userArr[document.id] = passwordValue
                }
                callback(userArr)
            }
            .addOnFailureListener { exception ->
                Log.w("Firestore", "Error getting documents: ", exception)
                callback(userArr) // 回傳空的 userArr
            }
    }

    private fun checkLogin(account: String, pw: String) {
        val loginReturn = findViewById<TextView>(R.id.loginReturn)
        getAccountData { accountData ->
            Log.d("accountData", accountData.toString())
            if (accountData[account] == pw) {
                Log.d("LoginStatus", "Login Successful")
                loginReturn.text = "登入成功！"
                saveLoginInfo(account, pw)
                Log.d("Now Login user",account)
                delayNavigationToChatActivity()
            } else {
                Log.d("LoginStatus", "Login Failed")
                loginReturn.text = "帳號或密碼錯誤！"
            }
        }
    }

    private fun saveLoginInfo(account: String, pw: String) {
        getSharedPreferences("LoginPrefs", MODE_PRIVATE).edit().apply {
            putString("account", account)
            putString("password", pw)
            putBoolean("isLoggedIn", true) // 標記已登入
            apply()
        }
    }

    private fun checkLoginStatus() {
        val sharedPreferences = getSharedPreferences("LoginPrefs", MODE_PRIVATE)
        val isLoggedIn = sharedPreferences.getBoolean("isLoggedIn", false)

        if (isLoggedIn) {
            // 如果已登入，直接跳轉到 ChatActivity
            navigateToChatActivity()
        }
    }

    private fun delayNavigationToChatActivity() {
        Handler(Looper.getMainLooper()).postDelayed({
            navigateToChatActivity()
        }, 2000) // 延遲 2 秒
    }

    private fun navigateToChatActivity() {
        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra("username", getSharedPreferences("LoginPrefs", MODE_PRIVATE).getString("account", "Unknown"))
        }
        startActivity(intent)
        finish() // 結束當前 Activity，避免返回
    }
}