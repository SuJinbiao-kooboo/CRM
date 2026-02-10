package com.ruoyi.web.tmp;

import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

public class NvidiaPartnerInfoClient {

    private static final String URL =
            "https://api.store.nvidia.com/products/v1/partner-info" +
                    "?pn=turing-intelligent-computing-guangzhou-co-ltd";

    public static void main(String[] args) {
        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT,
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                        "AppleWebKit/537.36 (KHTML, like Gecko) " +
                        "Chrome/144.0.0.0 Safari/537.36");

        headers.set(HttpHeaders.ORIGIN, "https://marketplace.nvidia.com");
        headers.set(HttpHeaders.REFERER, "https://marketplace.nvidia.com/");
        headers.set(HttpHeaders.ACCEPT, "application/json, text/plain, */*");
        headers.set(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9");
        headers.set("authority", "api.store.nvidia.com");
        headers.set("User-Agent", "PostmanRuntime/7.51.1");

        // ⚠️ 关键：Cookie（直接从 Postman / 浏览器复制）
        headers.set(HttpHeaders.COOKIE,
                "abck=E90767F14B1B8E7A01A10F90BFC1EEE3~-1~YAAQP/7DF87JbR+cAQAAy3wyRQ+19T9R/rV9uUd0Zw3qdXKmM+vHDYV1ewdMWJEHfDQd8RJlnrvg5uo4ZJnIcgG9n/rvSFH1IgfDvZdWzjJlgU2qQ+I1JXsENJmwgu/Ui8zYQm25+OCjjZEIo5Gu54Lwvm74lB47MuNLOEOpKx75VEdOiVVQIBiR32IDTdlEiTvS+oKJx0TSqAhoNC+a2RGAZvnt/sir7yAbYx1TAkTbsz3OCxcS5QxhRZHVjs75oGhT+GL9PkeducKgfrE+wIlMPmxLMzUDFeh4U4J+B8/kTfxCKGb+c9osoCuN/wU/oH7ZBIoEWXcdMkucmO4Mlm8kyqAy3ZZAAmvWMVFECNlzsXJvof84I5HgABy7EhYkyd/mowQrastDLugTq5+QwWEwlMHWZIbpW/dIG7e6fbqD/MLRMxGutEE+F8iVQwKZjp/cfwqgp87GCjHNxATz/B+h2sHqBg==~-1~-1~-1~-1~-1; bm_sz=407F1ABB173D6CBD4114487320DF51D2~YAAQP/7DF9DJbR+cAQAAzHwyRR74VQq0dBZwlfMJ4m+j8zoV/w8xnwZAoeMSgW0p5KcwRuUmMcO2yCtL0v0iPymt0LLuqu95msv90V0WBo3QDD4FC9GcCO66rellaS7K9mFK+vAxKZSWQfnUQwoRnnO+9VLQIvmgUvKgrwoniOPkEhuz+nQk4xMdnewqdZyGbkVImF5pOqLpjvCu1lwxqZ8I28SOXP1gHysd998GxKN4KvYV4WIJH1EW99pFRE0x4nGw7KC8jR3nHtFsEllT901Q4zIBn7z2xYHiPHG7r+tleGAu0qXFMXnwBgDdw0YcQmWVtLvIytY120vMuWrcDaAAIOkRggAjeAtHmw==~3555897~3160112; ak_bmsc=3C51945E87EEDCBCFB1CDACE01ADCA29~000000000000000000000000000000~YAAQP/7DF8/JbR+cAQAAy3wyRR4bRO9Pyo4bcmSdJA1Z3MC79F34REtyD7xVQvK134Ts7bbVPW/yLrPAaDEr6hU8anrZaenPuLpFXuSx8g666MmNR2kplXmiNo3F+/pkV1Zu1WeVhTVtwZa1HGtF3uFKnJofM5QL140OSKFKtKqP0yjxTmOrLo3o5LBHrX77qrtt5VaIY9EYIG/4nZYScpxztLjdfSvlp36kfILQDUW9NjVah9curmLWzVzz82vhBJxcfA6oFUaMk4RgBxjXupwzPc5pQnj5y27iMhqzwvzpZGpTau+NcQiX+H2lC2ZXNvAEv3Wi5+Dl3cWlHNDrOHSMCiSUEtlXlNgXhYV1RvIivSo="
        );

        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                URL,
                HttpMethod.GET,
                requestEntity,
                String.class
        );

        System.out.println("HTTP Status: " + response.getStatusCode());
        System.out.println("Response Body:");
        System.out.println(response.getBody());
    }
}
