package com.cn.app.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.nio.file.*;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ModelSettingsStore {
    public record Bundle(long revision,ProviderSettings chat,ProviderSettings speech){}
    public record Edit(long revision,ProviderSettings config,boolean clearKey){}
    private final Path file;
    private final ObjectMapper json=new ObjectMapper();
    private volatile Bundle current;
    private final Map<String,Map<String,Object>> tests=new ConcurrentHashMap<>();

    public ModelSettingsStore(AiProviderConfig ai,SpeechConfig speech,@Value("${super.config.path:../.local/model-settings.json}") String path){
        file=path.isBlank()?null:Path.of(path).toAbsolutePath().normalize();
        current=new Bundle(0,ProviderSettings.ai(ai),ProviderSettings.speech(speech));
        if(file!=null&&Files.exists(file))try{
            Bundle saved=json.readValue(file.toFile(),Bundle.class);
            saved.chat().validate("chat");saved.speech().validate("speech");current=saved;
        }catch(Exception e){throw new IllegalStateException("模型配置文件无法读取，请检查本地 model-settings.json");}
    }
    public ProviderSettings chat(){return current.chat();}
    public ProviderSettings speech(){return current.speech();}
    public synchronized ProviderSettings draft(String channel,Edit edit){
        channel(channel);
        if(edit==null||edit.config()==null)throw new ApiProblem(400,"INVALID_MODEL_CONFIG","缺少配置内容");
        if(edit.revision()!=current.revision())throw new ApiProblem(409,"CONFIG_CONFLICT","配置已被修改，请重新加载后编辑");
        ProviderSettings old=channel.equals("chat")?chat():speech(), candidate=edit.config();
        candidate.validate(channel);
        if(candidate.apiKey().isBlank()&&!edit.clearKey()){
            if(!old.apiKey().isBlank()&&!authority(old.baseUrl()).equals(authority(candidate.baseUrl())))
                throw new ApiProblem(400,"NEW_ENDPOINT_NEEDS_KEY","服务主机已改变，请为新服务重新填写 Key，避免误用旧凭据");
            candidate=candidate.withKey(old.apiKey());
        }else if(edit.clearKey())candidate=candidate.withKey("");
        return candidate;
    }
    public synchronized Map<String,Object> save(String channel,Edit edit){
        ProviderSettings candidate=draft(channel,edit);
        Bundle next=new Bundle(current.revision()+1,channel.equals("chat")?candidate:chat(),channel.equals("speech")?candidate:speech());
        persist(next);current=next;return view();
    }
    private void persist(Bundle next){
        if(file==null)return;
        try{
            Files.createDirectories(file.getParent());
            Path temp=Files.createTempFile(file.getParent(),"model-settings-",".tmp");
            try{
                json.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(),next);
                try{Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
                catch(AtomicMoveNotSupportedException e){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}
            }finally{Files.deleteIfExists(temp);}
        }catch(Exception e){throw new ApiProblem(500,"CONFIG_SAVE_FAILED","配置保存失败，请检查服务端目录权限");}
    }
    public synchronized Map<String,Object> view(){return Map.of("revision",current.revision(),"chat",masked(chat()),"speech",masked(speech()));}
    private Map<String,Object> masked(ProviderSettings s){
        Map<String,Object> m=new LinkedHashMap<>();
        m.put("protocol",s.protocol());m.put("baseUrl",s.baseUrl());m.put("model",s.model());m.put("voice",s.voice());
        m.put("speed",s.speed());m.put("timeoutSeconds",s.timeoutSeconds());m.put("maxTokens",s.maxTokens());
        m.put("maxCharacters",s.maxCharacters());m.put("extraBody",s.extraBody());m.put("hasApiKey",!s.apiKey().isBlank());
        m.put("outputLimitField",s.outputLimitField());
        m.put("configured",s.configured());m.put("fingerprint",s.fingerprint());m.put("endpoint",s.endpoint(false));
        m.put("streamEndpoint",s.isSpeech()?"":s.endpoint(true));
        m.put("verification",tests.getOrDefault(s.fingerprint(),Map.of("status","UNTESTED")));return m;
    }
    public Map<String,Object> recordTest(ProviderSettings s,boolean success,Map<String,Object> details){
        Map<String,Object> result=new LinkedHashMap<>(details);result.put("fingerprint",s.fingerprint());
        result.put("status",success?"PASSED":"FAILED");result.put("checkedAt",Instant.now().toString());
        if(tests.size()>100)tests.clear();tests.put(s.fingerprint(),result);return result;
    }
    private static String authority(String url){if(url.isBlank())return "";URI u=URI.create(url);return u.getScheme()+"://"+u.getAuthority();}
    private static void channel(String channel){if(!Set.of("chat","speech").contains(channel))throw new ApiProblem(404,"CONFIG_NOT_FOUND","未知的配置类型");}
}
