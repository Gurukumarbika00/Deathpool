package com.deadpool.ai

import android.content.Context
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale

data class Message(val text: String, val isUser: Boolean, val imageUrl: String? = null)

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private lateinit var tts: TextToSpeech
    private var isTorchOn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)

        setContent {
            MaterialTheme {
                MainScreen(
                    onToggleTorch = { toggleTorch() },
                    onSpeak = { text -> speakDeadpool(text) }
                )
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale("hi", "IN")
            tts.setPitch(0.9f) // Deadpool husky witty tone
            tts.setSpeechRate(1.1f) // Tez delivery
        }
    }

    private fun speakDeadpool(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun toggleTorch() {
        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = cameraManager.cameraIdList[0]
        isTorchOn = !isTorchOn
        cameraManager.setTorchMode(cameraId, isTorchOn)
    }

    override fun onDestroy() {
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }
}

@Composable
fun MainScreen(onToggleTorch: () -> Unit, onSpeak: (String) -> Unit) {
    var apiKey by remember { mutableStateOf("") }
    var inputText by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<Message>() }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(12.dp)
    ) {
        Text("🔴 DEADPOOL AI BRAIN", color = Color.Red, fontSize = 22.sp)

        // API Key Input / Vault
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("Gemini API Key Yahan Dalein") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Quick Hardware & Tools Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = onToggleTorch,
                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
            ) {
                Text("🔦 Torch")
            }
            Button(
                onClick = { messages.clear() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C))
            ) {
                Text("🧹 Clear Memory")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Chat View
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            reverseLayout = false
        ) {
            items(messages) { msg ->
                val align = if (msg.isUser) Alignment.End else Alignment.Start
                val bg = if (msg.isUser) Color(0xFFD32F2F) else Color(0xFF212121)

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = align
                ) {
                    Box(
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .background(bg, RoundedCornerShape(12.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Text(msg.text, color = Color.White)
                            if (msg.imageUrl != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                AsyncImage(
                                    model = msg.imageUrl,
                                    contentDescription = "AI Image",
                                    modifier = Modifier
                                        .size(220.dp)
                                        .padding(4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Prompt Input
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("Kuch bhi pucho ya likho '/image sher'...") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = {
                    val prompt = inputText.trim()
                    if (prompt.isNotEmpty()) {
                        messages.add(Message(prompt, isUser = true))
                        inputText = ""

                        coroutineScope.launch {
                            // Check for image generation command
                            if (prompt.startsWith("/image")) {
                                val query = prompt.removePrefix("/image").trim()
                                val encoded = URLEncoder.encode(query, "UTF-8")
                                val imgUrl = "https://image.pollinations.ai/prompt/$encoded?width=800&height=800&nologo=true"
                                val reply = "Le bhai, teri photo ban gayi ekdum mast!"
                                messages.add(Message(reply, isUser = false, imageUrl = imgUrl))
                                onSpeak(reply)
                            } else {
                                val reply = fetchGeminiDeadpool(prompt, apiKey)
                                messages.add(Message(reply, isUser = false))
                                onSpeak(reply)
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) {
                Text("Bhej")
            }
        }
    }
}

suspend fun fetchGeminiDeadpool(userPrompt: String, apiKey: String): String = withContext(Dispatchers.IO) {
    if (apiKey.isEmpty()) {
        return@withContext "Oye hero! Upar pehle Gemini API key toh daal le, tabhi toh dimag chalega!"
    }

    val systemPrompt = "Tumhara naam Deadpool hai. Har jawab Hindi me Bollywood dubbing style me doge: super witty, thoda dramatic, bindass comedy punchlines ke sath. Formal kitabi baatein bilkul mat karna."
    val fullPrompt = "$systemPrompt\n\nUser: $userPrompt"

    val client = OkHttpClient()
    val json = JSONObject().apply {
        put("contents", JSONArray().apply {
            put(JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().put("text", fullPrompt))
                })
            })
        })
    }

    val body = json.toString().toRequestBody("application/json".toMediaType())
    val request = Request.Builder()
        .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey")
        .post(body)
        .build()

    try {
        val response = client.newCall(request).execute()
        val resBody = response.body?.string() ?: ""
        val jsonRes = JSONObject(resBody)
        val text = jsonRes.getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
        text
    } catch (e: Exception) {
        "Arrey yaar, network ka locha ho gaya ya key invalid hai! Error: ${e.localizedMessage}"
    }
}
