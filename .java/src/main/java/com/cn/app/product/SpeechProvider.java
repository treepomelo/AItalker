package com.cn.app.product;

public interface SpeechProvider {
    byte[] synthesize(String text);
    default byte[] synthesize(String text,ProviderSettings snapshot){return synthesize(text);}
}
