package com.cn.app.product;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ExplanationContentPolicyTest {
    @TempDir Path directory;

    @ParameterizedTest
    @ValueSource(strings={"测试零售价为人民币六十八元。", "仅需六十八元。", "６８．００元", "售价：¥68", "六十八块钱", "壹佰贰拾捌元整", "七日内可退货。", "售后请咨询客服。", "原价不详。", "包邮送达。", "提供一年保修。", "规格、价格与售后均为演示设定。"})
    void rejectsCommerceIncludingChineseAmountsAndDisclaimers(String text) {
        assertEquals("EXPLANATION_CONTENT_REJECTED", assertThrows(ApiProblem.class,
                () -> ExplanationContentPolicy.requireAllowed(text)).errorCode);
    }

    @Test void preservesFunctionCultureAndMeasurements() {
        String text="书签适合纸质书，厚0.4毫米，重12克。以江南花窗为设计灵感。多元文化与三元色。避免弯折，用软布擦拭。";
        assertDoesNotThrow(() -> ExplanationContentPolicy.requireAllowed(text));
        assertEquals(text, ExplanationContentPolicy.sourceForExplanation(text));
        assertEquals("主体厚0.4毫米。以花窗为灵感。", ExplanationContentPolicy.sourceForExplanation(
                "主体厚0.4毫米。测试零售价：人民币68.00元。以花窗为灵感。测试售后：7日退货。"));
    }

    @Test void reloadsEditedPromptWithoutRestart() throws Exception {
        Path file=directory.resolve("prompts.yml");
        Files.writeString(file,"prompts:\n  explanation: 旧规则\n");
        PromptConfig prompts=new PromptConfig(file.toString());
        assertEquals("旧规则",prompts.explanationPrompt());
        Files.writeString(file,"prompts:\n  explanation: 功能介绍与文化底蕴\n");
        assertEquals("功能介绍与文化底蕴",prompts.explanationPrompt());
    }

    @Test void rejectsModelPriceBeforeSavingEvenWithCustomPrompt() {
        CatalogRepository catalog=mock(CatalogRepository.class);
        ProviderGateway gateway=mock(ProviderGateway.class);
        PromptConfig prompts=mock(PromptConfig.class);
        when(prompts.explanationPrompt()).thenReturn("介绍商品");
        var settings=new ModelSettingsStore(new AiProviderConfig(),new SpeechConfig(),"");
        var model=new TextModelService(settings,gateway,prompts);
        when(gateway.complete(any(),anyList())).thenReturn("书签用于标记书页。测试零售价为人民币六十八元。");
        when(catalog.product(1001)).thenReturn(Map.of("name","花窗书签"));
        when(catalog.documents(1001)).thenReturn(List.of(Map.of("name","介绍","content","用于标记书页。")));
        var service=new ProductService(catalog,model,prompts);
        assertEquals("EXPLANATION_CONTENT_REJECTED",assertThrows(ApiProblem.class,
                () -> service.generate(1001,"文化赏析","古风雅叙",60)).errorCode);
        verify(catalog,never()).saveExplanation(anyLong(),anyString(),anyString(),anyString(),anyInt(),anyList());
        verify(gateway).complete(any(),argThat(messages -> messages.get(0).get("content").contains(ExplanationContentPolicy.INSTRUCTION)));
    }
}
