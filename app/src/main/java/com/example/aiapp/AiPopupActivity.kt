package com.example.aiapp

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlin.concurrent.thread

class AiPopupActivity : AppCompatActivity() {
    private lateinit var clipboardManager: ClipboardManager
    private var copiedText: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_popup)

        clipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = clipboardManager.primaryClip
        if (clipData != null && clipData.itemCount > 0) {
            copiedText = clipData.getItemAt(0).text.toString()
            clipboardManager.setPrimaryClip(ClipData.newPlainText("empty", ""))
        } else {
            Toast.makeText(this, "복사된 텍스트가 없습니다.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val etUserInput = findViewById<EditText>(R.id.etUserInput)
        val btnSend = findViewById<Button>(R.id.btnSend)
        val btnOption1 = findViewById<Button>(R.id.btnOption1)
        val btnOption2 = findViewById<Button>(R.id.btnOption2)

        btnSend.setOnClickListener {
            val userInstruction = etUserInput.text.toString()
            btnSend.text = "AI가 작성 중..."
            btnSend.isEnabled = false

            // TODO: 저장된 API 키를 불러와 실제 API 연동 필요 (현재는 더미 응답)
            thread {
                Thread.sleep(1500)
                val answer1 = "[옵션1] 원본: $copiedText \n요청: $userInstruction 반영 완료."
                val answer2 = "[옵션2] 원본: $copiedText \n요청: $userInstruction 다른 버전."
                
                runOnUiThread {
                    btnOption1.visibility = View.VISIBLE
                    btnOption2.visibility = View.VISIBLE
                    btnOption1.text = answer1
                    btnOption2.text = answer2
                    btnSend.visibility = View.GONE
                }
            }
        }

        val onOptionSelected = View.OnClickListener { view ->
            clipboardManager.setPrimaryClip(ClipData.newPlainText("AI_Result", (view as Button).text.toString()))
            Toast.makeText(this, "결과가 클립보드에 복사되었습니다.", Toast.LENGTH_SHORT).show()
            finish()
        }

        btnOption1.setOnClickListener(onOptionSelected)
        btnOption2.setOnClickListener(onOptionSelected)
    }
}
