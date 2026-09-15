package com.cn.app.product;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SpeechServiceTest {
    @TempDir Path directory;
    CatalogRepository catalog;
    SpeechConfig config;
    SpeechService service;
    String id=UUID.randomUUID().toString();
    Map<String,Object> task=new ConcurrentHashMap<>();
    AtomicReference<String> cache=new AtomicReference<>();

    @BeforeEach void setup(){
        catalog=mock(CatalogRepository.class);config=new SpeechConfig();
        config.setBaseUrl("https://test.invalid/v1");config.setApiKey("test");config.setModel("tts");config.setStorageDirectory(directory.toString());
        when(catalog.explanation(1001L,"example")).thenReturn(Map.of("content","测试讲解"));
        when(catalog.audioByCache(anyString())).thenAnswer(i->Optional.ofNullable(cache.get()));
        when(catalog.createAudio(eq(1001L),eq("example"),anyString())).thenAnswer(i->{cache.set(id);task.put("status","PENDING");task.put("taskId",id);return id;});
        when(catalog.audio(id)).thenAnswer(i->new HashMap<>(task));
        doAnswer(i->{task.put("status",i.getArgument(1));return null;}).when(catalog).audioStatus(eq(id),anyString(),nullable(String.class),nullable(String.class));
    }
    @AfterEach void close(){if(service!=null)service.close();}
    @Test void repeatedPlaybackReusesTaskAndSavedAudio() throws Exception {
        AtomicInteger calls=new AtomicInteger();
        service=new SpeechService(catalog,config,text->{calls.incrementAndGet();return new byte[]{1,2,3};});
        assertEquals(id,service.create(1001,"example").get("taskId"));
        await().atMost(Duration.ofSeconds(3)).until(()->"COMPLETED".equals(task.get("status")));
        assertArrayEquals(new byte[]{1,2,3},Files.readAllBytes(service.audioFile(id)));
        assertEquals(id,service.create(1001,"example").get("taskId"));assertEquals(1,calls.get());
        verify(catalog,times(1)).createAudio(anyLong(),anyString(),anyString());
    }
    @Test void failedTaskCanRetryWithoutLosingExplanation(){
        AtomicInteger calls=new AtomicInteger();
        service=new SpeechService(catalog,config,text->{if(calls.incrementAndGet()==1)throw new ApiProblem(502,"TEST","test failure");return new byte[]{1};});
        service.create(1001,"example");
        await().atMost(Duration.ofSeconds(3)).until(()->"FAILED".equals(task.get("status")));
        assertEquals(id,service.create(1001,"example").get("taskId"));
        await().atMost(Duration.ofSeconds(3)).until(()->"COMPLETED".equals(task.get("status")));
        assertEquals(2,calls.get());
    }
    @Test void missingConfigNeverCreatesPendingTask(){
        config.setApiKey("");service=new SpeechService(catalog,config,text->new byte[0]);
        assertEquals("SPEECH_NOT_CONFIGURED",assertThrows(ApiProblem.class,()->service.create(1001,"example")).errorCode);
        verify(catalog,never()).createAudio(anyLong(),anyString(),anyString());
    }
    @Test void productOwnershipIsCheckedBeforeSynthesizing(){
        when(catalog.explanation(1002,"example")).thenThrow(new ApiProblem(404,"EXPLANATION_NOT_FOUND","not found"));
        SpeechProvider provider=mock(SpeechProvider.class);service=new SpeechService(catalog,config,provider);
        assertThrows(ApiProblem.class,()->service.create(1002,"example"));verifyNoInteractions(provider);
    }
    @Test void submittedJobKeepsItsSnapshotWhenConfigurationChanges() throws Exception {
        var settings=new ModelSettingsStore(new AiProviderConfig(),config,"");
        var original=settings.speech();
        var entered=new java.util.concurrent.CountDownLatch(1);
        var resume=new java.util.concurrent.CountDownLatch(1);
        AtomicReference<ProviderSettings> received=new AtomicReference<>();
        SpeechProvider provider=new SpeechProvider(){
            public byte[] synthesize(String text){throw new AssertionError("Snapshot required");}
            public byte[] synthesize(String text,ProviderSettings snapshot){
                entered.countDown();
                try {if(!resume.await(3,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("Timeout");}
                catch(InterruptedException e){throw new RuntimeException(e);}
                received.set(snapshot);return new byte[]{1};
            }
        };
        service=new SpeechService(catalog,config,provider,settings);
        service.create(1001,"example");assertTrue(entered.await(3,java.util.concurrent.TimeUnit.SECONDS));
        try {settings.save("speech",new ModelSettingsStore.Edit(0,original.withKey("replacement-key"),false));}
        finally {resume.countDown();}
        await().atMost(Duration.ofSeconds(3)).until(()->"COMPLETED".equals(task.get("status")));
        assertEquals(original,received.get());assertNotEquals(settings.speech().fingerprint(),received.get().fingerprint());
    }
}
