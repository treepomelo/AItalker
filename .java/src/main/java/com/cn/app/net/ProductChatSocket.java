package com.cn.app.net;

import com.cn.app.product.ApiProblem;
import com.cn.app.product.ProductService;
import com.cn.app.utils.SpringContextUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import java.util.Map;

@Component @ServerEndpoint("/product-chat/{productId}")
public class ProductChatSocket {
    private Session session;
    private Disposable subscription;
    private long productId;
    private boolean started;
    private final ObjectMapper json = new ObjectMapper();

    @OnOpen public void open(Session session, @PathParam("productId") long productId) {
        this.session = session;
        this.productId = productId;
        session.setMaxTextMessageBufferSize(32000);
        session.setMaxIdleTimeout(120000);
    }

    @OnMessage public void message(String input) {
        if (started) return;
        started = true;
        try {
            JsonNode node = json.readTree(input);
            String question = node.path("question").asText("");
            if (productId < 1) throw new ApiProblem(400,"INVALID_PRODUCT","商品不存在");
            ProductService service = (ProductService) SpringContextUtil.getBean("productService");
            subscription = service.ask(productId, question).subscribe(text -> send("delta", text), e -> {
                send("error", e instanceof ApiProblem ? e.getMessage() : "商品讲解暂不可用，请重试"); close();
            }, () -> { send("done", ""); close(); });
        } catch (Exception e) { send("error", e instanceof ApiProblem ? e.getMessage() : "问题格式无效，请重试"); close(); }
    }

    private synchronized void send(String type, String content) {
        try { if (session != null && session.isOpen()) session.getBasicRemote().sendText(json.writeValueAsString(Map.of("type",type,"content",content))); }
        catch (Exception e) { close(); }
    }
    @OnClose public void close() { if (subscription != null) subscription.dispose(); try { if (session != null && session.isOpen()) session.close(); } catch (Exception ignored) {} }
    @OnError public void error(Throwable e) { close(); }
}
