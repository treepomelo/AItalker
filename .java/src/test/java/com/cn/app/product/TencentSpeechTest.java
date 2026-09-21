package com.cn.app.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import static org.junit.jupiter.api.Assertions.*;

class TencentSpeechTest {
    HttpServer server;
    List<JsonNode> requests;
    Map<String,String> lastHeaders;
    volatile String errorCode="";
    volatile String errorMessage="";

    @BeforeEach void start() throws Exception {
        requests=new ArrayList<>();lastHeaders=new ConcurrentHashMap<>();
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        ObjectMapper mapper=new ObjectMapper();
        server.createContext("/",exchange->{
            try{
                requests.add(mapper.readTree(exchange.getRequestBody().readAllBytes()));
                exchange.getRequestHeaders().forEach((k,v)->lastHeaders.put(k.toLowerCase(),v.get(0)));
                byte[] payload;
                if(errorCode.isEmpty()){
                    String audio=Base64.getEncoder().encodeToString("ID3-chunk".getBytes(StandardCharsets.UTF_8));
                    payload=("{\"Response\":{\"Audio\":\""+audio+"\",\"RequestId\":\"req-1\"}}").getBytes(StandardCharsets.UTF_8);
                }else{
                    payload=("{\"Response\":{\"Error\":{\"Code\":\""+errorCode+"\",\"Message\":\""+errorMessage+"\"},\"RequestId\":\"req-1\"}}").getBytes(StandardCharsets.UTF_8);
                }
                exchange.getResponseHeaders().set("Content-Type","application/json");
                exchange.sendResponseHeaders(200,payload.length);
                exchange.getResponseBody().write(payload);
            }catch(Exception e){throw new RuntimeException(e);}
            finally{exchange.close();}
        });
        server.start();
    }
    @AfterEach void stop(){server.stop(0);}

    private ProviderSettings settings(){
        return new ProviderSettings("TENCENT_SPEECH","http://127.0.0.1:"+server.getAddress().getPort(),
                "AKIDtest:secret","601000","mp3",1.0,90,1024,4000,"max_tokens","{}");
    }

    @Test void sendsTc3SignedTextToVoiceRequest() throws Exception {
        byte[] bytes=new ProviderGateway().speech(settings(),"文创商品测试讲解");
        assertArrayEquals("ID3-chunk".getBytes(StandardCharsets.UTF_8),bytes);
        assertEquals(1,requests.size());
        JsonNode request=requests.get(0);
        assertEquals("文创商品测试讲解",request.path("Text").asText());
        assertEquals(601000,request.path("VoiceType").asInt());
        assertEquals("mp3",request.path("Codec").asText());
        assertEquals(16000,request.path("SampleRate").asInt());
        assertFalse(request.path("SessionId").asText().isEmpty());
        assertEquals("TextToVoice",lastHeaders.get("x-tc-action"));
        assertEquals("2019-08-23",lastHeaders.get("x-tc-version"));
        assertEquals("ap-guangzhou",lastHeaders.get("x-tc-region"));
        String authorization=lastHeaders.get("authorization");
        assertTrue(authorization.startsWith("TC3-HMAC-SHA256 Credential=AKIDtest/"));
        assertTrue(authorization.contains("/tts/tc3_request, SignedHeaders=content-type;host, Signature="));
        assertTrue(authorization.substring(authorization.lastIndexOf("Signature=")+10).matches("[0-9a-f]{64}"));
        assertFalse(authorization.contains("secret"));
    }

    @Test void longTextIsSplitAndConcatenated() throws Exception {
        String longText="第一句。".repeat(40);
        byte[] bytes=new ProviderGateway().speech(settings(),longText);
        assertTrue(requests.size()>=2,"expected multiple chunks, got "+requests.size());
        for(JsonNode request:requests)assertTrue(request.path("Text").asText().length()<=100);
        byte[] expected="ID3-chunk".getBytes(StandardCharsets.UTF_8);
        assertEquals(expected.length*requests.size(),bytes.length);
    }

    @Test void authFailureIsSanitized(){
        errorCode="AuthFailure.SecretIdNotFound";errorMessage="secret key leaked: secret";
        ApiProblem error=assertThrows(ApiProblem.class,()->new ProviderGateway().speech(settings(),"test"));
        assertEquals("PROVIDER_AUTH_FAILED",error.errorCode);assertEquals(502,error.status);
        assertFalse(error.getMessage().contains("secret"));
    }

    @Test void providerErrorKeepsCodeButNotMessage(){
        errorCode="InvalidParameterValue.InvalidText";errorMessage="内部堆栈 secret";
        ApiProblem error=assertThrows(ApiProblem.class,()->new ProviderGateway().speech(settings(),"test"));
        assertEquals("PROVIDER_ERROR",error.errorCode);
        assertTrue(error.getMessage().contains("InvalidParameterValue.InvalidText"));
        assertFalse(error.getMessage().contains("secret"));
    }

    @Test void chunkingSplitsOnPunctuationAndHardSplitsLongSentences(){
        List<String> chunks=ProviderGateway.splitSpeechChunks("短短。也短。句很长但仍在限制内。",5);
        assertEquals(List.of("短短。","也短。","句很长但仍","在限制内。"),chunks);
        assertTrue(ProviderGateway.splitSpeechChunks("  \n ",5).isEmpty());
        List<String> single=ProviderGateway.splitSpeechChunks("你好世界",10);
        assertEquals(List.of("你好世界"),single);
    }

    @Test void speedMapsIntoTencentRange(){
        assertEquals(0,ProviderGateway.tencentSpeed(1.0));
        assertEquals(1,ProviderGateway.tencentSpeed(1.2));
        assertEquals(-2,ProviderGateway.tencentSpeed(0.6));
        assertEquals(-2,ProviderGateway.tencentSpeed(0.25));
        assertEquals(6,ProviderGateway.tencentSpeed(4.0));
    }

    @Test void hmacMatchesRfc4231Vector() throws Exception {
        byte[] key=new byte[20];java.util.Arrays.fill(key,(byte)0x0b);
        assertEquals("b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7",
                HexFormat.of().formatHex(ProviderGateway.hmacSha256(key,"Hi There")));
    }

    @Test void tencentSettingsValidation(){
        ProviderSettings valid=new ProviderSettings("TENCENT_SPEECH","https://tts.tencentcloudapi.com",
                "AKIDabc:xyz","601000","mp3",1.0,90,1024,4000,"max_tokens","{}");
        assertDoesNotThrow(()->valid.validate("speech"));
        assertTrue(valid.isSpeech());
        assertEquals("https://tts.tencentcloudapi.com",valid.endpoint(false));
        assertThrows(ApiProblem.class,()->valid.withKey("no-colon-key").validate("speech"));
        assertThrows(ApiProblem.class,()->new ProviderSettings("TENCENT_SPEECH","https://tts.tencentcloudapi.com",
                "AKIDabc:xyz","601000","wav",1.0,90,1024,4000,"max_tokens","{}").validate("speech"));
        assertThrows(ApiProblem.class,()->new ProviderSettings("TENCENT_SPEECH","https://tts.tencentcloudapi.com",
                "AKIDabc:xyz","zhiyu","mp3",1.0,90,1024,4000,"max_tokens","{}").validate("speech"));
    }
}
