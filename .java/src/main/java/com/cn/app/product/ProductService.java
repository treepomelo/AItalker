package com.cn.app.product;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class ProductService {
    private final CatalogRepository catalog;
    private final TextModelService model;

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
