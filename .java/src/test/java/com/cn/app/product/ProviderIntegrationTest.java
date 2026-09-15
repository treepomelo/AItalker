package com.cn.app.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class ProviderIntegrationTest {
    HttpServer server;
    SpeechConfig speech;
    AtomicReference<String> body=new AtomicReference<>();
    AtomicReference<String> authorization=new AtomicReference<>();
    volatile int status=200;
    volatile String contentType="audio/mpeg";
    volatile byte[] response="ID3-test-audio".getBytes(StandardCharsets.UTF_8);

    @BeforeEach void start() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/v1/audio/speech",exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.getResponseHeaders().set("Content-Type",contentType);
            exchange.sendResponseHeaders(status,response.length);
            exchange.getResponseBody().write(response);exchange.close();
        });
        server.start();speech=new SpeechConfig();
        speech.setBaseUrl("http://127.0.0.1:"+server.getAddress().getPort()+"/v1");
        speech.setApiKey("unit-test-secret");speech.setModel("configured-tts-model");speech.setVoice("configured-voice");
    }
    @AfterEach void stop(){server.stop(0);}

    @Test void sendsConfiguredSpeechProtocolAndReturnsAudio() throws Exception {
        byte[] bytes=new OpenAiCompatibleSpeechProvider(speech).synthesize("文创商品测试讲解");
        assertArrayEquals(response,bytes);
        JsonNode request=new ObjectMapper().readTree(body.get());
        assertEquals("configured-tts-model",request.path("model").asText());
        assertEquals("configured-voice",request.path("voice").asText());
        assertEquals("mp3",request.path("response_format").asText());
        assertEquals("文创商品测试讲解",request.path("input").asText());
        assertEquals("Bearer unit-test-secret",authorization.get());
    }
    @Test void missingSpeechConfigurationDoesNotContactProvider(){
        speech.setApiKey("");
        ApiProblem error=assertThrows(ApiProblem.class,()->new OpenAiCompatibleSpeechProvider(speech).synthesize("test"));
        assertEquals("SPEECH_NOT_CONFIGURED",error.errorCode);assertNull(body.get());
    }
    @Test void providerAuthenticationFailureIsSanitized(){
        status=401;response="secret=unit-test-secret; private provider error".getBytes(StandardCharsets.UTF_8);
        ApiProblem error=assertThrows(ApiProblem.class,()->new OpenAiCompatibleSpeechProvider(speech).synthesize("test"));
        assertEquals("PROVIDER_AUTH_FAILED",error.errorCode);assertEquals(502,error.status);
        assertFalse(error.getMessage().contains("unit-test-secret"));
    }
    @Test void jsonSuccessIsNotSavedAsAudio(){
        contentType="application/json";response="{\"error\":\"bad model\"}".getBytes(StandardCharsets.UTF_8);
        assertEquals("SPEECH_INVALID_RESPONSE",assertThrows(ApiProblem.class,
                ()->new OpenAiCompatibleSpeechProvider(speech).synthesize("test")).errorCode);
    }
    @Test void textModelMissingConfigurationIsExplicit(){
        assertEquals("MODEL_NOT_CONFIGURED",assertThrows(ApiProblem.class,
                ()->new TextModelService(new AiProviderConfig()).generate("资料","导购","亲切",60)).errorCode);
    }
    @Test void textModelUsesOnlyProvidedSource() throws Exception {
        server.createContext("/v1/chat/completions",exchange->{
            body.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            byte[] reply="{\"choices\":[{\"message\":{\"content\":\"真实模型响应测试\"}}]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,reply.length);
            exchange.getResponseBody().write(reply);exchange.close();
        });
        AiProviderConfig config=new AiProviderConfig();config.setBaseUrl(speech.getBaseUrl());config.setApiKey("test");config.setModel("test-model");
        assertEquals("真实模型响应测试",new TextModelService(config).generate("书签68元","门店导购","亲切",60));
        JsonNode request=new ObjectMapper().readTree(body.get());
        assertTrue(request.path("messages").path(1).path("content").asText().contains("书签68元"));
        assertEquals("test-model",request.path("model").asText());
    }

    @Test void chatDecodesStreamingEventsAndStopsAtDone() {
        server.createContext("/v1/chat/completions",exchange->{
            byte[] reply=("data: {\"choices\":[{\"delta\":{\"content\":\"你好\"}}]}\n\n"
                    +"data: {\"choices\":[{\"delta\":{\"content\":\"，文创\"}}]}\n\n"
                    +"data: [DONE]\n\n").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","text/event-stream");exchange.sendResponseHeaders(200,reply.length);
            exchange.getResponseBody().write(reply);exchange.close();
        });
        AiProviderConfig config=new AiProviderConfig();config.setBaseUrl(speech.getBaseUrl());config.setApiKey("test");config.setModel("test-model");
        assertEquals(java.util.List.of("你好","，文创"),new TextModelService(config)
                .stream("[{\"role\":\"user\",\"content\":\"你好\"}]").collectList().block());
    }
}
