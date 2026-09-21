package com.cn.app.product;

import java.text.Normalizer;
import java.util.regex.Pattern;

/** Deterministic guard for explanation text; does not alter the knowledge database. */
final class ExplanationContentPolicy {
    static final String INSTRUCTION = "讲解仅围绕有依据的功能、使用方式和文化底蕴展开。不得输出任何价格、金额、交易、售后内容，亦不得以免责声明或信息缺失提示的形式提及。不得编造事实。";
    private static final String NUMBER = "[0-9零〇一二两三四五六七八九十百千万亿壹贰叁肆伍陆柒捌玖拾佰仟萬億点.,]+";
    private static final Pattern COMMERCIAL = Pattern.compile(
            "价格|價[格錢]|售价|售價|零售价|定价|标价|报价|原价|现价|价钱|价值估算|性价比|人民币|人民幣|金额|金額|"
            + "折扣|优惠|促销|满减|特价|打折|折后|售价|售后|售後|退换|退換|退货|退貨|退款|保修|保固|质保|维修|維修|"
            + "客服|服务承诺|服务保障|购买|購買|下单|订单|付款|支付|运费|運費|配送|发货|包邮|补发|赔付|"
            + "[¥￥$€£]|\\b(?:rmb|cny|usd|price|pricing|discount|refund|warranty|after[ -]?sales)\\b|"
            + NUMBER + "(?:元(?!素|代|朝|年|宵|旦|化|色)|圆整|圓整|块钱|塊錢|美元|美金|欧元|港币|港幣|折)",
            Pattern.CASE_INSENSITIVE);

    static boolean containsCommercialContent(String text) {
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKC)
                .replaceAll("[\\s\\p{Z}\\p{Cf}]+", "");
        return COMMERCIAL.matcher(normalized).find();
    }

    static String sourceForExplanation(String source) {
        // Preserve complete remaining sentences, units and decimal measurements.
        StringBuilder result = new StringBuilder();
        for (String sentence : source.split("(?<=[。！？!?；;\\n])")) {
            if (!containsCommercialContent(sentence)) result.append(sentence);
        }
        return result.toString().trim();
    }

    static void requireAllowed(String text) {
        if (containsCommercialContent(text))
            throw new ApiProblem(502, "EXPLANATION_CONTENT_REJECTED", "本次讲解包含不符合要求的内容，未保存。请重新生成以功能与文化为主的讲解。");
    }

    private ExplanationContentPolicy() {}
}
