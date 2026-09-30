package com.example.aiapp

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
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
    private var isClipboardRead = false

    private lateinit var etUserInput: EditText
    private lateinit var btnSend: Button
    private lateinit var btnOption1: Button
    private lateinit var btnOption2: Button
    private lateinit var layoutExtraButtons: LinearLayout
    private lateinit var btnRefresh: Button
    private lateinit var btnUseOriginal: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_popup)

        clipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        
        etUserInput = findViewById(R.id.etUserInput)
        btnSend = findViewById(R.id.btnSend)
        btnOption1 = findViewById(R.id.btnOption1)
        btnOption2 = findViewById(R.id.btnOption2)
        layoutExtraButtons = findViewById(R.id.layoutExtraButtons)
        btnRefresh = findViewById(R.id.btnRefresh)
        btnUseOriginal = findViewById(R.id.btnUseOriginal)

        // 1. 최초 전송 버튼
        btnSend.setOnClickListener {
            fetchAiResponses(etUserInput.text.toString())
        }

        // 2. 새로고침 버튼
        btnRefresh.setOnClickListener {
            fetchAiResponses(etUserInput.text.toString())
        }

        // 3. 내 초안 그대로 쓰기 버튼 (AI 무시)
        btnUseOriginal.setOnClickListener {
            val originalText = etUserInput.text.toString()
            if (originalText.isNotBlank()) {
                clipboardManager.setPrimaryClip(ClipData.newPlainText("AI_Result", originalText))
                Toast.makeText(this, "초안이 복사되었습니다.", Toast.LENGTH_SHORT).show()
                finish() // 복사 후 팝업 자동 종료
            } else {
                Toast.makeText(this, "입력된 초안이 없습니다.", Toast.LENGTH_SHORT).show()
            }
        }

        // AI가 만들어준 옵션 선택 시 복사 및 종료
        val onOptionSelected = View.OnClickListener { view ->
            clipboardManager.setPrimaryClip(ClipData.newPlainText("AI_Result", (view as Button).text.toString()))
            Toast.makeText(this, "클립보드에 복사되었습니다.", Toast.LENGTH_SHORT).show()
            finish() // 팝업 자동 종료
        }

        btnOption1.setOnClickListener(onOptionSelected)
        btnOption2.setOnClickListener(onOptionSelected)
    }

    // AI API 호출을 전담하는 함수 (전송 & 새로고침 버튼이 공통으로 사용)
    private fun fetchAiResponses(userInstruction: String) {
        if (userInstruction.isBlank()) {
            Toast.makeText(this, "답변 초안을 먼저 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        val prefs = getSharedPreferences("AI_APP_PREFS", MODE_PRIVATE)
        val apiKey = prefs.getString("API_KEY", "") ?: ""
        val defaultSystemPrompt = "너는 훌륭한 대화 답변 어시스턴트야. 사용자가 '대화 문맥(상대방의 말 등)'과 '자신이 쓰려는 답변 초안'을 줄 거야. 문맥에 아주 자연스럽게 이어지도록 답변 초안을 다듬어서 2가지 다른 버전으로 만들어줘. 절대 다른 사족 없이 [\"버전1\", \"버전2\"] 형태의 JSON 배열(Array)로만 대답해야 해."
        val systemPrompt = prefs.getString("PROMPT_TEXT", "")?.takeIf { it.isNotBlank() } ?: defaultSystemPrompt

        if (apiKey.isEmpty()) {
            Toast.makeText(this, "앱 메인화면에서 DeepSeek API 키를 먼저 입력해주세요.", Toast.LENGTH_LONG).show()
            return
        }

        // 로딩 상태 UI 변경
        btnSend.text = "DeepSeek가 작성 중..."
        btnSend.isEnabled = false
        btnRefresh.isEnabled = false
        btnOption1.text = "로딩 중..."
        btnOption2.text = "로딩 중..."

        thread {
            try {
                val url = URL("https://api.deepseek.com/chat/completions")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Authorization", "Bearer $apiKey")
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true

                val prompt = "대화 문맥(복사한 글): $copiedText\n내가 쓰려는 답변 초안: $userInstruction"
                
                val jsonBody = JSONObject().apply {
                    put("model", "deepseek-chat")
                    put("messages", org.json.JSONArray().apply {
                        put(JSONObject().apply { put("role", "system"); put("content", systemPrompt) })
                        put(JSONObject().apply { put("role", "user"); put("content", prompt) })
                    })
                    put("temperature", 0.7)
                    put("max_tokens", 4000)
                }

                val writer = OutputStreamWriter(conn.outputStream)
                writer.write(jsonBody.toString())
                writer.flush()
                writer.close()

                if (conn.responseCode == 200) {
                    val responseStr = conn.inputStream.bufferedReader().use { it.readText() }
                    var content = JSONObject(responseStr)
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                    
                    content = content.replace("```json", "").replace("```", "").trim()
                    
                    var answer1 = "결과를 읽을 수 없습니다."
                    var answer2 = "다시 시도해주세요."
                    
                    try {
                        val resultList = org.json.JSONArray(content)
                        answer1 = resultList.getString(0)
                        answer2 = resultList.getString(1)
                    } catch (e: Exception) {
                        val parts = content.split("\",\"")
                        if (parts.isNotEmpty()) answer1 = parts[0].replace("[\\[\\]\"]".toRegex(), "").trim()
                        if (parts.size > 1) answer2 = parts[1].replace("[\\[\\]\"]".toRegex(), "").trim()
                    }

                    runOnUiThread {
                        btnOption1.visibility = View.VISIBLE
                        btnOption2.visibility = View.VISIBLE
                        layoutExtraButtons.visibility = View.VISIBLE
                        
                        btnOption1.text = answer1
                        btnOption2.text = answer2
                        
                        btnSend.visibility = View.GONE
                        btnRefresh.isEnabled = true
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this@AiPopupActivity, "API 오류: ${conn.responseCode}", Toast.LENGTH_SHORT).show()
                        resetUiOnFailure()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@AiPopupActivity, "네트워크 통신 실패", Toast.LENGTH_SHORT).show()
                    resetUiOnFailure()
                }
            }
        }
    }

    private fun resetUiOnFailure() {
        btnSend.text = "전송"
        btnSend.isEnabled = true
        btnRefresh.isEnabled = true
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        
        if (hasFocus && !isClipboardRead) {
            isClipboardRead = true
            val clipData = clipboardManager.primaryClip
            
            if (clipData != null && clipData.itemCount > 0) {
                copiedText = clipData.getItemAt(0).text.toString()
                clipboardManager.setPrimaryClip(ClipData.newPlainText("empty", ""))
            } else {
                Toast.makeText(this, "복사된 텍스트가 없습니다. 문맥을 먼저 복사해주세요.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}
