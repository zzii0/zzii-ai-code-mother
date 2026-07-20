package com.nylg.zziiaicodemother.core;

import com.nylg.zziiaicodemother.model.enums.CodeGenTypeEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.util.List;

import reactor.core.publisher.Flux;

@SpringBootTest
class AiCodeGeneratorFacadeTest {

    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;

    @Test
    void generateAndSaveCode() {
        File file = aiCodeGeneratorFacade.generateAndSaveCode("帮我做一个网站的登录页面，不要超过20行代码", CodeGenTypeEnum.MULTI_FILE);
        Assertions.assertNotNull(file);
    }

    @Test
    void generateAndSaveCodeStream() {
        Flux<String> result = aiCodeGeneratorFacade.generateAndSaveCodeStream("帮我做一个网站的登录页面，不要超过20行代码", CodeGenTypeEnum.MULTI_FILE);
        List<String> list = result.collectList().block();
        Assertions.assertNotNull(list);
        String content = String.join("", list);
        Assertions.assertNotNull(content);

    }
}
