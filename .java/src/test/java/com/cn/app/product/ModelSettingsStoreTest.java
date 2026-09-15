package com.cn.app.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ModelSettingsStoreTest {
    @TempDir Path temp;
    ModelSettingsStore store;
    ProviderSettings config(String url,String key,String model){return new ProviderSettings("OPENAI_CHAT",url,key,model,"",1,10,128,4000,"max_tokens","{}");}
    @BeforeEach void setup(){store=new ModelSettingsStore(new AiProviderConfig(),new SpeechConfig(),temp.resolve("settings.json").toString());}
    @Test void settingsPersistButApiKeyNeverAppearsInResponse() throws Exception{
        var view=store.save("chat",new ModelSettingsStore.Edit(0,config("https://example.com/v1","secret-key","test"),false));
        assertFalse(new ObjectMapper().writeValueAsString(view).contains("secret-key"));
        ModelSettingsStore reloaded=new ModelSettingsStore(new AiProviderConfig(),new SpeechConfig(),temp.resolve("settings.json").toString());
        assertEquals("secret-key",reloaded.chat().apiKey());assertEquals(store.chat().fingerprint(),reloaded.chat().fingerprint());
        assertFalse(reloaded.chat().toString().contains("secret-key"));
    }
    @Test void blankKeyPreservesButExplicitClearRemoves(){
        store.save("chat",new ModelSettingsStore.Edit(0,config("https://example.com/v1","secret","one"),false));
        store.save("chat",new ModelSettingsStore.Edit(1,config("https://example.com/v1","","two"),false));assertEquals("secret",store.chat().apiKey());
        store.save("chat",new ModelSettingsStore.Edit(2,config("https://example.com/v1","","two"),true));assertFalse(store.chat().configured());
    }
    @Test void credentialIsNotReusedOnDifferentHost(){
        store.save("chat",new ModelSettingsStore.Edit(0,config("https://first.example/v1","secret","one"),false));
        assertEquals("NEW_ENDPOINT_NEEDS_KEY",assertThrows(ApiProblem.class,()->store.draft("chat",new ModelSettingsStore.Edit(1,config("https://second.example/v1","","two"),false))).errorCode);
        assertEquals("new-secret",store.draft("chat",new ModelSettingsStore.Edit(1,config("https://second.example/v1","new-secret","two"),false)).apiKey());
    }
    @Test void staleRevisionDoesNotOverwriteNewConfiguration(){
        store.save("chat",new ModelSettingsStore.Edit(0,config("https://example.com/v1","secret","one"),false));
        assertEquals(409,assertThrows(ApiProblem.class,()->store.save("chat",new ModelSettingsStore.Edit(0,config("https://example.com/v1","secret","two"),false))).status);
        assertEquals("one",store.chat().model());
    }
    @Test void verificationIsTiedToExactSettings(){
        ProviderSettings first=config("https://example.com/v1","secret","one");
        store.recordTest(first,true,Map.of("checks",Map.of()));store.save("chat",new ModelSettingsStore.Edit(0,first,false));
        assertEquals("PASSED",verification(store.view()));
        store.save("chat",new ModelSettingsStore.Edit(1,config("https://example.com/v1","","two"),false));
        assertEquals("UNTESTED",verification(store.view()));assertNotEquals(first.fingerprint(),store.chat().fingerprint());
    }
    private Object verification(Map<String,Object> view){return ((Map<?,?>)((Map<?,?>)view.get("chat")).get("verification")).get("status");}
    @Test void coreRequestFieldsCannotBeOverriddenByExtras(){
        ProviderSettings invalid=new ProviderSettings("OPENAI_CHAT","https://example.com/v1","secret","one","",1,10,128,4000,"max_tokens","{\"model\":\"hidden-model\"}");
        assertThrows(ApiProblem.class,()->store.save("chat",new ModelSettingsStore.Edit(0,invalid,false)));
    }
}
