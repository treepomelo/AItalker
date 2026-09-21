package com.cn.app.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Immutable: a request and a queued speech job always use one complete configuration. */
public record ProviderSettings(String protocol, String baseUrl, String apiKey, String model,
        String voice, double speed, int timeoutSeconds, int maxTokens, int maxCharacters, String outputLimitField, String extraBody) {
    public ProviderSettings {
        protocol=clean(protocol);baseUrl=clean(baseUrl).replaceAll("/+$","");apiKey=clean(apiKey);
        model=clean(model);voice=clean(voice);extraBody=clean(extraBody).isEmpty()?"{}":extraBody;
        outputLimitField=clean(outputLimitField).isEmpty()?"max_tokens":outputLimitField;
    }
    private static String clean(String value){return value==null?"":value.trim();}
    public boolean configured(){return !baseUrl.isEmpty()&&!apiKey.isEmpty()&&!model.isEmpty()
            && (!isSpeech()||!voice.isEmpty());}
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isSpeech(){return protocol.equals("OPENAI_SPEECH")||protocol.equals("MINIMAX_SPEECH")||protocol.equals("TENCENT_SPEECH");}
    public ProviderSettings withKey(String key){return new ProviderSettings(protocol,baseUrl,key,model,voice,speed,timeoutSeconds,maxTokens,maxCharacters,outputLimitField,extraBody);}
    public ProviderSettings withMaxTokens(int value){return new ProviderSettings(protocol,baseUrl,apiKey,model,voice,speed,timeoutSeconds,value,maxCharacters,outputLimitField,extraBody);}
    public static ProviderSettings ai(AiProviderConfig c){return new ProviderSettings("OPENAI_CHAT",c.getBaseUrl(),c.getApiKey(),c.getModel(),"",1,c.getTimeoutSeconds(),1024,4000,"max_tokens","{}");}
    public static ProviderSettings speech(SpeechConfig c){return new ProviderSettings("OPENAI_SPEECH",c.getBaseUrl(),c.getApiKey(),c.getModel(),c.getVoice(),c.getSpeed(),c.getTimeoutSeconds(),1024,c.getMaxCharacters(),"max_tokens","{}");}
    public void validate(String channel){
        Set<String> protocols=channel.equals("chat")?Set.of("OPENAI_CHAT","ANTHROPIC","GEMINI"):Set.of("OPENAI_SPEECH","MINIMAX_SPEECH","TENCENT_SPEECH");
        if(!protocols.contains(protocol))bad("所选协议不适用于此功能");
        if(!Set.of("max_tokens","max_completion_tokens","omit").contains(outputLimitField))bad("请选择正确的输出长度字段");
        if(timeoutSeconds<5||timeoutSeconds>120||maxTokens<16||maxTokens>16384||maxCharacters<1||maxCharacters>4000)
            bad("超时应为 5–120 秒，输出上限 16–16384，语音文本上限 1–4000 字");
        if(!Double.isFinite(speed)||speed<0.25||speed>4||(protocol.equals("MINIMAX_SPEECH")&&(speed<0.5||speed>2)))bad("语速超出所选协议支持范围");
        if(model.length()>200||voice.length()>200||apiKey.length()>4096||apiKey.contains("\n")||apiKey.contains("\r"))bad("模型、音色或密钥格式无效");
        if(!baseUrl.isEmpty()){
            try{
                URI u=URI.create(baseUrl);
                if(!Set.of("https","http").contains(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null||u.getQuery()!=null||u.getFragment()!=null)bad("请填写无密钥、无查询参数的 HTTP(S) 服务地址");
                if(u.getPath().contains(".."))bad("服务地址不能包含相对路径");
                for(String suffix:List.of("/chat/completions","/messages","/audio/speech","/t2a_v2")){
                    String expected=switch(protocol){case "OPENAI_CHAT"->"/chat/completions";case "ANTHROPIC"->"/messages";case "OPENAI_SPEECH"->"/audio/speech";case "MINIMAX_SPEECH"->"/t2a_v2";default->"";};
                    if(u.getPath().endsWith(suffix)&&!suffix.equals(expected))bad("完整接口路径与所选协议不一致，请改用正确的根地址");
                }
                if(u.getPath().matches(".*/models/[^/]+:(generateContent|streamGenerateContent)")){
                    if(!protocol.equals("GEMINI"))bad("Gemini 完整接口地址需选择 Gemini 协议");
                    String embedded=u.getPath().replaceFirst(".*/models/","").replaceFirst(":(generateContent|streamGenerateContent)$","");
                    if(!embedded.equals(model))bad("Gemini 地址中的模型与模型 ID 不一致，建议只填写 /v1beta 根地址");
                }
            }catch(IllegalArgumentException e){bad("服务地址格式无效");}
        }
        if(protocol.equals("GEMINI")&&!model.isEmpty()&&!model.matches("[A-Za-z0-9_.:-]+"))bad("Gemini 模型 ID 不包含 models/ 前缀或 URL");
        if(protocol.equals("TENCENT_SPEECH")){
            if(!apiKey.isEmpty()&&!apiKey.matches("[A-Za-z0-9]+:[^:\\s]+"))bad("腾讯云密钥请使用 SecretId:SecretKey 格式");
            if(!voice.isEmpty()&&!voice.equals("mp3"))bad("腾讯云语音当前仅输出 mp3，请将编码留作 mp3");
            if(!model.isEmpty()&&!model.matches("\\d{1,9}"))bad("腾讯云音色 ID 应为数字，如 601000");
        }
        extras();
    }
    public Map<String,Object> extras(){
        try{
            var node=new ObjectMapper().readTree(extraBody);
            if(!node.isObject()||extraBody.length()>4000)bad("扩展参数必须是 JSON 对象，且不超过 4000 字符");
            Set<String> allowed=Set.of("temperature","top_p","top_k","presence_penalty","frequency_penalty","enable_thinking","reasoning_effort");
            Map<String,Object> result=new TreeMap<>();
            node.fields().forEachRemaining(e->{
                if(!allowed.contains(e.getKey()))bad("不支持扩展参数："+e.getKey()+"；模型、消息、流式和音频格式由系统统一控制");
                if(e.getKey().equals("enable_thinking")&&!e.getValue().isBoolean())bad("enable_thinking 必须为布尔值");
                if(e.getKey().equals("reasoning_effort")&&!e.getValue().isTextual())bad("reasoning_effort 必须为字符串");
                if(!Set.of("enable_thinking","reasoning_effort").contains(e.getKey())&&!e.getValue().isNumber())bad("采样参数必须为数字");
                if(!protocol.equals("OPENAI_CHAT"))bad("当前仅 OpenAI 兼容聊天支持扩展参数");
                result.put(e.getKey(),new ObjectMapper().convertValue(e.getValue(),Object.class));
            });return result;
        }catch(ApiProblem e){throw e;}catch(Exception e){bad("扩展参数不是有效 JSON 对象");return Map.of();}
    }
    public String fingerprint(){
        try{
            String canonical=new ObjectMapper().writeValueAsString(List.of(protocol,baseUrl,apiKey,model,voice,speed,timeoutSeconds,maxTokens,maxCharacters,outputLimitField,extras()));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        }catch(Exception e){throw new IllegalStateException("Cannot fingerprint settings");}
    }
    public String endpoint(boolean stream){
        if(baseUrl.isEmpty())return "";
        String route=switch(protocol){case "OPENAI_CHAT"->"/chat/completions";case "ANTHROPIC"->"/messages";case "OPENAI_SPEECH"->"/audio/speech";case "MINIMAX_SPEECH"->"/t2a_v2";case "GEMINI"->"";case "TENCENT_SPEECH"->"";default->throw new ApiProblem(400,"UNSUPPORTED_PROTOCOL","不支持该协议");};
        if(protocol.equals("GEMINI")){
            String root=baseUrl.replaceFirst("/models/[^/]+:(generateContent|streamGenerateContent)$","");
            return root+"/models/"+model+":"+(stream?"streamGenerateContent?alt=sse":"generateContent");
        }
        return baseUrl.endsWith(route)?baseUrl:baseUrl+route;
    }
    private static void bad(String message){throw new ApiProblem(400,"INVALID_MODEL_CONFIG",message);}
    @Override public String toString(){return "ProviderSettings[protocol="+protocol+", credentials=REDACTED]";}
}
