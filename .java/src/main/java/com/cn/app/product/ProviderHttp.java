package com.cn.app.product;

import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;
import java.net.URI;

final class ProviderHttp {
    private ProviderHttp() {}

    static WebClient client(String baseUrl, String key) {
        try {
            URI uri = URI.create(baseUrl);
            if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null
                    || uri.getFragment() != null) throw new IllegalArgumentException();
        } catch (Exception e) {
            throw new ApiProblem(503, "PROVIDER_CONFIG_INVALID", "模型服务地址配置无效，请联系管理员");
        }
        return WebClient.builder().baseUrl(baseUrl.replaceAll("/+$", ""))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + key)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(20 * 1024 * 1024)).build();
    }

    static ApiProblem failure(int status, String label) {
        if (status == 401 || status == 403)
            return new ApiProblem(502, "PROVIDER_AUTH_FAILED", label + "服务认证失败，请检查服务端 API Key");
        if (status == 429)
            return new ApiProblem(503, "PROVIDER_RATE_LIMITED", label + "服务额度不足或请求过于频繁，请稍后重试");
        return new ApiProblem(502, "PROVIDER_ERROR", label + "服务调用失败，请检查模型配置后重试");
    }
}
