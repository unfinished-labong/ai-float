package com.example.aiapp

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class AiPopupActivity : AppCompatActivity() {
    private lateinit var clipboardManager: ClipboardManager
    private var copiedText: String = ""
    private var isClipboardRead = false // 클립보드를 중복으로 읽지 않도록 방지하는 플래그

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_popup)

        clipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        
        // 버튼 및 UI 초기화 (클립보드 읽기는 여기서 하지 않음)
        val etUserInput = findViewById<EditText>(R.id.etUserInput)
        val btnSend = findViewById<Button>(R.id.btnSend)
        val btnOption1 = findViewById<Button>(R.id.btnOption1)
        val btnOption2 = findViewById<Button>(R.id.btnOption2)

        btnSend.setOnClickListener {
            val userInstruction = etUserInput.text.toString()
            btnSend.text = "DeepSeek가 작성 중..."
            btnSend.isEnabled = false

            val prefs = getSharedPreferences("AI_APP_PREFS", MODE_PRIVATE)
            val apiKey = prefs.getString("API_KEY", "") ?: ""
            val defaultSystemPrompt = "너는 텍스트 변환기야. 사용자의 원본 텍스트를 지시사항에 맞게 변환해. 반드시 2가지 다른 버전을 만들어내고, 다른 사족 없이 [\"버전1\", \"버전2\"] 형태의 JSON 배열(Array)로만 대답해."
            val systemPrompt = prefs.getString("PROMPT_TEXT", "")?.takeIf { it.isNotBlank() } ?: defaultSystemPrompt

            if (apiKey.isEmpty()) {
                Toast.makeText(this, "앱 메인화면에서 DeepSeek API 키를 먼저 입력해주세요.", Toast.LENGTH_LONG).show()
                btnSend.text = "전송"
                btnSend.isEnabled = true
                return@setOnClickListener
            }

            thread {
                try {
                    val url = URL("https://api.deepseek.com/chat/completions")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Authorization", "Bearer $apiKey")
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.doOutput = true

                    val prompt = "원본 텍스트: $copiedText\n사용자 지시사항: $userInstruction"
                    
                    val jsonBody = JSONObject().apply {
                        put("model", "deepseek-chat")
                        put("messages", org.json.JSONArray().apply {
                            put(JSONObject().apply { put("role", "system"); put("content", systemPrompt) })
                            put(JSONObject().apply { put("role", "user"); put("content", prompt) })
                        })
                        put("temperature", 0.7)
                    }

                    val writer = OutputStreamWriter(conn.outputStream)
                    writer.write(jsonBody.toString())
                    writer.flush()
                    writer.close()

                    if (conn.responseCode == 200) {
                        val responseStr = conn.inputStream.bufferedReader().use { it.readText() }
                        val content = JSONObject(responseStr)
                            .getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content")
                        
                        var answer1 = "결과를 읽을 수 없습니다."
                        var answer2 = "다시 시도해주세요."
                        
                        try {
                            val resultList = org.json.JSONArray(content)
                            answer1 = resultList.getString(0)
                            answer2 = resultList.getString(1)
                        } catch (e: Exception) {
                            val parts = content.replace("[\\[\\]\"]".toRegex(), "").split(",")
                            if (parts.isNotEmpty()) answer1 = parts[0].trim()
                            if (parts.size > 1) answer2 = parts[1].trim()
                        }

                        runOnUiThread {
                            btnOption1.visibility = View.VISIBLE
                            btnOption2.visibility = View.VISIBLE
                            btnOption1.text = answer1
                            btnOption2.text = answer2
                            btnSend.visibility = View.GONE
                        }
                    } else {
                        runOnUiThread {
                            Toast.makeText(this@AiPopupActivity, "API 오류: ${conn.responseCode}", Toast.LENGTH_SHORT).show()
                            btnSend.text = "전송"
                            btnSend.isEnabled = true
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(this@AiPopupActivity, "네트워크 통신 실패", Toast.LENGTH_SHORT).show()
                        btnSend.text = "전송"
                        btnSend.isEnabled = true
                    }
                }
            }
        }

        val onOptionSelected = View.OnClickListener { view ->
            clipboardManager.setPrimaryClip(ClipData.newPlainText("AI_Result", (view as Button).text.toString()))
            Toast.makeText(this, "클립보드에 복사되었습니다.", Toast.LENGTH_SHORT).show()
            finish()
        }

        btnOption1.setOnClickListener(onOptionSelected)
        btnOption2.setOnClickListener(onOptionSelected)
    }

    // 팝업창이 화면에 완전히 뜨고 포커스를 얻은 직후에 실행되는 함수
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        
        if (hasFocus && !isClipboardRead) {
            isClipboardRead = true // 한 번만 읽도록 처리
            val clipData = clipboardManager.primaryClip
            
            if (clipData != null && clipData.itemCount > 0) {
                copiedText = clipData.getItemAt(0).text.toString()
                // 성공적으로 읽었으면 클립보드 비우기
                clipboardManager.setPrimaryClip(ClipData.newPlainText("empty", ""))
            } else {
                Toast.makeText(this, "복사된 텍스트가 없습니다.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}
