package com.cn.app.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import java.util.*;

@Service
public class TextModelService {
    private final ModelSettingsStore settings;
    private final ProviderGateway gateway;
    private final PromptConfig prompts;
    @Autowired public TextModelService(ModelSettingsStore settings,ProviderGateway gateway,PromptConfig prompts){this.settings=settings;this.gateway=gateway;this.prompts=prompts;}
    public TextModelService(AiProviderConfig config){this(new ModelSettingsStore(config,new SpeechConfig(),""),new ProviderGateway(),new PromptConfig());}
    public void requireConfigured(){gateway.require(settings.chat(),"chat");}
    public Flux<String> stream(String messagesJson){
        return stream(messagesJson,prompts.assistantPrompt());
    }
    public Flux<String> stream(String messagesJson,String systemPrompt){
        ProviderSettings snapshot=settings.chat();gateway.require(snapshot,"chat");
        List<Map<String,String>> messages=new ArrayList<>();
        messages.add(Map.of("role","system","content",systemPrompt));
        try{
            JsonNode input=new ObjectMapper().readTree(messagesJson);
            if(!input.isArray()||input.isEmpty()||input.size()>30||messagesJson.length()>24000)throw new IllegalArgumentException();
            for(JsonNode item:input){String role=item.path("role").asText(),content=item.path("content").asText();
                if(!(role.equals("user")||role.equals("assistant"))||content.isBlank())throw new IllegalArgumentException();
                messages.add(Map.of("role",role,"content",content));}
        }catch(Exception e){throw new ApiProblem(400,"CHAT_INPUT_INVALID","消息为空或过长，请新建会话后重试");}
        return gateway.stream(snapshot,messages);
    }
    public String generate(String source,String scenario,String tone,int seconds){
        ProviderSettings snapshot=settings.chat();
        String system=prompts.explanationPrompt();
        String prompt="场景："+scenario+"；语气："+tone+"；目标时长："+seconds+"秒。\n资料：\n"+source;
        int requestedTokens=Math.min(4096,Math.max(1024,seconds*12));
        ProviderSettings generation=snapshot.withMaxTokens(Math.max(snapshot.maxTokens(),requestedTokens));
        return gateway.complete(generation,List.of(Map.of("role","system","content",system),Map.of("role","user","content",prompt)));
    }
}
