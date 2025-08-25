package com.pocket_poker_pal_app.PocketPokerPalApp.controller;

import com.pocket_poker_pal_app.PocketPokerPalApp.service.WhisperService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AudioTranscribeController {

    private final WhisperService whisperService;

    @PostMapping("/transcribe-audio")
    public ResponseEntity<?> transcribe(@RequestParam("audio") MultipartFile audioFile) {
        if (audioFile.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No file uploaded."));
        }
        String ct = audioFile.getContentType();
        boolean ok = ct != null && (ct.startsWith("audio/") || ct.equals("video/webm"));
        if (!ok) {
            return ResponseEntity.badRequest().body(Map.of("error", "Please upload an audio file."));
        }
        try {
            String transcript = whisperService.transcribe(audioFile);
            return ResponseEntity.ok(Map.of("transcript", transcript));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", "Failed to transcribe: " + e.getMessage()));
        }
    }
}
