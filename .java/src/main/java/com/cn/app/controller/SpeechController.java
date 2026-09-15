package com.cn.app.controller;

import com.cn.app.msg.Result;
import com.cn.app.product.CatalogRepository;
import com.cn.app.product.SpeechService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/audio-tasks") @RequiredArgsConstructor
public class SpeechController {
    private final CatalogRepository catalog;
    private final SpeechService speech;
    @GetMapping("/{id}") public Result get(@PathVariable String id) { return Result.data(catalog.audio(id)); }
    @GetMapping("/{id}/file") public ResponseEntity<Resource> file(@PathVariable String id) {
        return ResponseEntity.ok().contentType(MediaType.valueOf("audio/mpeg"))
                .body(new FileSystemResource(speech.audioFile(id)));
    }
}
