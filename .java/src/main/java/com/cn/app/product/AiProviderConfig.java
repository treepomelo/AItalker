package com.cn.app.product;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter @Setter @Component
@ConfigurationProperties(prefix = "super.ai")
public class AiProviderConfig {
    private String baseUrl = "";
    private String apiKey = "";
    private String model = "";
    private int timeoutSeconds = 60;

    public boolean configured() {
        return !baseUrl.isBlank() && !apiKey.isBlank() && !model.isBlank();
    }
}
