package com.cn.app.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.cn.app.msg.Result;
import com.cn.app.product.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.bind.annotation.*;
import java.util.Set;

@RestController @RequestMapping("/admin/model-settings") @RequiredArgsConstructor
public class ModelSettingsController {
    private final ModelSettingsStore settings;
    private final ModelConfigurationService configuration;
    private final Environment environment;

    private void authorize(HttpServletRequest request){
        String origin=request.getHeader("Origin");
        boolean loopback=Set.of("127.0.0.1","::1","0:0:0:0:0:0:0:1").contains(request.getRemoteAddr());
        boolean localHost=Set.of("127.0.0.1","localhost","::1","[::1]").contains(request.getServerName());
        boolean local=environment.acceptsProfiles(Profiles.of("local"))&&loopback&&localHost;
        if(local){
            if(origin!=null&&!Set.of("http://127.0.0.1:5173","http://localhost:5173","http://127.0.0.1:9000","http://localhost:9000").contains(origin))
                throw new ApiProblem(403,"CONFIG_ORIGIN_DENIED","配置页面来源不被允许");
            if(!request.getMethod().equals("GET")&&!"1".equals(request.getHeader("X-Config-Request")))
                throw new ApiProblem(403,"CONFIG_REQUEST_DENIED","请使用本地模型配置页操作");
            return;
        }
        StpUtil.checkLogin();StpUtil.checkRole("ADMIN");
    }
    @GetMapping public Result get(HttpServletRequest request){authorize(request);return Result.data(settings.view());}
    @PutMapping("/{channel}") public Result save(@PathVariable String channel,@RequestBody ModelSettingsStore.Edit edit,HttpServletRequest request){
        authorize(request);return Result.data(settings.save(channel,edit));
    }
    @PostMapping("/{channel}/test") public Result test(@PathVariable String channel,@RequestBody ModelSettingsStore.Edit edit,HttpServletRequest request){
        authorize(request);return Result.data(configuration.test(channel,edit));
    }
}
