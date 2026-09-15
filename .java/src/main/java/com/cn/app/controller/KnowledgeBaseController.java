package com.cn.app.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.cn.app.msg.Result;
import com.cn.app.product.ApiProblem;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController @RequestMapping("/admin/knowledge-bases") @RequiredArgsConstructor
public class KnowledgeBaseController {
    private final JdbcTemplate jdbc;
    private void admin() { StpUtil.checkLogin(); StpUtil.checkRole("ADMIN"); }
    @GetMapping public Result list() { admin(); return Result.data(jdbc.queryForList("SELECT * FROM knowledge_base ORDER BY created_at")); }
    @PostMapping public Result create(@RequestBody Map<String,String> body) {
        admin(); String id=UUID.randomUUID().toString(); String name=body.getOrDefault("name","").trim();
        if(name.isEmpty() || name.length()>200) throw new ApiProblem(400,"INVALID_NAME","请填写有效的知识库名称");
        jdbc.update("INSERT INTO knowledge_base(id,name,status,created_at) VALUES(?,?,'ACTIVE',NOW())",id,name);
        return Result.data(Map.of("id",id,"name",name));
    }
    private void requireBase(String id) {
        if(jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_base WHERE id=?",Integer.class,id)==0)
            throw new ApiProblem(404,"KNOWLEDGE_BASE_NOT_FOUND","知识库不存在");
    }
    @PostMapping("/{id}/documents") public Result add(@PathVariable String id,@RequestBody Map<String,String> body) {
        admin(); requireBase(id); String content=body.getOrDefault("content","").trim();
        String name=body.getOrDefault("name","资料");
        if(content.isEmpty() || content.length()>20000 || name.length()>255)
            throw new ApiProblem(400,"INVALID_DOCUMENT","请输入 1–20000 字的资料正文及有效文件名");
        String did=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO knowledge_document(id,knowledge_base_id,name,type,content,status,created_at) VALUES(?,?,?,'TEXT',?,'COMPLETED',NOW())",did,id,name,content);
        return Result.data(Map.of("id",did,"knowledgeBaseId",id,"status","COMPLETED"));
    }
    @GetMapping("/{id}/documents") public Result documents(@PathVariable String id) {
        admin(); requireBase(id); return Result.data(jdbc.queryForList("SELECT * FROM knowledge_document WHERE knowledge_base_id=?",id));
    }
    @PostMapping("/{id}/products/{productId}") public Result attach(@PathVariable String id,@PathVariable long productId) {
        admin(); requireBase(id);
        if(jdbc.queryForObject("SELECT COUNT(*) FROM product WHERE id=?",Integer.class,productId)==0)
            throw new ApiProblem(404,"PRODUCT_NOT_FOUND","商品不存在");
        jdbc.update("INSERT INTO product_knowledge_rel(product_id,knowledge_base_id) VALUES(?,?) ON DUPLICATE KEY UPDATE product_id=product_id",productId,id);
        return Result.ok();
    }
}
