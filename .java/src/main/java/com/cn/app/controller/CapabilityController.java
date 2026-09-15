package com.cn.app.controller;

import com.cn.app.msg.Result;
import com.cn.app.product.AiProviderConfig;
import com.cn.app.product.SpeechConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController @RequestMapping("/capabilities") @RequiredArgsConstructor
public class CapabilityController {
    private final com.cn.app.product.ModelSettingsStore settings;
    @GetMapping public Result get() {
        return Result.data(Map.of("modelConfigured",settings.chat().configured(),"speechConfigured",settings.speech().configured(),
                "model",settings.chat().configured()?settings.chat().model():"","speechMaxCharacters",settings.speech().maxCharacters()));
    }
}
