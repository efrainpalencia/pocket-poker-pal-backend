package com.pocket_poker_pal_app.PocketPokerPalApp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class WhisperService {

    @Value("${openai.api.key}")
    private String openaiApiKey;

    // Slightly longer timeouts for mobile uploads / slower networks
    private final OkHttpClient client = new OkHttpClient.Builder()
            .callTimeout(Duration.ofSeconds(120))
            .connectTimeout(Duration.ofSeconds(20))
            .readTimeout(Duration.ofSeconds(120))
            .writeTimeout(Duration.ofSeconds(120))
            .build();

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Transcribe an audio blob via OpenAI Whisper.
     * Accepts audio/webm, audio/ogg, audio/wav, audio/m4a and even video/webm (some browsers label mic blobs that way).
     */
    public @NotNull String transcribe(MultipartFile audioFile) throws IOException {
        if (audioFile.isEmpty()) throw new IOException("Empty file");

        String contentType = audioFile.getContentType();
        String fileName = audioFile.getOriginalFilename();

        // Normalize missing/odd metadata
        if (contentType == null || contentType.isBlank()) {
            contentType = "audio/webm";                 // safe default from browser recording
        }
        if (fileName == null || fileName.isBlank()) {
            // give Whisper a sensible extension for better format detection
            String ext =
                    contentType.contains("webm") ? "webm" :
                            contentType.contains("ogg")  ? "ogg"  :
                                    contentType.contains("wav")  ? "wav"  :
                                            contentType.contains("mp4")  ? "m4a"  : "bin";
            fileName = "recording." + ext;
        }

        // Whisper accepts video/webm too; keep the original type
        MediaType mediaType = MediaType.parse(contentType);
        RequestBody fileBody = RequestBody.create(audioFile.getBytes(), mediaType);

        MultipartBody requestBody = new MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("file", fileName, fileBody)
                .addFormDataPart("model", "whisper-1")
                // Optional knobs:
                // .addFormDataPart("temperature", "0")
                // .addFormDataPart("language", "en")
                // .addFormDataPart("prompt", "Poker tournament rules context...")
                .build();

        Request request = new Request.Builder()
                .url("https://api.openai.com/v1/audio/transcriptions")
                .post(requestBody)
                .addHeader("Authorization", "Bearer " + openaiApiKey)
                .build();

        try (Response response = client.newCall(request).execute()) {
            ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null) {
                String err = body != null ? body.string() : "No body";
                throw new IOException("Whisper API failed: " + response.code() + " — " + err);
            }
            JsonNode root = mapper.readTree(body.string());
            JsonNode text = root.get("text");
            if (text == null || text.asText().isBlank()) {
                throw new IOException("Whisper returned empty transcript");
            }
            return text.asText();
        }
    }
}
