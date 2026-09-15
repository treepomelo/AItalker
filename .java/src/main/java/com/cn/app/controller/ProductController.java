package com.cn.app.controller;

import com.cn.app.msg.Result;
import com.cn.app.product.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController @RequestMapping("/products") @RequiredArgsConstructor
public class ProductController {
    private final CatalogRepository catalog;
    private final ProductService service;
    private final SpeechService speech;

    @GetMapping public Result list() { return Result.data(catalog.products()); }
    @GetMapping("/{id}") public Result detail(@PathVariable long id) { return Result.data(catalog.product(id)); }
    @GetMapping("/{id}/knowledge") public Result knowledge(@PathVariable long id) { catalog.product(id); return Result.data(catalog.documents(id)); }
    @PostMapping("/{id}/explanations") public Result generate(@PathVariable long id, @RequestBody(required=false) GenerateOptions options) {
        if (options==null) options=new GenerateOptions("门店导购","自然亲切",60);
        return Result.data(service.generate(id, options.scenario(),options.tone(),options.duration()));
    }
    @GetMapping("/{id}/explanations/latest") public Result latest(@PathVariable long id) {
        catalog.product(id); List<Map<String,Object>> history=catalog.history(id);
        return Result.data(history.isEmpty()?null:history.get(0));
    }
    @GetMapping("/{id}/explanations") public Result history(@PathVariable long id) { catalog.product(id); return Result.data(catalog.history(id)); }
    @PostMapping("/{id}/explanations/{explanationId}/audio") public Result audio(@PathVariable long id,@PathVariable String explanationId) {
        return Result.data(speech.create(id,explanationId));
    }
    public record GenerateOptions(String scenario,String tone,int duration) {}
}
