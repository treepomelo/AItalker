package com.cn.app.product;

import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.*;
import org.junit.jupiter.api.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class NativeProtocolTest {
    HttpServer server;
    ProviderGateway gateway=new ProviderGateway();
    ObjectMapper json=new ObjectMapper();
    AtomicReference<JsonNode> request=new AtomicReference<>();
    AtomicReference<Headers> headers=new AtomicReference<>();
    List<Map<String,String>> messages=List.of(Map.of("role","system","content","system prompt"),Map.of("role","user","content","hello"));
    @BeforeEach void start() throws Exception{server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.start();}
    @AfterEach void stop(){server.stop(0);}
    ProviderSettings config(String protocol,String root){return new ProviderSettings(protocol,"http://127.0.0.1:"+server.getAddress().getPort()+root,"private-key","model-one","voice-one",1,5,128,4000,"max_tokens","{}");}
    void reply(HttpExchange x,String type,String body) throws java.io.IOException{
        headers.set(x.getRequestHeaders());request.set(json.readTree(x.getRequestBody()));byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
        x.getResponseHeaders().set("Content-Type",type);x.sendResponseHeaders(200,bytes.length);x.getResponseBody().write(bytes);x.close();
    }
    @Test void anthropicUsesNativeBodyHeadersAndStreamingEvents(){
        server.createContext("/v1/messages",x->{
            JsonNode body=json.readTree(x.getRequestBody());request.set(body);headers.set(x.getRequestHeaders());
            String payload=body.path("stream").asBoolean()?"event: message_start\ndata: {\"type\":\"message_start\"}\n\nevent: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"Claude hello\"}}\n\nevent: message_stop\ndata: {\"type\":\"message_stop\"}\n\n":"{\"content\":[{\"type\":\"text\",\"text\":\"Claude hello\"}]}";
            byte[] bytes=payload.getBytes(StandardCharsets.UTF_8);x.getResponseHeaders().set("Content-Type",body.path("stream").asBoolean()?"text/event-stream":"application/json");x.sendResponseHeaders(200,bytes.length);x.getResponseBody().write(bytes);x.close();
        });
        ProviderSettings s=config("ANTHROPIC","/v1/messages");
        assertEquals("Claude hello",gateway.complete(s,messages));
        assertEquals("system prompt",request.get().path("system").asText());assertEquals("user",request.get().path("messages").path(0).path("role").asText());
        assertEquals("private-key",headers.get().getFirst("x-api-key"));assertEquals("2023-06-01",headers.get().getFirst("anthropic-version"));assertNull(headers.get().getFirst("Authorization"));
        assertEquals(List.of("Claude hello"),gateway.stream(s,messages).collectList().block());
    }
    @Test void geminiConvertsRolesSystemAndStreamUrl(){
        server.createContext("/v1beta/models/model-one:generateContent",x->reply(x,"application/json","{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Gemini hello\"}]}}]}"));
        server.createContext("/v1beta/models/model-one:streamGenerateContent",x->{assertEquals("alt=sse",x.getRequestURI().getQuery());reply(x,"text/event-stream","data: {\"candidates\":[{\"content\":{\"parts\":[{\"thought\":true,\"text\":\"private reasoning\"},{\"text\":\"Gemini hello\"}]}}]}\n\n");});
        ProviderSettings s=config("GEMINI","/v1beta");
        assertEquals("Gemini hello",gateway.complete(s,messages));assertEquals("private-key",headers.get().getFirst("x-goog-api-key"));assertNull(headers.get().getFirst("Authorization"));
        assertEquals("system prompt",request.get().path("systemInstruction").path("parts").path(0).path("text").asText());
        assertEquals(List.of("Gemini hello"),gateway.stream(s,messages).collectList().block());
    }
    @Test void minimaxDecodesHexAndUsesNativeVoiceSetting(){
        server.createContext("/v1/t2a_v2",x->reply(x,"application/json","{\"base_resp\":{\"status_code\":0},\"data\":{\"audio\":\"494433616263\"}}"));
        assertArrayEquals("ID3abc".getBytes(StandardCharsets.UTF_8),gateway.speech(config("MINIMAX_SPEECH","/v1"),"测试"));
        assertEquals("voice-one",request.get().path("voice_setting").path("voice_id").asText());
        assertEquals("mp3",request.get().path("audio_setting").path("format").asText());assertEquals("hex",request.get().path("output_format").asText());
    }
    @Test void minimaxBusinessFailureDoesNotAppearAsSuccess(){
        server.createContext("/v1/t2a_v2",x->reply(x,"application/json","{\"base_resp\":{\"status_code\":1004,\"status_msg\":\"private-key\"}}"));
        ApiProblem e=assertThrows(ApiProblem.class,()->gateway.speech(config("MINIMAX_SPEECH","/v1"),"测试"));
        assertEquals("MINIMAX_PROVIDER_ERROR",e.errorCode);assertFalse(e.getMessage().contains("private-key"));
    }
    @Test void endpointAndProtocolMismatchAreRejectedBeforeSending(){
        assertThrows(ApiProblem.class,()->config("OPENAI_SPEECH","/v1/chat/completions").validate("speech"));
        assertThrows(ApiProblem.class,()->config("OPENAI_CHAT","/v1beta/models/model-one:generateContent").validate("chat"));
        assertThrows(ApiProblem.class,()->config("GEMINI","/v1beta/models/other:generateContent").validate("chat"));
        assertEquals(config("OPENAI_CHAT","/v1").endpoint(false),config("OPENAI_CHAT","/v1/chat/completions").endpoint(false));
    }
    @Test void jsonReplyCannotMasqueradeAsGeminiStream(){
        server.createContext("/v1beta/models/model-one:streamGenerateContent",x->reply(x,"application/json","{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"not streaming\"}]}}]}"));
        var error=assertThrows(ApiProblem.class,()->gateway.stream(config("GEMINI","/v1beta"),messages).collectList().block());
        assertEquals("MODEL_INVALID_STREAM",error.errorCode);
    }
    @Test void openaiCompletionLengthFieldMatchesConfiguration(){
        server.createContext("/v1/chat/completions",x->reply(x,"application/json","{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}"));
        ProviderSettings old=config("OPENAI_CHAT","/v1");
        ProviderSettings s=new ProviderSettings(old.protocol(),old.baseUrl(),old.apiKey(),old.model(),old.voice(),1,5,256,4000,"max_completion_tokens","{\"enable_thinking\":false}");
        gateway.complete(s,messages);assertEquals(256,request.get().path("max_completion_tokens").asInt());assertFalse(request.get().has("max_tokens"));assertFalse(request.get().path("enable_thinking").asBoolean());
    }
    @Test void configurationProbeUsesSameNativeCallsAsRuntime(){
        server.createContext("/v1/messages",x->{
            JsonNode body=json.readTree(x.getRequestBody());boolean streaming=body.path("stream").asBoolean();
            String payload=streaming?"data: {\"type\":\"content_block_delta\",\"delta\":{\"text\":\"ok\"}}\n\ndata: {\"type\":\"message_stop\"}\n\n":"{\"content\":[{\"text\":\"ok\"}]}";
            byte[] bytes=payload.getBytes(StandardCharsets.UTF_8);x.getResponseHeaders().set("Content-Type",streaming?"text/event-stream":"application/json");x.sendResponseHeaders(200,bytes.length);x.getResponseBody().write(bytes);x.close();
        });
        ModelSettingsStore store=new ModelSettingsStore(new AiProviderConfig(),new SpeechConfig(),"");
        var edit=new ModelSettingsStore.Edit(0,config("ANTHROPIC","/v1"),false);
        Map<String,Object> result=new ModelConfigurationService(store,gateway).test("chat",edit);
        assertEquals("PASSED",result.get("status"));assertFalse(store.chat().configured());
        store.save("chat",edit);assertEquals("ok",gateway.complete(store.chat(),messages));
        assertEquals(result.get("fingerprint"),store.chat().fingerprint());
    }
}
