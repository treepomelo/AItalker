package com.cn.app.product;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.*;

@Service @RequiredArgsConstructor
public class ModelConfigurationService {
    private final ModelSettingsStore settings;
    private final ProviderGateway gateway;

    public Map<String,Object> test(String channel,ModelSettingsStore.Edit edit){
        ProviderSettings s=settings.draft(channel,edit);
        Map<String,Object> checks=new LinkedHashMap<>();
        long started=System.nanoTime();
        if(channel.equals("chat")){
            var prompt=List.of(Map.of("role","system","content","这是连接测试。"),Map.of("role","user","content","请只回复：连接成功。"));
            try{String text=gateway.complete(s,prompt);checks.put("普通回复",Map.of("passed",true,"detail","收到 "+text.length()+" 字文本"));}
            catch(ApiProblem e){checks.put("普通回复",Map.of("passed",false,"detail",e.getMessage()));}
            try{
                String text=gateway.stream(s,prompt).reduce("",(a,b)->a+b).block(Duration.ofSeconds(s.timeoutSeconds()+5L));
                if(text==null||text.isBlank())throw new ApiProblem(502,"MODEL_EMPTY_STREAM","流式响应为空");
                checks.put("流式回复",Map.of("passed",true,"detail","收到 "+text.length()+" 字流式文本"));
            }catch(Exception e){checks.put("流式回复",Map.of("passed",false,"detail",e instanceof ApiProblem?e.getMessage():"流式调用超时或失败"));}
        }else{
            try{byte[] audio=gateway.speech(s,"你好，这是语音连接测试。");checks.put("语音合成",Map.of("passed",true,"detail","收到 MP3 格式响应，"+audio.length+" 字节"));}
            catch(ApiProblem e){checks.put("语音合成",Map.of("passed",false,"detail",e.getMessage()));}
        }
        boolean success=checks.values().stream().allMatch(c->Boolean.TRUE.equals(((Map<?,?>)c).get("passed")));
        return settings.recordTest(s,success,Map.of("checks",checks,"elapsedMs",(System.nanoTime()-started)/1_000_000,
                "endpoint",s.endpoint(false),"streamEndpoint",s.isSpeech()?"":s.endpoint(true),"protocol",s.protocol()));
    }
}
