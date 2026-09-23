package com.cn.app.controller;

import com.cn.app.msg.Result;
import com.cn.app.product.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/admin/model-settings") @RequiredArgsConstructor
public class ModelSettingsController {
    private final ModelSettingsStore settings;
    private final ModelConfigurationService configuration;

    private void authorize(HttpServletRequest request){
        if(!request.getMethod().equals("GET")&&!"1".equals(request.getHeader("X-Config-Request")))
            throw new ApiProblem(403,"CONFIG_REQUEST_DENIED","请使用模型配置页操作");
    }
    @GetMapping public Result get(HttpServletRequest request){authorize(request);return Result.data(settings.view());}
    @PutMapping("/{channel}") public Result save(@PathVariable String channel,@RequestBody ModelSettingsStore.Edit edit,HttpServletRequest request){
        authorize(request);return Result.data(settings.save(channel,edit));
    }
    @PostMapping("/{channel}/test") public Result test(@PathVariable String channel,@RequestBody ModelSettingsStore.Edit edit,HttpServletRequest request){
        authorize(request);return Result.data(configuration.test(channel,edit));
    }
}
