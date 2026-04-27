package com.example.gemma4ondevicetest

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private enum class ScreenTab {
        CHAT, MODEL, STATUS
    }

    private lateinit var toolbar: MaterialToolbar
    private lateinit var statusSummaryText: TextView
    private lateinit var statusText: TextView
    private lateinit var modelInfoText: TextView
    private lateinit var selectedModelText: TextView
    private lateinit var progressLabelText: TextView
    private lateinit var inputEdit: EditText
    private lateinit var progress: LinearProgressIndicator
    private lateinit var chatRecycler: RecyclerView
    private lateinit var chatEmptyState: LinearLayout
    private lateinit var chatScreen: LinearLayout
    private lateinit var modelScreen: ScrollView
    private lateinit var statusScreen: LinearLayout
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var pickModelButton: MaterialButton
    private lateinit var downloadGemmaButton: MaterialButton
    private lateinit var loadModelButton: MaterialButton
    private lateinit var sendButton: MaterialButton

    private val messages = mutableListOf<ChatMessage>()
    private lateinit var adapter: ChatAdapter
    private var selectedSource: ModelStore.ModelSource = ModelStore.CUSTOM
    private var currentTab: ScreenTab = ScreenTab.CHAT

    private val pickModelLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            importModel(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toolbar = findViewById(R.id.toolbar)
        statusText = findViewById(R.id.text_status)
        statusSummaryText = findViewById(R.id.text_status_summary)
        modelInfoText = findViewById(R.id.text_model_info)
        selectedModelText = findViewById(R.id.text_selected_model)
        progressLabelText = findViewById(R.id.text_progress_label)
        inputEdit = findViewById(R.id.edit_prompt)
        progress = findViewById(R.id.progress)
        chatRecycler = findViewById(R.id.recycler_chat)
        chatEmptyState = findViewById(R.id.chat_empty_state)
        chatScreen = findViewById(R.id.screen_chat)
        modelScreen = findViewById(R.id.screen_model)
        statusScreen = findViewById(R.id.screen_status)
        bottomNavigation = findViewById(R.id.bottom_navigation)
        pickModelButton = findViewById(R.id.button_pick_model)
        downloadGemmaButton = findViewById(R.id.button_download_gemma)
        loadModelButton = findViewById(R.id.button_load_model)
        sendButton = findViewById(R.id.button_send)
        selectedSource = ModelStore.getSelectedModel(this)

        adapter = ChatAdapter(messages)
        chatRecycler.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        chatRecycler.adapter = adapter
        toolbar.setNavigationOnClickListener {
            handleBackNavigation()
        }

        pickModelButton.setOnClickListener {
            selectedSource = ModelStore.CUSTOM
            ModelStore.setSelectedModel(this, selectedSource)
            pickModelLauncher.launch(arrayOf("*/*"))
        }
        downloadGemmaButton.setOnClickListener {
            downloadGemmaModel()
        }
        loadModelButton.setOnClickListener {
            toggleModel()
        }
        sendButton.setOnClickListener {
            sendPrompt()
        }
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_model -> switchTab(ScreenTab.MODEL)
                R.id.nav_status -> switchTab(ScreenTab.STATUS)
                else -> switchTab(ScreenTab.CHAT)
            }
            true
        }
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (!handleBackNavigation()) {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        )

        addSystemMessage("직접 `.litertlm` 파일을 선택하거나 Gemma 4 E2B (2bit) 모델을 다운로드한 뒤 로드하면 오프라인 채팅을 시작할 수 있습니다.")
        bottomNavigation.selectedItemId = R.id.nav_chat
        refreshUi()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            LlmEngine.free()
        }
    }

    private fun importModel(uri: Uri) {
        setBusy(true, "모델 파일 복사 중...")
        lifecycleScope.launch {
            val result = ModelStore.importModel(this@MainActivity, uri)
            setBusy(false)
            result.onSuccess {
                LlmEngine.free()
                selectedSource = ModelStore.CUSTOM
                addSystemMessage("모델 파일을 저장했습니다: ${it.name}")
                refreshUi()
            }.onFailure {
                toast(it.message ?: "모델 파일 가져오기에 실패했습니다.")
                refreshUi()
            }
        }
    }

    private fun downloadGemmaModel() {
        selectedSource = ModelStore.GEMMA_4
        ModelStore.setSelectedModel(this, selectedSource)
        setBusy(true, "Gemma 4 E2B (2bit) 다운로드 중...", determinate = true, progressValue = 0)
        lifecycleScope.launch {
            val result = ModelStore.downloadModel(this@MainActivity, ModelStore.GEMMA_4) { value ->
                runOnUiThread {
                    progress.progress = value
                    progressLabelText.text = getString(R.string.download_progress, value)
                }
            }
            setBusy(false)
            result.onSuccess {
                LlmEngine.free()
                addSystemMessage("Gemma 4 E2B (2bit) 다운로드가 완료되었습니다.")
            }.onFailure {
                toast(it.message ?: "모델 다운로드에 실패했습니다.")
            }
            refreshUi()
        }
    }

    private fun toggleModel() {
        if (LlmEngine.isLoaded) {
            LlmEngine.free()
            addSystemMessage("모델을 언로드했습니다.")
            refreshUi()
            return
        }

        setBusy(true, "모델 로드 중...")
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                LlmEngine.loadModel(this@MainActivity, selectedSource)
            }
            setBusy(false)
            if (loaded) {
                addSystemMessage("${selectedSource.label} 로드가 완료되었습니다.")
            } else {
                toast(LlmEngine.getLastError() ?: "모델 로드에 실패했습니다.")
            }
            refreshUi()
        }
    }

    private fun sendPrompt() {
        val prompt = inputEdit.text.toString().trim()
        if (prompt.isBlank()) return
        if (!LlmEngine.isLoaded) {
            toast("먼저 모델을 로드하세요.")
            return
        }

        inputEdit.setText("")
        appendMessage(ChatMessage(prompt, fromUser = true))
        setBusy(true, "응답 생성 중...")
        lifecycleScope.launch {
            val reply = withContext(Dispatchers.IO) {
                LlmEngine.generate(prompt)
            }
            setBusy(false)
            if (reply.isBlank()) {
                toast(LlmEngine.getLastError() ?: "응답 생성에 실패했습니다.")
            } else {
                appendMessage(ChatMessage(reply, fromUser = false))
            }
            refreshUi()
        }
    }

    private fun refreshUi() {
        selectedModelText.text = getString(R.string.selected_model, selectedSource.label)
        modelInfoText.text = ModelStore.describe(this, selectedSource)
        statusSummaryText.text = buildStatusSummary()
        if (LlmEngine.isLoaded) {
            statusText.text = "상태: 모델 로드됨"
            loadModelButton.text = getString(R.string.unload_model)
            sendButton.isEnabled = true
        } else {
            statusText.text = "상태: 모델 미로드"
            loadModelButton.text = getString(R.string.load_model)
            sendButton.isEnabled = false
        }
        progressLabelText.visibility = View.GONE
        loadModelButton.isEnabled = ModelStore.hasModel(this, selectedSource)
        chatEmptyState.visibility = if (messages.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun setBusy(
        busy: Boolean,
        status: String? = null,
        determinate: Boolean = false,
        progressValue: Int = 0
    ) {
        progress.isIndeterminate = busy && !determinate
        progress.progress = progressValue
        progress.visibility = if (busy) View.VISIBLE else View.GONE
        progressLabelText.visibility = if (busy && determinate) View.VISIBLE else View.GONE
        if (!busy) {
            progressLabelText.text = ""
        }
        pickModelButton.isEnabled = !busy
        downloadGemmaButton.isEnabled = !busy
        loadModelButton.isEnabled = !busy && ModelStore.hasModel(this, selectedSource)
        sendButton.isEnabled = !busy && LlmEngine.isLoaded
        inputEdit.isEnabled = !busy
        statusText.text = status ?: if (LlmEngine.isLoaded) "상태: 모델 로드됨" else "상태: 모델 미로드"
    }

    private fun appendMessage(message: ChatMessage) {
        adapter.addMessage(message)
        chatRecycler.scrollToPosition(adapter.itemCount - 1)
        chatEmptyState.visibility = View.GONE
    }

    private fun addSystemMessage(message: String) {
        appendMessage(ChatMessage(message, fromUser = false))
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun switchTab(tab: ScreenTab) {
        currentTab = tab
        chatScreen.visibility = if (tab == ScreenTab.CHAT) View.VISIBLE else View.GONE
        modelScreen.visibility = if (tab == ScreenTab.MODEL) View.VISIBLE else View.GONE
        statusScreen.visibility = if (tab == ScreenTab.STATUS) View.VISIBLE else View.GONE
        toolbar.subtitle = when (tab) {
            ScreenTab.CHAT -> getString(R.string.toolbar_subtitle)
            ScreenTab.MODEL -> getString(R.string.model_section_title)
            ScreenTab.STATUS -> getString(R.string.status_section_title)
        }
    }

    private fun handleBackNavigation(): Boolean {
        if (currentTab != ScreenTab.CHAT) {
            bottomNavigation.selectedItemId = R.id.nav_chat
            switchTab(ScreenTab.CHAT)
            return true
        }
        if (inputEdit.hasFocus()) {
            inputEdit.clearFocus()
            return true
        }
        return false
    }

    private fun buildStatusSummary(): String {
        val modelReady = if (ModelStore.hasModel(this, selectedSource)) "준비됨" else "없음"
        val engineState = if (LlmEngine.isLoaded) "로드됨" else "미로드"
        return "선택 모델: ${selectedSource.label}\n저장 상태: $modelReady\n엔진 상태: $engineState"
    }
}
