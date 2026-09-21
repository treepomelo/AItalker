package com.cn.app.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** The configuration probes and production requests intentionally share every adapter. */
@Component
public class ProviderGateway {
    private static final String TENCENT_REGION="ap-guangzhou";
    private static final int TENCENT_CHUNK_LIMIT=100;
    private final ObjectMapper json=new ObjectMapper();

    public void require(ProviderSettings s,String channel){
        s.validate(channel);
        if(!s.configured())throw new ApiProblem(503,channel.equals("chat")?"MODEL_NOT_CONFIGURED":"SPEECH_NOT_CONFIGURED",
                channel.equals("chat")?"尚未配置大模型，请在模型配置页填写后重试":"语音服务尚未配置，文字讲解可正常查看");
    }
    private WebClient client(ProviderSettings s){
        WebClient.Builder b=WebClient.builder().codecs(c->c.defaultCodecs().maxInMemorySize(20*1024*1024));
        switch(s.protocol()){
            case "ANTHROPIC" -> b.defaultHeader("x-api-key",s.apiKey()).defaultHeader("anthropic-version","2023-06-01");
            case "GEMINI" -> b.defaultHeader("x-goog-api-key",s.apiKey());
            default -> b.defaultHeader("Authorization","Bearer "+s.apiKey());
        }
        return b.build();
    }
    private Map<String,Object> chatBody(ProviderSettings s,List<Map<String,String>> messages,boolean stream){
        Map<String,Object> body=new LinkedHashMap<>();
        String system=messages.stream().filter(m->m.get("role").equals("system")).map(m->m.get("content")).reduce("",(a,b)->a+"\n"+b).trim();
        List<Map<String,String>> turns=messages.stream().filter(m->!m.get("role").equals("system")).toList();
        switch(s.protocol()){
            case "OPENAI_CHAT" -> {body.put("model",s.model());body.put("messages",messages);body.put("stream",stream);if(!s.outputLimitField().equals("omit"))body.put(s.outputLimitField(),s.maxTokens());body.putAll(s.extras());}
            case "ANTHROPIC" -> {body.put("model",s.model());body.put("system",system);body.put("messages",turns);body.put("stream",stream);body.put("max_tokens",s.maxTokens());}
            case "GEMINI" -> {
                body.put("systemInstruction",Map.of("parts",List.of(Map.of("text",system))));
                body.put("contents",turns.stream().map(m->Map.of("role",m.get("role").equals("assistant")?"model":"user","parts",List.of(Map.of("text",m.get("content"))))).toList());
                body.put("generationConfig",Map.of("maxOutputTokens",s.maxTokens()));
            }
            default -> throw new ApiProblem(400,"UNSUPPORTED_PROTOCOL","不支持该聊天协议");
        }return body;
    }
    private WebClient.ResponseSpec request(ProviderSettings s,Object body,boolean stream){
        return client(s).post().uri(s.endpoint(stream)).contentType(MediaType.APPLICATION_JSON).bodyValue(body)
                .retrieve().onStatus(status->status.isError(),r->r.releaseBody().then(Mono.error(ProviderHttp.failure(r.statusCode().value(),s.isSpeech()?"语音":"大模型"))));
    }
    public String complete(ProviderSettings s,List<Map<String,String>> messages){
        require(s,"chat");
        try{
            JsonNode response=request(s,chatBody(s,messages,false),false).bodyToMono(JsonNode.class)
                    .timeout(Duration.ofSeconds(s.timeoutSeconds())).block();
            String result=extract(s,response,false).trim();
            if(result.isEmpty())throw new ApiProblem(502,"MODEL_EMPTY_RESPONSE","模型未返回有效文本，可能被拦截或接口协议不匹配");
            return result;
        }catch(ApiProblem e){throw e;}catch(Exception e){throw failure(e,"模型");}
    }
    public Flux<String> stream(ProviderSettings s,List<Map<String,String>> messages){
        require(s,"chat");
        return Flux.defer(()->client(s).post().uri(s.endpoint(true)).contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM).bodyValue(chatBody(s,messages,true)).exchangeToFlux(r->{
                    if(r.statusCode().isError())return r.releaseBody().thenMany(Flux.error(ProviderHttp.failure(r.statusCode().value(),"大模型")));
                    MediaType type=r.headers().contentType().orElse(MediaType.APPLICATION_OCTET_STREAM);
                    if(!type.isCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                        return r.releaseBody().thenMany(Flux.error(new ApiProblem(502,"MODEL_INVALID_STREAM","接口未返回 SSE 流式响应，请检查协议与服务地址")));
                    return r.bodyToFlux(String.class);
                })
                .timeout(Duration.ofSeconds(s.timeoutSeconds()))
                .takeUntil(chunk->terminal(s,chunk))
                .filter(chunk->!chunk.trim().equals("[DONE]"))
                .map(chunk->{try{return extract(s,json.readTree(chunk),true);}catch(ApiProblem e){throw e;}catch(Exception e){throw new ApiProblem(502,"MODEL_INVALID_STREAM","返回内容不是所选协议的流式事件");}})
                .filter(text->!text.isEmpty())
                .switchIfEmpty(Flux.error(new ApiProblem(502,"MODEL_EMPTY_STREAM","流式响应没有有效文本，请检查协议、模型和参数")))
                .onErrorMap(e->e instanceof ApiProblem?e:failure(e,"流式模型")));
    }
    private boolean terminal(ProviderSettings s,String chunk){
        if(chunk.trim().equals("[DONE]"))return true;
        try{return s.protocol().equals("ANTHROPIC")&&json.readTree(chunk).path("type").asText().equals("message_stop");}
        catch(Exception ignored){return false;}
    }
    private String extract(ProviderSettings s,JsonNode node,boolean stream){
        if(node==null)return "";
        if(node.has("error")||node.path("type").asText().equals("error"))throw new ApiProblem(502,"PROVIDER_ERROR","厂商返回了调用错误，请检查模型权限、额度及协议");
        return switch(s.protocol()){
            case "OPENAI_CHAT" -> textContent(node.path("choices").path(0).path(stream?"delta":"message").path("content"));
            case "ANTHROPIC" -> stream?node.path("delta").path("text").asText(""):textBlocks(node.path("content"));
            case "GEMINI" -> textBlocks(node.path("candidates").path(0).path("content").path("parts"));
            default -> "";
        };
    }
    private String textContent(JsonNode node){return node.isTextual()?node.asText():textBlocks(node);}
    private String textBlocks(JsonNode blocks){
        StringBuilder result=new StringBuilder();
        if(blocks.isArray())for(JsonNode part:blocks)if(!part.path("thought").asBoolean(false))result.append(part.path("text").asText(""));
        return result.toString();
    }
    public byte[] speech(ProviderSettings s,String text){
        require(s,"speech");
        if(text==null||text.isBlank()||text.length()>s.maxCharacters())throw new ApiProblem(400,"SPEECH_TEXT_INVALID","讲解为空或超过语音文本限制");
        try{
            byte[] bytes;
            if(s.protocol().equals("MINIMAX_SPEECH")){
                Map<String,Object> body=Map.of("model",s.model(),"text",text,"stream",false,"output_format","hex",
                        "voice_setting",Map.of("voice_id",s.voice(),"speed",s.speed(),"vol",1,"pitch",0),
                        "audio_setting",Map.of("sample_rate",32000,"bitrate",128000,"format","mp3","channel",1));
                JsonNode response=request(s,body,false).bodyToMono(JsonNode.class).timeout(Duration.ofSeconds(s.timeoutSeconds())).block();
                if(response==null||response.path("base_resp").path("status_code").asInt(-1)!=0)
                    throw new ApiProblem(502,"MINIMAX_PROVIDER_ERROR","MiniMax 返回失败，请检查模型、音色、额度及 Key");
                try{bytes=HexFormat.of().parseHex(response.path("data").path("audio").asText(""));}
                catch(Exception e){throw new ApiProblem(502,"SPEECH_INVALID_RESPONSE","MiniMax 未返回有效的 hex 音频");}
            }else if(s.protocol().equals("TENCENT_SPEECH")){
                bytes=tencentSpeech(s,text);
            }else{
                bytes=client(s).post().uri(s.endpoint(false)).contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Map.of("model",s.model(),"input",text,"voice",s.voice(),"speed",s.speed(),"response_format","mp3"))
                        .exchangeToMono(r->{
                            if(r.statusCode().isError())return r.releaseBody().then(Mono.error(ProviderHttp.failure(r.statusCode().value(),"语音")));
                            MediaType type=r.headers().contentType().orElse(MediaType.APPLICATION_OCTET_STREAM);
                            if(!(type.getType().equals("audio")||type.isCompatibleWith(MediaType.APPLICATION_OCTET_STREAM)))
                                return r.releaseBody().then(Mono.error(new ApiProblem(502,"SPEECH_INVALID_RESPONSE","语音接口返回了非音频内容，请检查协议")));
                            return r.bodyToMono(byte[].class);
                        }).timeout(Duration.ofSeconds(s.timeoutSeconds())).block();
            }
            if(bytes==null||bytes.length<3)throw new ApiProblem(502,"SPEECH_EMPTY_RESPONSE","语音接口没有返回有效音频");
            if(!((bytes[0]=='I'&&bytes[1]=='D'&&bytes[2]=='3')||((bytes[0]&255)==255&&(bytes[1]&224)==224)))
                throw new ApiProblem(502,"SPEECH_INVALID_RESPONSE","返回数据不是 MP3 音频，请检查协议与输出格式");
            return bytes;
        }catch(ApiProblem e){throw e;}catch(Exception e){throw failure(e,"语音");}
    }
    private byte[] tencentSpeech(ProviderSettings s,String text){
        String[] credential=s.apiKey().split(":",2);
        String endpoint=s.endpoint(false),host=URI.create(endpoint).getHost();
        WebClient client=WebClient.builder().codecs(c->c.defaultCodecs().maxInMemorySize(20*1024*1024)).build();
        ByteArrayOutputStream audio=new ByteArrayOutputStream();
        for(String chunk:splitSpeechChunks(text,TENCENT_CHUNK_LIMIT)){
            Map<String,Object> body=new LinkedHashMap<>();
            body.put("Text",chunk);body.put("SessionId",UUID.randomUUID().toString());
            body.put("Volume",0);body.put("Speed",tencentSpeed(s.speed()));body.put("ProjectId",0);
            body.put("ModelType",1);body.put("VoiceType",Integer.parseInt(s.model()));
            body.put("PrimaryLanguage",1);body.put("SampleRate",16000);body.put("Codec",s.voice());
            String payload;
            try{payload=json.writeValueAsString(body);}catch(Exception e){throw new IllegalStateException("Cannot serialize tencent tts request",e);}
            long timestamp=Instant.now().getEpochSecond();
            JsonNode response=client.post().uri(endpoint)
                    .header(HttpHeaders.CONTENT_TYPE,"application/json; charset=utf-8")
                    .header(HttpHeaders.AUTHORIZATION,tc3Authorization(credential[0],credential[1],"tts",host,payload,timestamp))
                    .header("X-TC-Action","TextToVoice").header("X-TC-Version","2019-08-23")
                    .header("X-TC-Timestamp",String.valueOf(timestamp)).header("X-TC-Region",TENCENT_REGION)
                    .bodyValue(payload.getBytes(StandardCharsets.UTF_8))
                    .retrieve().onStatus(status->status.isError(),r->r.releaseBody().then(Mono.error(ProviderHttp.failure(r.statusCode().value(),"语音"))))
                    .bodyToMono(JsonNode.class).timeout(Duration.ofSeconds(s.timeoutSeconds())).block();
            JsonNode result=response==null?null:response.path("Response");
            if(result==null||result.isMissingNode())throw new ApiProblem(502,"SPEECH_INVALID_RESPONSE","腾讯语音接口返回了无效响应");
            JsonNode error=result.path("Error");
            if(!error.isMissingNode()&&!error.isNull()){
                String code=error.path("Code").asText("");
                if(code.startsWith("AuthFailure")||code.startsWith("UnauthorizedOperation"))
                    throw new ApiProblem(502,"PROVIDER_AUTH_FAILED","语音服务认证失败，请检查服务端 API Key");
                if(code.startsWith("LimitExceeded"))throw new ApiProblem(503,"PROVIDER_RATE_LIMITED","语音服务额度不足或请求过于频繁，请稍后重试");
                throw new ApiProblem(502,"PROVIDER_ERROR","腾讯语音合成失败（"+code+"），请检查音色、额度与配置");
            }
            String encoded=result.path("Audio").asText("");
            if(encoded.isEmpty())throw new ApiProblem(502,"SPEECH_EMPTY_RESPONSE","腾讯语音接口没有返回有效音频");
            try{audio.writeBytes(Base64.getDecoder().decode(encoded));}
            catch(IllegalArgumentException e){throw new ApiProblem(502,"SPEECH_INVALID_RESPONSE","腾讯语音接口返回的音频无法解析");}
        }
        return audio.toByteArray();
    }
    static double tencentSpeed(double speed){return Math.round(Math.max(-2,Math.min(6,(speed-1)*5))*100)/100.0;}
    static List<String> splitSpeechChunks(String text,int limit){
        List<String> chunks=new ArrayList<>();
        StringBuilder current=new StringBuilder();
        for(String sentence:text.split("(?<=[。！？!?；;\n])")){
            String remaining=sentence;
            if(remaining.isBlank())continue;
            if(current.length()+remaining.length()>limit&&current.length()>0){chunks.add(current.toString());current.setLength(0);}
            while(remaining.length()>limit){chunks.add(remaining.substring(0,limit));remaining=remaining.substring(limit);}
            current.append(remaining);
        }
        if(current.length()>0)chunks.add(current.toString());
        return chunks;
    }
    static String tc3Authorization(String secretId,String secretKey,String service,String host,String payload,long timestamp){
        try{
            String date=DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC).format(Instant.ofEpochSecond(timestamp));
            String canonical="POST\n/\n\ncontent-type:application/json; charset=utf-8\nhost:"+host+"\n\ncontent-type;host\n"+sha256Hex(payload);
            String scope=date+"/"+service+"/tc3_request";
            String toSign="TC3-HMAC-SHA256\n"+timestamp+"\n"+scope+"\n"+sha256Hex(canonical);
            byte[] secretDate=hmacSha256(("TC3"+secretKey).getBytes(StandardCharsets.UTF_8),date);
            byte[] secretService=hmacSha256(secretDate,service);
            byte[] secretSigning=hmacSha256(secretService,"tc3_request");
            String signature=HexFormat.of().formatHex(hmacSha256(secretSigning,toSign));
            return "TC3-HMAC-SHA256 Credential="+secretId+"/"+scope+", SignedHeaders=content-type;host, Signature="+signature;
        }catch(Exception e){throw new IllegalStateException("Cannot sign tencent request",e);}
    }
    static byte[] hmacSha256(byte[] key,String data)throws Exception{
        Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }
    static String sha256Hex(String data)throws Exception{
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data.getBytes(StandardCharsets.UTF_8)));
    }
    private ApiProblem failure(Throwable e,String label){
        return new ApiProblem(e instanceof java.util.concurrent.TimeoutException?504:502,"PROVIDER_UNAVAILABLE",label+"调用失败或超时，请检查网络与厂商配置");
    }
}
