package com.example.aiapp

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = getSharedPreferences("AI_APP_PREFS", MODE_PRIVATE)

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                startActivity(intent)
            } else {
                startService(Intent(this, FloatingService::class.java))
                Toast.makeText(this, "플로팅 버튼 활성화", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btnStop).setOnClickListener {
            stopService(Intent(this, FloatingService::class.java))
            Toast.makeText(this, "플로팅 버튼 비활성화", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnInputPrompt).setOnClickListener { showInputDialog("프롬프트 설정", "PROMPT_TEXT") }
        findViewById<Button>(R.id.btnInputApi).setOnClickListener { showInputDialog("API 키 입력", "API_KEY") }
    }

    private fun showInputDialog(title: String, prefKey: String) {
        val editText = EditText(this)
        editText.setText(prefs.getString(prefKey, ""))
        AlertDialog.Builder(this).setTitle(title).setView(editText)
            .setPositiveButton("저장") { _, _ -> prefs.edit().putString(prefKey, editText.text.toString()).apply() }
            .setNegativeButton("취소", null).show()
    }
}
