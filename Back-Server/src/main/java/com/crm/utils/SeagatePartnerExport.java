package com.crm.utils;

import com.alibaba.excel.EasyExcel;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.Data;
import org.springframework.http.HttpRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class SeagatePartnerExport {

    // 国家映射（简化写了一点，你可以把完整的 codes/names 放进去）
    private static final Map<String, String> COUNTRY_MAP = new LinkedHashMap<>();
    static {
        COUNTRY_MAP.put("AF", "AFGHANISTAN");
        COUNTRY_MAP.put("AX", "ALAND ISLANDS");
        COUNTRY_MAP.put("AL", "ALBANIA");
        COUNTRY_MAP.put("DZ", "ALGERIA");
        COUNTRY_MAP.put("AS", "AMERICAN SAMOA");
        COUNTRY_MAP.put("AD", "ANDORRA");
        COUNTRY_MAP.put("AO", "ANGOLA");
        COUNTRY_MAP.put("AI", "ANGUILLA");
        COUNTRY_MAP.put("AQ", "ANTARCTICA");
        COUNTRY_MAP.put("AG", "ANTIGUA AND BARBUDA");
        COUNTRY_MAP.put("AR", "ARGENTINA");
        COUNTRY_MAP.put("AM", "ARMENIA");
        COUNTRY_MAP.put("AW", "ARUBA");
        COUNTRY_MAP.put("AU", "AUSTRALIA");
        COUNTRY_MAP.put("AT", "AUSTRIA");
        COUNTRY_MAP.put("AZ", "AZERBAIJAN");
        COUNTRY_MAP.put("BS", "BAHAMAS");
        COUNTRY_MAP.put("BH", "BAHRAIN");
        COUNTRY_MAP.put("BD", "BANGLADESH");
        COUNTRY_MAP.put("BB", "BARBADOS");
        COUNTRY_MAP.put("BY", "BELARUS");
        COUNTRY_MAP.put("BE", "BELGIUM");
        COUNTRY_MAP.put("BZ", "BELIZE");
        COUNTRY_MAP.put("BJ", "BENIN");
        COUNTRY_MAP.put("BM", "BERMUDA");
        COUNTRY_MAP.put("BT", "BHUTAN");
        COUNTRY_MAP.put("BO", "BOLIVIA");
        COUNTRY_MAP.put("BQ", "BONAIRE, SINT EUSTATIUS AND SABA");
        COUNTRY_MAP.put("BA", "BOSNIA AND HERZEGOVINA");
        COUNTRY_MAP.put("BW", "BOTSWANA");
        COUNTRY_MAP.put("BV", "BOUVET ISLAND");
        COUNTRY_MAP.put("BR", "BRAZIL");
        COUNTRY_MAP.put("IO", "BRITISH INDIAN OCEAN TERRITORY");
        COUNTRY_MAP.put("BN", "BRUNEI DARUSSALAM");
        COUNTRY_MAP.put("BG", "BULGARIA");
        COUNTRY_MAP.put("BF", "BURKINA FASO");
        COUNTRY_MAP.put("BI", "BURUNDI");
        COUNTRY_MAP.put("KH", "CAMBODIA");
        COUNTRY_MAP.put("CM", "CAMEROON");
        COUNTRY_MAP.put("CA", "CANADA");
        COUNTRY_MAP.put("CV", "CAPE VERDE");
        COUNTRY_MAP.put("KY", "CAYMAN ISLANDS");
        COUNTRY_MAP.put("CF", "CENTRAL AFRICAN REPUBLIC");
        COUNTRY_MAP.put("TD", "CHAD");
        COUNTRY_MAP.put("CL", "CHILE");
        COUNTRY_MAP.put("CN", "CHINA");
        COUNTRY_MAP.put("CX", "CHRISTMAS ISLAND");
        COUNTRY_MAP.put("CC", "COCOS (KEELING) ISLANDS");
        COUNTRY_MAP.put("CO", "COLOMBIA");
        COUNTRY_MAP.put("KM", "COMOROS");
        COUNTRY_MAP.put("CG", "CONGO");
        COUNTRY_MAP.put("CD", "CONGO, DEMOCRATIC REPUBLIC OF THE");
        COUNTRY_MAP.put("CK", "COOK ISLANDS");
        COUNTRY_MAP.put("CR", "COSTA RICA");
        COUNTRY_MAP.put("CI", "COTE D'IVOIRE");
        COUNTRY_MAP.put("HR", "CROATIA");
        COUNTRY_MAP.put("CU", "CUBA");
        COUNTRY_MAP.put("CW", "CURACAO");
        COUNTRY_MAP.put("CY", "CYPRUS");
        COUNTRY_MAP.put("CZ", "CZECH REPUBLIC");
        COUNTRY_MAP.put("DK", "DENMARK");
        COUNTRY_MAP.put("DJ", "DJIBOUTI");
        COUNTRY_MAP.put("DM", "DOMINICA");
        COUNTRY_MAP.put("DO", "DOMINICAN REPUBLIC");
        COUNTRY_MAP.put("EC", "ECUADOR");
        COUNTRY_MAP.put("EG", "EGYPT");
        COUNTRY_MAP.put("SV", "EL SALVADOR");
        COUNTRY_MAP.put("GQ", "EQUATORIAL GUINEA");
        COUNTRY_MAP.put("ER", "ERITREA");
        COUNTRY_MAP.put("EE", "ESTONIA");
        COUNTRY_MAP.put("ET", "ETHIOPIA");
        COUNTRY_MAP.put("FK", "FALKLAND ISLANDS (MALVINAS)");
        COUNTRY_MAP.put("FO", "FAROE ISLANDS");
        COUNTRY_MAP.put("FJ", "FIJI");
        COUNTRY_MAP.put("FI", "FINLAND");
        COUNTRY_MAP.put("FR", "FRANCE");
        COUNTRY_MAP.put("GF", "FRENCH GUIANA");
        COUNTRY_MAP.put("PF", "FRENCH POLYNESIA");
        COUNTRY_MAP.put("TF", "FRENCH SOUTHERN TERRITORIES");
        COUNTRY_MAP.put("GA", "GABON");
        COUNTRY_MAP.put("GM", "GAMBIA");
        COUNTRY_MAP.put("GE", "GEORGIA");
        COUNTRY_MAP.put("DE", "GERMANY");
        COUNTRY_MAP.put("GH", "GHANA");
        COUNTRY_MAP.put("GI", "GIBRALTAR");
        COUNTRY_MAP.put("GR", "GREECE");
        COUNTRY_MAP.put("GL", "GREENLAND");
        COUNTRY_MAP.put("GD", "GRENADA");
        COUNTRY_MAP.put("GP", "GUADELOUPE");
        COUNTRY_MAP.put("GU", "GUAM");
        COUNTRY_MAP.put("GT", "GUATEMALA");
        COUNTRY_MAP.put("GG", "GUERNSEY");
        COUNTRY_MAP.put("GN", "GUINEA");
        COUNTRY_MAP.put("GW", "GUINEA-BISSAU");
        COUNTRY_MAP.put("GY", "GUYANA");
        COUNTRY_MAP.put("HT", "HAITI");
        COUNTRY_MAP.put("HM", "HEARD ISLAND AND MCDONALD ISLANDS");
        COUNTRY_MAP.put("VA", "HOLY SEE (VATICAN CITY STATE)");
        COUNTRY_MAP.put("HN", "HONDURAS");
        COUNTRY_MAP.put("HK", "HONG KONG");
        COUNTRY_MAP.put("HU", "HUNGARY");
        COUNTRY_MAP.put("IS", "ICELAND");
        COUNTRY_MAP.put("IN", "INDIA");
        COUNTRY_MAP.put("ID", "INDONESIA");
        COUNTRY_MAP.put("IR", "IRAN, ISLAMIC REPUBLIC OF");
        COUNTRY_MAP.put("IQ", "IRAQ");
        COUNTRY_MAP.put("IE", "IRELAND");
        COUNTRY_MAP.put("IM", "ISLE OF MAN");
        COUNTRY_MAP.put("IL", "ISRAEL");
        COUNTRY_MAP.put("IT", "ITALY");
        COUNTRY_MAP.put("JM", "JAMAICA");
        COUNTRY_MAP.put("JP", "JAPAN");
        COUNTRY_MAP.put("JE", "JERSEY");
        COUNTRY_MAP.put("JO", "JORDAN");
        COUNTRY_MAP.put("KZ", "KAZAKHSTAN");
        COUNTRY_MAP.put("KE", "KENYA");
        COUNTRY_MAP.put("KI", "KIRIBATI");
        COUNTRY_MAP.put("KP", "KOREA, DEMOCRATIC PEOPLE'S REPUBLIC OF");
        COUNTRY_MAP.put("KR", "KOREA, REPUBLIC OF");
        COUNTRY_MAP.put("KW", "KUWAIT");
        COUNTRY_MAP.put("KG", "KYRGYZSTAN");
        COUNTRY_MAP.put("LA", "LAO PEOPLE'S DEMOCRATIC REPUBLIC");
        COUNTRY_MAP.put("LV", "LATVIA");
        COUNTRY_MAP.put("LB", "LEBANON");
        COUNTRY_MAP.put("LS", "LESOTHO");
        COUNTRY_MAP.put("LR", "LIBERIA");
        COUNTRY_MAP.put("LY", "LIBYA");
        COUNTRY_MAP.put("LI", "LIECHTENSTEIN");
        COUNTRY_MAP.put("LT", "LITHUANIA");
        COUNTRY_MAP.put("LU", "LUXEMBOURG");
        COUNTRY_MAP.put("MO", "MACAO");
        COUNTRY_MAP.put("MK", "MACEDONIA, THE FORMER YUGOSLAV REPUBLIC OF");
        COUNTRY_MAP.put("MG", "MADAGASCAR");
        COUNTRY_MAP.put("MW", "MALAWI");
        COUNTRY_MAP.put("MY", "MALAYSIA");
        COUNTRY_MAP.put("MV", "MALDIVES");
        COUNTRY_MAP.put("ML", "MALI");
        COUNTRY_MAP.put("MT", "MALTA");
        COUNTRY_MAP.put("MH", "MARSHALL ISLANDS");
        COUNTRY_MAP.put("MQ", "MARTINIQUE");
        COUNTRY_MAP.put("MR", "MAURITANIA");
        COUNTRY_MAP.put("MU", "MAURITIUS");
        COUNTRY_MAP.put("YT", "MAYOTTE");
        COUNTRY_MAP.put("MX", "MEXICO");
        COUNTRY_MAP.put("FM", "MICRONESIA, FEDERATED STATES OF");
        COUNTRY_MAP.put("MD", "MOLDOVA, REPUBLIC OF");
        COUNTRY_MAP.put("MC", "MONACO");
        COUNTRY_MAP.put("MN", "MONGOLIA");
        COUNTRY_MAP.put("ME", "MONTENEGRO");
        COUNTRY_MAP.put("MS", "MONTSERRAT");
        COUNTRY_MAP.put("MA", "MOROCCO");
        COUNTRY_MAP.put("MZ", "MOZAMBIQUE");
        COUNTRY_MAP.put("MM", "MYANMAR");
        COUNTRY_MAP.put("NA", "NAMIBIA");
        COUNTRY_MAP.put("NR", "NAURU");
        COUNTRY_MAP.put("NP", "NEPAL");
        COUNTRY_MAP.put("NL", "NETHERLANDS");
        COUNTRY_MAP.put("NC", "NEW CALEDONIA");
        COUNTRY_MAP.put("NZ", "NEW ZEALAND");
        COUNTRY_MAP.put("NI", "NICARAGUA");
        COUNTRY_MAP.put("NE", "NIGER");
        COUNTRY_MAP.put("NG", "NIGERIA");
        COUNTRY_MAP.put("NU", "NIUE");
        COUNTRY_MAP.put("NF", "NORFOLK ISLAND");
        COUNTRY_MAP.put("MP", "NORTHERN MARIANA ISLANDS");
        COUNTRY_MAP.put("NO", "NORWAY");
        COUNTRY_MAP.put("OM", "OMAN");
        COUNTRY_MAP.put("PK", "PAKISTAN");
        COUNTRY_MAP.put("PW", "PALAU");
        COUNTRY_MAP.put("PS", "PALESTINE, STATE OF");
        COUNTRY_MAP.put("PA", "PANAMA");
        COUNTRY_MAP.put("PG", "PAPUA NEW GUINEA");
        COUNTRY_MAP.put("PY", "PARAGUAY");
        COUNTRY_MAP.put("PE", "PERU");
        COUNTRY_MAP.put("PH", "PHILIPPINES");
        COUNTRY_MAP.put("PN", "PITCAIRN");
        COUNTRY_MAP.put("PL", "POLAND");
        COUNTRY_MAP.put("PT", "PORTUGAL");
        COUNTRY_MAP.put("PR", "PUERTO RICO");
        COUNTRY_MAP.put("QA", "QATAR");
        COUNTRY_MAP.put("RE", "REUNION");
        COUNTRY_MAP.put("RO", "ROMANIA");
        COUNTRY_MAP.put("RU", "RUSSIAN FEDERATION");
        COUNTRY_MAP.put("RW", "RWANDA");
        COUNTRY_MAP.put("BL", "SAINT BARTHELEMY");
        COUNTRY_MAP.put("SH", "SAINT HELENA, ASCENSION AND TRISTAN DA CUNHA");
        COUNTRY_MAP.put("KN", "SAINT KITTS AND NEVIS");
        COUNTRY_MAP.put("LC", "SAINT LUCIA");
        COUNTRY_MAP.put("MF", "SAINT MARTIN (FRENCH PART)");
        COUNTRY_MAP.put("PM", "SAINT PIERRE AND MIQUELON");
        COUNTRY_MAP.put("VC", "SAINT VINCENT AND THE GRENADINES");
        COUNTRY_MAP.put("WS", "SAMOA");
        COUNTRY_MAP.put("SM", "SAN MARINO");
        COUNTRY_MAP.put("ST", "SAO TOME AND PRINCIPE");
        COUNTRY_MAP.put("SA", "SAUDI ARABIA");
        COUNTRY_MAP.put("SN", "SENEGAL");
        COUNTRY_MAP.put("RS", "SERBIA");
        COUNTRY_MAP.put("SC", "SEYCHELLES");
        COUNTRY_MAP.put("SL", "SIERRA LEONE");
        COUNTRY_MAP.put("SG", "SINGAPORE");
        COUNTRY_MAP.put("SX", "SINT MAARTEN (DUTCH PART)");
        COUNTRY_MAP.put("SK", "SLOVAKIA");
        COUNTRY_MAP.put("SI", "SLOVENIA");
        COUNTRY_MAP.put("SB", "SOLOMON ISLANDS");
        COUNTRY_MAP.put("SO", "SOMALIA");
        COUNTRY_MAP.put("ZA", "SOUTH AFRICA");
        COUNTRY_MAP.put("GS", "SOUTH GEORGIA AND THE SOUTH SANDWICH ISLANDS");
        COUNTRY_MAP.put("SS", "SOUTH SUDAN");
        COUNTRY_MAP.put("ES", "SPAIN");
        COUNTRY_MAP.put("LK", "SRI LANKA");
        COUNTRY_MAP.put("SD", "SUDAN");
        COUNTRY_MAP.put("SR", "SURINAME");
        COUNTRY_MAP.put("SJ", "SVALBARD AND JAN MAYEN");
        COUNTRY_MAP.put("SZ", "SWAZILAND");
        COUNTRY_MAP.put("SE", "SWEDEN");
        COUNTRY_MAP.put("CH", "SWITZERLAND");
        COUNTRY_MAP.put("SY", "SYRIAN ARAB REPUBLIC");
        COUNTRY_MAP.put("TW", "TAIWAN, PROVINCE OF CHINA");
        COUNTRY_MAP.put("TJ", "TAJIKISTAN");
        COUNTRY_MAP.put("TZ", "TANZANIA, UNITED REPUBLIC OF");
        COUNTRY_MAP.put("TH", "THAILAND");
        COUNTRY_MAP.put("TL", "TIMOR-LESTE");
        COUNTRY_MAP.put("TG", "TOGO");
        COUNTRY_MAP.put("TK", "TOKELAU");
        COUNTRY_MAP.put("TO", "TONGA");
        COUNTRY_MAP.put("TT", "TRINIDAD AND TOBAGO");
        COUNTRY_MAP.put("TN", "TUNISIA");
        COUNTRY_MAP.put("TR", "TURKEY");
        COUNTRY_MAP.put("TM", "TURKMENISTAN");
        COUNTRY_MAP.put("TC", "TURKS AND CAICOS ISLANDS");
        COUNTRY_MAP.put("TV", "TUVALU");
        COUNTRY_MAP.put("UG", "UGANDA");
        COUNTRY_MAP.put("UA", "UKRAINE");
        COUNTRY_MAP.put("AE", "UNITED ARAB EMIRATES");
        COUNTRY_MAP.put("GB", "UNITED KINGDOM");
        COUNTRY_MAP.put("US", "UNITED STATES");
        COUNTRY_MAP.put("UM", "UNITED STATES MINOR OUTLYING ISLANDS");
        COUNTRY_MAP.put("UY", "URUGUAY");
        COUNTRY_MAP.put("UZ", "UZBEKISTAN");
        COUNTRY_MAP.put("VU", "VANUATU");
        COUNTRY_MAP.put("VE", "VENEZUELA");
        COUNTRY_MAP.put("VN", "VIET NAM");
        COUNTRY_MAP.put("VG", "VIRGIN ISLANDS, BRITISH");
        COUNTRY_MAP.put("VI", "VIRGIN ISLANDS, U.S.");
        COUNTRY_MAP.put("WF", "WALLIS AND FUTUNA");
        COUNTRY_MAP.put("EH", "WESTERN SAHARA");
        COUNTRY_MAP.put("YE", "YEMEN");
        COUNTRY_MAP.put("ZM", "ZAMBIA");
        COUNTRY_MAP.put("ZW", "ZIMBABWE");
    }


    // 代理商实体
    @Data
    public static class PartnerInfo {
        private String countryCode;
        private String countryName;
        private String name;
        private String partnerType;
        private String storageType;
        private String phone;
        private String website;
        private String logoUrl;
        private String lacieWebsite;
        private String storeSeagateUrl;
        private String storeLacieUrl;

    }

    public static void main(String[] args) {
        RestTemplate restTemplate = createRestTemplateWithHeaders();
        List<PartnerInfo> allPartners = new ArrayList<>();
        List<String> errCountryList = new ArrayList<>();
        for (Map.Entry<String, String> entry : COUNTRY_MAP.entrySet()) {
            String code = entry.getKey();
            String name = entry.getValue();
            String baseUrl = "https://www.seagate.com/ww/solrQueryResponseRetrieval/";

            String facetPartnerType = null;
            try {
                facetPartnerType = URLEncoder.encode("{!ex=partner_type_tag key=partner_type}partner_type", StandardCharsets.UTF_8.toString());
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }

            String url =
                    "https://www.seagate.com/ww/solrQueryResponseRetrieval/?q=*&omitHeader=false&collection=wtb&wt=json&indent=true&start=0&rows=3000&fl=store_seagate_url,store_lacie_url,partner_type,storage_type,name,phone,url,website,lacie_website,logo_url&sort=wtbnewsortingweigth+asc&fq=is_active:true&fq=is_wtb:true&facet.field=storage_type&facet.field=lacie_external_drives&facet.field=external_drives&facet.field=internal_drives&fq=is_seagate:true&fq=country:"
                            + code;
            try {

                Thread.sleep(1000);
                System.out.println(url);
                ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
                JSONObject root = JSON.parseObject(response.getBody());

                JSONObject responseObj = root.getJSONObject("response");
                if (responseObj != null) {
                    JSONArray docs = responseObj.getJSONArray("docs");
                    if (docs != null) {
                        System.out.println(name+"查找到客户信息 "+docs.size() +"个");
                        for (int i = 0; i < docs.size(); i++) {
                            JSONObject doc = docs.getJSONObject(i);
                            PartnerInfo partner = new PartnerInfo();
                            partner.setCountryCode(code);
                            partner.setCountryName(name);
                            partner.setName(doc.getString("name"));

                            partner.setPartnerType(doc.getJSONArray("partner_type") != null ?
                                    doc.getJSONArray("partner_type").toJSONString() : "");
                            partner.setStorageType(doc.getJSONArray("storage_type") != null ?
                                    doc.getJSONArray("storage_type").toJSONString() : "");

                            partner.setPhone(doc.getString("phone"));
                            partner.setWebsite(doc.getString("website"));
                            partner.setLogoUrl(doc.getString("logo_url"));
                            partner.setLacieWebsite(doc.getString("lacie_website"));
                            partner.setStoreSeagateUrl(doc.getString("store_seagate_url"));
                            partner.setStoreLacieUrl(doc.getString("store_lacie_url"));

                            allPartners.add(partner);
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("请求失败, 国家: " + code + " - " + name + "，错误: " + e.getMessage());
                errCountryList.add(code+" = "+name);
            }
        }
        System.out.println("查询失败的国家汇总="+JSON.toJSONString(errCountryList));
        // 输出到 Excel
        String fileName = "seagate_partners.xlsx";
        EasyExcel.write(new File(fileName), PartnerInfo.class)
                .sheet("Partners")
                .doWrite(allPartners);

        System.out.println("导出完成: " + fileName);
    }


    // 创建带有请求头的RestTemplate
    private static RestTemplate createRestTemplateWithHeaders() {
        RestTemplate restTemplate = new RestTemplate();

        // 设置请求拦截器添加头信息
        restTemplate.setRequestFactory(new HttpComponentsClientHttpRequestFactory());
        restTemplate.setInterceptors(Collections.singletonList(new ClientHttpRequestInterceptor() {
            @Override
            public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException, IOException {
                // 添加必要的请求头
                request.getHeaders().set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
                request.getHeaders().set("Accept", "application/json, text/plain, */*");
                request.getHeaders().set("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
                request.getHeaders().set("Accept-Encoding", "gzip, deflate, br");
                request.getHeaders().set("Connection", "keep-alive");
                request.getHeaders().set("Sec-Fetch-Dest", "empty");
                request.getHeaders().set("Sec-Fetch-Mode", "cors");
                request.getHeaders().set("Sec-Fetch-Site", "same-origin");
                request.getHeaders().set("Referer", "https://www.seagate.com/");

                return execution.execute(request, body);
            }
        }));

        return restTemplate;
    }

}