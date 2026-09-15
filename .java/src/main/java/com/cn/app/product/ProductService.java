package com.cn.app.product;

import lombok.RequiredArgsConstructor;
import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.Flux;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class ProductService {
    private final CatalogRepository catalog;
    private final TextModelService model;
    private final ObjectMapper json = new ObjectMapper();

    public Flux<String> ask(long id, String question) {
        Map<String,Object> product = catalog.product(id);
        model.requireConfigured();
        if (question == null || question.isBlank() || question.length() > 1000)
            throw new ApiProblem(400,"INVALID_PRODUCT_QUESTION","请输入 1–1000 字的问题");
        List<Map<String,Object>> sources = catalog.documents(id);
        if (sources.isEmpty()) throw new ApiProblem(409,"KNOWLEDGE_NOT_READY","当前商品暂无可用知识资料");
        String source = "商品："+product.get("name")+"\n商品简介："+product.get("description")+"\n商品详情："+product.get("details")+"\n"+
                sources.stream().map(d -> "【"+d.get("name")+"】\n"+d.get("content")).collect(Collectors.joining("\n\n"));
        if (source.length() > 24000) throw new ApiProblem(400,"KNOWLEDGE_TOO_LARGE","商品资料过长，请管理员精简资料后重试");
        try {
            String messages = json.writeValueAsString(List.of(Map.of("role","user","content","我的问题是："+question)));
            String system = "你是商品知识讲解助手。只能依据下面这件商品的资料回答用户问题，不确定时明确说资料中没有写明。禁止编造价格、功效、产地、认证、授权或售后承诺。回答简洁、自然，直接回应问题。\n\n当前商品资料：\n"+source;
            return model.stream(messages,system);
        } catch (Exception e) {
            throw new ApiProblem(500,"PRODUCT_QUESTION_FAILED","商品问题处理失败，请重试");
        }
    }

    public Map<String,Object> generate(long id, String scenario, String tone, int seconds) {
        Map<String,Object> product = catalog.product(id);
        model.requireConfigured();
        if (scenario == null || tone == null || scenario.isBlank() || tone.isBlank()
                || scenario.length()>40 || tone.length()>40 || seconds<15 || seconds>180)
            throw new ApiProblem(400,"INVALID_GENERATION_OPTIONS","请选择有效场景、语气及 15–180 秒的讲解时长");
        List<Map<String,Object>> sources = catalog.documents(id);
        if (sources.isEmpty()) throw new ApiProblem(409,"KNOWLEDGE_NOT_READY","当前商品暂无可用知识资料");
        String source = "商品："+product.get("name")+"\n"+sources.stream()
                .map(d -> "【"+d.get("name")+"】\n"+d.get("content")).collect(Collectors.joining("\n\n"));
        if (source.length()>24000) throw new ApiProblem(400,"KNOWLEDGE_TOO_LARGE","商品资料过长，请管理员精简资料后重试");
        String text = model.generate(source,scenario,tone,seconds);
        return catalog.saveExplanation(id,text,scenario,tone,seconds,sources);
    }
}
