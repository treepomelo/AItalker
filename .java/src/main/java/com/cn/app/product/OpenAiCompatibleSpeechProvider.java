package com.cn.app.product;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Compatibility entry point; protocol-specific serialization lives in ProviderGateway. */
@Service
public class OpenAiCompatibleSpeechProvider implements SpeechProvider {
    private final ModelSettingsStore settings;
    private final ProviderGateway gateway;
    @Autowired public OpenAiCompatibleSpeechProvider(ModelSettingsStore settings,ProviderGateway gateway){this.settings=settings;this.gateway=gateway;}
    public OpenAiCompatibleSpeechProvider(SpeechConfig config){this(new ModelSettingsStore(new AiProviderConfig(),config,""),new ProviderGateway());}
    @Override public byte[] synthesize(String text){return gateway.speech(settings.speech(),text);}
    @Override public byte[] synthesize(String text,ProviderSettings snapshot){return gateway.speech(snapshot,text);}
}
