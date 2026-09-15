package com.cn.app.product;

import com.cn.app.controller.ModelSettingsController;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ModelSettingsAccessTest {
    private final ModelSettingsStore store=mock(ModelSettingsStore.class);
    private final MockEnvironment environment=new MockEnvironment();
    private final ModelSettingsController controller=new ModelSettingsController(store,mock(ModelConfigurationService.class),environment);
    private MockHttpServletRequest request(){
        environment.setActiveProfiles("local");
        var request=new MockHttpServletRequest();request.setRemoteAddr("127.0.0.1");request.setServerName("localhost");request.setMethod("GET");return request;
    }
    @Test void localPageCanReadConfiguration(){
        var request=request();request.addHeader("Origin","http://127.0.0.1:5173");controller.get(request);verify(store).view();
    }
    @Test void unrelatedWebsiteCannotReadEvenOnLoopback(){
        var request=request();request.addHeader("Origin","https://unrelated.example");
        assertEquals("CONFIG_ORIGIN_DENIED",assertThrows(ApiProblem.class,()->controller.get(request)).errorCode);verifyNoInteractions(store);
    }
    @Test void localMutationRequiresCustomHeader(){
        var request=request();request.setMethod("PUT");
        assertEquals("CONFIG_REQUEST_DENIED",assertThrows(ApiProblem.class,()->controller.save("chat",null,request)).errorCode);verifyNoInteractions(store);
        request.addHeader("X-Config-Request","1");controller.save("chat",null,request);verify(store).save("chat",null);
    }
    @Test void unrelatedHostDoesNotReceiveLocalBypass(){
        var request=request();request.setServerName("unrelated.example");assertThrows(Exception.class,()->controller.get(request));verifyNoInteractions(store);
    }
    @Test void remoteAddressDoesNotReceiveLocalBypass(){
        var request=request();request.setRemoteAddr("192.168.1.12");assertThrows(Exception.class,()->controller.get(request));verifyNoInteractions(store);
    }
    @Test void productionDoesNotReceiveLocalBypass(){
        var request=request();environment.setActiveProfiles("prod");assertThrows(Exception.class,()->controller.get(request));verifyNoInteractions(store);
    }
}
