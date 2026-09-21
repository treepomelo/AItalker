package com.cn.app.product;

import jakarta.annotation.PreDestroy;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

@Service
public class SpeechService {
    private final CatalogRepository catalog;
    private final SpeechConfig config;
    private final SpeechProvider provider;
    private final ModelSettingsStore settings;
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(20), r -> { Thread t=new Thread(r,"speech-worker");t.setDaemon(true);return t; });

    @org.springframework.beans.factory.annotation.Autowired
    public SpeechService(CatalogRepository catalog, SpeechConfig config, SpeechProvider provider,ModelSettingsStore settings) {
        this.catalog=catalog;this.config=config;this.provider=provider;this.settings=settings;
    }
    public SpeechService(CatalogRepository catalog, SpeechConfig config, SpeechProvider provider) {
        this(catalog,config,provider,new ModelSettingsStore(new AiProviderConfig(),config,""));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recover() { catalog.recoverInterruptedAudio(); }

    public synchronized Map<String,Object> create(long productId,String explanationId) {
        Map<String,Object> explanation=catalog.explanation(productId,explanationId);
        if (ExplanationContentPolicy.containsCommercialContent((String)explanation.get("content")))
            throw new ApiProblem(409,"EXPLANATION_NEEDS_REGENERATION","该历史讲解含有价格或售后内容，请先生成新讲解再合成语音。");
        ProviderSettings snapshot=settings.speech();
        if (!snapshot.configured()) throw new ApiProblem(503,"SPEECH_NOT_CONFIGURED","语音服务尚未配置，文字讲解可正常查看");
        String text=(String)explanation.get("content");
        if (text.isBlank() || text.length()>snapshot.maxCharacters())
            throw new ApiProblem(400,"SPEECH_TEXT_INVALID","讲解超出语音长度限制，请生成更短的讲解");
        String key=hash(explanationId+"\n"+text+"\n"+snapshot.fingerprint());
        Optional<String> existing=catalog.audioByCache(key);
        String id;
        if (existing.isPresent()) {
            id=existing.get();
            String status=(String)catalog.audio(id).get("status");
            if (status.equals("PENDING") || status.equals("RUNNING") || (status.equals("COMPLETED") && Files.exists(filePath(id))))
                return catalog.audio(id);
            catalog.audioStatus(id,"PENDING",null,null);
        } else {
            try { id=catalog.createAudio(productId,explanationId,key); }
            catch (DuplicateKeyException e) { return catalog.audio(catalog.audioByCache(key).orElseThrow()); }
        }
        final String taskId=id;
        try { workers.execute(() -> execute(taskId,text,snapshot)); }
        catch (RejectedExecutionException e) {
            catalog.audioStatus(id,"FAILED",null,"语音任务较多，请稍后重试");
            throw new ApiProblem(429,"SPEECH_QUEUE_FULL","语音任务较多，请稍后重试");
        }
        return catalog.audio(id);
    }

    private void execute(String id,String text,ProviderSettings snapshot) {
        try {
            catalog.audioStatus(id,"RUNNING",null,null);
            byte[] bytes=provider.synthesize(text,snapshot);
            Path file=filePath(id);
            Files.createDirectories(file.getParent());
            Path temp=Files.createTempFile(file.getParent(),id,".tmp");
            try {
                Files.write(temp,bytes);
                Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);
            } finally { Files.deleteIfExists(temp); }
            catalog.audioStatus(id,"COMPLETED","/audio-tasks/"+id+"/file",null);
        } catch (Exception e) {
            catalog.audioStatus(id,"FAILED",null,e instanceof ApiProblem ? e.getMessage() : "音频生成或保存失败，请重试");
        }
    }

    public Path audioFile(String id) {
        Map<String,Object> task=catalog.audio(id);
        Path path=filePath(id);
        if (!"COMPLETED".equals(task.get("status")) || !Files.isRegularFile(path))
            throw new ApiProblem(404,"AUDIO_NOT_READY","音频尚未就绪或文件已过期，请重新生成");
        return path;
    }

    private Path filePath(String id) {
        try { UUID.fromString(id); } catch (IllegalArgumentException e) {
            throw new ApiProblem(400,"INVALID_TASK_ID","无效的语音任务编号");
        }
        return Path.of(config.getStorageDirectory()).toAbsolutePath().normalize().resolve(id+".mp3");
    }

    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    @PreDestroy public void close() { workers.shutdownNow(); }
}
