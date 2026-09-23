package com.cn.app.net;

import com.cn.app.product.ApiProblem;
import com.cn.app.product.TextModelService;
import com.cn.app.utils.SpringContextUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import java.util.Map;

@Component @ServerEndpoint("/text-chat")
public class TextChatSocket {
    private Session session;
    private Disposable subscription;
    private boolean started;
    private final ObjectMapper json=new ObjectMapper();

    @OnOpen public void open(Session session) {
        this.session=session;
        session.setMaxTextMessageBufferSize(32000);
        session.setMaxIdleTimeout(120000);
    }
    @OnMessage public void message(String input) {
        if(started)return;
        started=true;
        try {
            TextModelService model=(TextModelService)SpringContextUtil.getBean("textModelService");
            subscription=model.stream(input).subscribe(text -> send("delta",text),e -> {
                send("error",e instanceof ApiProblem?e.getMessage():"聊天服务暂不可用，请重试");close();
            },() -> {send("done","");close();});
        } catch(Exception e) {send("error",e instanceof ApiProblem?e.getMessage():"聊天服务暂不可用");close();}
    }
    private synchronized void send(String type,String content) {
        try {if(session!=null&&session.isOpen())session.getBasicRemote().sendText(json.writeValueAsString(Map.of("type",type,"content",content)));}
        catch(Exception e) {close();}
    }
    @OnClose public void close() {
        if(subscription!=null)subscription.dispose();
        try {if(session!=null&&session.isOpen())session.close();}catch(Exception ignored){}
    }
    @OnError public void error(Throwable e) {close();}
}
