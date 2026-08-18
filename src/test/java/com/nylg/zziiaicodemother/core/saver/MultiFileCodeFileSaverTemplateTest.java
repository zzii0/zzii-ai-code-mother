package com.nylg.zziiaicodemother.core.saver;

import com.nylg.zziiaicodemother.ai.model.result.MultiFileCodeResult;
import com.nylg.zziiaicodemother.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MultiFileCodeFileSaverTemplateTest {

    private final MultiFileCodeFileSaverTemplate saver = new MultiFileCodeFileSaverTemplate();

    @Test
    void validateInput_shouldRejectMissingCssOrJs() {
        MultiFileCodeResult onlyHtml = new MultiFileCodeResult();
        onlyHtml.setHtmlCode("<html></html>");
        assertThrows(BusinessException.class, () -> saver.validateInput(onlyHtml));

        MultiFileCodeResult htmlAndCss = new MultiFileCodeResult();
        htmlAndCss.setHtmlCode("<html></html>");
        htmlAndCss.setCssCode("body{}");
        assertThrows(BusinessException.class, () -> saver.validateInput(htmlAndCss));
    }

    @Test
    void validateInput_shouldAcceptCompleteThreeFiles() {
        MultiFileCodeResult complete = new MultiFileCodeResult();
        complete.setHtmlCode("<html></html>");
        complete.setCssCode("body{}");
        complete.setJsCode("console.log(1);");
        assertDoesNotThrow(() -> saver.validateInput(complete));
    }
}
