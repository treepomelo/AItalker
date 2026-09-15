package com.cn.app.product;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter @Setter @Component
@ConfigurationProperties(prefix = "super.speech")
public class SpeechConfig {
    private String baseUrl = "";
    private String apiKey = "";
    private String model = "";
    private String voice = "alloy";
    private double speed = 1.0;
    private int timeoutSeconds = 90;
    private int maxCharacters = 4000;
    private String storageDirectory = "./audio-data";

    public boolean configured() {
        return !baseUrl.isBlank() && !apiKey.isBlank() && !model.isBlank() && !voice.isBlank();
    }
}
