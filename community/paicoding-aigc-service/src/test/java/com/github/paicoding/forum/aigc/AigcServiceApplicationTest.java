package com.github.paicoding.forum.aigc;

import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AigcServiceApplicationTest {

    @Test
    void shouldScanArticleKnowledgeIndexStateMapperInStandaloneService() {
        MapperScan mapperScan = AigcServiceApplication.class.getAnnotation(MapperScan.class);

        assertTrue(List.of(mapperScan.basePackages())
                .contains("com.github.paicoding.forum.service.ai.repository.mapper"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldImportRocketMqConfigurationForStandaloneService() throws Exception {
        try (InputStream input = new ClassPathResource("application.yml").getInputStream()) {
            Map<String, Object> root = new Yaml().load(input);
            Map<String, Object> spring = (Map<String, Object>) root.get("spring");
            Map<String, Object> config = (Map<String, Object>) spring.get("config");

            assertTrue(String.valueOf(config.get("import"))
                    .contains("application-rocketmq.yml"));
        }
    }
}
