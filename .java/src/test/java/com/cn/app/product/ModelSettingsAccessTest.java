package com.cn.app.product;

import com.cn.app.controller.ModelSettingsController;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ModelSettingsAccessTest {
    private final ModelSettingsStore store=mock(ModelSettingsStore.class);
    private final ModelSettingsController controller=new ModelSettingsController(store,mock(ModelConfigurationService.class));
    private MockHttpServletRequest request(){
        var request=new MockHttpServletRequest();request.setRemoteAddr("127.0.0.1");request.setServerName("localhost");request.setMethod("GET");return request;
    }
    @Test void anyoneCanReadConfiguration(){
        controller.get(request());verify(store).view();
    }
    @Test void remoteClientCanReadConfiguration(){
        var request=request();request.setRemoteAddr("192.168.1.12");request.setServerName("example.com");
        controller.get(request);verify(store).view();
    }
    @Test void mutationRequiresCustomHeader(){
        var request=request();request.setMethod("PUT");
        assertEquals("CONFIG_REQUEST_DENIED",assertThrows(ApiProblem.class,()->controller.save("chat",null,request)).errorCode);verifyNoInteractions(store);
        request.addHeader("X-Config-Request","1");controller.save("chat",null,request);verify(store).save("chat",null);
    }
}
