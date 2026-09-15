package com.cn.app.product;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Repository @RequiredArgsConstructor
public class CatalogRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public List<Map<String, Object>> products() {
        return jdbc.query("SELECT p.id,p.name,p.description,d.details FROM product p LEFT JOIN product_profile d ON d.product_id=p.id ORDER BY p.id", (rs,n) -> {
            Map<String,Object> p = new LinkedHashMap<>();
            p.put("id", rs.getLong("id")); p.put("name", rs.getString("name")); p.put("description", rs.getString("description"));
            String details = rs.getString("details");
            if (details != null) try { p.put("details", json.readValue(details, new TypeReference<Map<String,Object>>() {})); }
            catch (Exception ignored) { p.put("details", Map.of()); }
            return p;
        });
    }

    public Map<String,Object> product(long id) {
        return products().stream().filter(p -> ((Long)p.get("id")) == id).findFirst()
                .orElseThrow(() -> new ApiProblem(404,"PRODUCT_NOT_FOUND","商品不存在"));
    }

    public List<Map<String,Object>> documents(long productId) {
        return jdbc.query("SELECT d.id,d.name,d.content FROM knowledge_document d JOIN product_knowledge_rel r ON r.knowledge_base_id=d.knowledge_base_id JOIN knowledge_base b ON b.id=d.knowledge_base_id WHERE r.product_id=? AND d.status='COMPLETED' AND b.status='ACTIVE' ORDER BY d.id", (rs,n) -> Map.of(
                "id", rs.getString("id"), "name", rs.getString("name"), "content", Objects.toString(rs.getString("content"),"")), productId);
    }

    private List<Map<String,Object>> explanations(String condition, Object... args) {
        return jdbc.query("SELECT e.*,d.seq,d.is_demo,d.scenario,d.tone,d.duration_seconds,d.sources FROM product_explanation e JOIN explanation_detail d ON d.explanation_id=e.id WHERE "+condition+" ORDER BY d.seq DESC", (rs,n) -> {
            Map<String,Object> e = new LinkedHashMap<>();
            e.put("id",rs.getString("id")); e.put("productId",rs.getLong("product_id"));
            e.put("content",rs.getString("content")); e.put("createdAt",rs.getTimestamp("created_at").toLocalDateTime().toString());
            e.put("isDemo",rs.getBoolean("is_demo")); e.put("version",rs.getLong("seq"));
            e.put("scenario",rs.getString("scenario")); e.put("tone",rs.getString("tone"));
            e.put("duration",rs.getInt("duration_seconds"));
            try { e.put("sources",json.readValue(rs.getString("sources"),List.class)); }
            catch (Exception ignored) { e.put("sources",List.of()); }
            return e;
        },args);
    }

    public List<Map<String,Object>> history(long id) { return explanations("e.product_id=?",id); }

    public Map<String,Object> explanation(long productId, String explanationId) {
        return explanations("e.product_id=? AND e.id=?",productId,explanationId).stream().findFirst()
                .orElseThrow(() -> new ApiProblem(404,"EXPLANATION_NOT_FOUND","该商品的讲解版本不存在"));
    }

    @Transactional
    public Map<String,Object> saveExplanation(long productId, String text, String scenario, String tone, int seconds, List<Map<String,Object>> sources) {
        String id = UUID.randomUUID().toString();
        String serialized;
        try { serialized = json.writeValueAsString(sources.stream().map(s -> Map.of("id",s.get("id"),"name",s.get("name"))).toList()); }
        catch (Exception e) { throw new IllegalStateException("Could not serialize sources"); }
        jdbc.update("INSERT INTO product_explanation(id,product_id,content,status,created_at) VALUES(?,?,?,'COMPLETED',NOW())",id,productId,text);
        jdbc.update("INSERT INTO explanation_detail(explanation_id,is_demo,scenario,tone,duration_seconds,sources) VALUES(?,false,?,?,?,?)",id,scenario,tone,seconds,serialized);
        return explanation(productId,id);
    }

    public Map<String,Object> audio(String id) {
        List<Map<String,Object>> rows = jdbc.query("SELECT t.*,o.product_id FROM speech_task t JOIN speech_task_options o ON o.task_id=t.id WHERE t.id=?",(rs,n) -> {
            Map<String,Object> t = new LinkedHashMap<>();
            t.put("taskId",rs.getString("id")); t.put("status",rs.getString("status"));
            t.put("productId",rs.getLong("product_id")); t.put("explanationId",rs.getString("explanation_id"));
            t.put("audioUrl",rs.getString("audio_url")); t.put("error",rs.getString("error_message"));
            return t;
        },id);
        if (rows.isEmpty()) throw new ApiProblem(404,"AUDIO_TASK_NOT_FOUND","语音任务不存在");
        return rows.get(0);
    }

    public Optional<String> audioByCache(String key) {
        return jdbc.query("SELECT task_id FROM speech_task_options WHERE cache_key=?", (rs,n)->rs.getString(1),key).stream().findFirst();
    }

    @Transactional
    public String createAudio(long productId, String explanationId, String key) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO speech_task(id,explanation_id,status,created_at) VALUES(?,?,'PENDING',NOW())",id,explanationId);
        jdbc.update("INSERT INTO speech_task_options(task_id,product_id,cache_key) VALUES(?,?,?)",id,productId,key);
        return id;
    }

    public void audioStatus(String id,String status,String url,String error) {
        jdbc.update("UPDATE speech_task SET status=?,audio_url=?,error_message=? WHERE id=?",status,url,error,id);
    }

    public void recoverInterruptedAudio() {
        jdbc.update("UPDATE speech_task SET status='FAILED',error_message='服务已重启，请重新生成语音' WHERE status IN ('PENDING','RUNNING')");
    }
}
