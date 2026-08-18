package com.nylg.zziiaicodemother.core.validator;

import com.nylg.zziiaicodemother.ai.model.result.HtmlCodeResult;
import com.nylg.zziiaicodemother.ai.model.result.MultiFileCodeResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NativeArtifactValidatorTest {

    private final NativeArtifactValidator validator = new NativeArtifactValidator();

    @Test
    void htmlValid() {
        HtmlCodeResult result = new HtmlCodeResult();
        result.setHtmlCode("""
                <!DOCTYPE html>
                <html>
                <head><title>Demo</title></head>
                <body><h1>Hello</h1></body>
                </html>
                """);
        ArtifactValidationResult validation = validator.validateHtml(result);
        assertTrue(validation.isValid());
    }

    @Test
    void htmlAllowsShortButValidDocument() {
        HtmlCodeResult result = new HtmlCodeResult();
        result.setHtmlCode("<html><body>ok</body></html>");
        ArtifactValidationResult validation = validator.validateHtml(result);
        assertTrue(validation.isValid());
    }

    @Test
    void htmlRejectsEmpty() {
        HtmlCodeResult result = new HtmlCodeResult();
        result.setHtmlCode("   ");
        ArtifactValidationResult validation = validator.validateHtml(result);
        assertFalse(validation.isValid());
        assertEquals("index.html", validation.firstIssue().getFileName());
    }

    @Test
    void htmlRejectsProse() {
        HtmlCodeResult result = new HtmlCodeResult();
        result.setHtmlCode("这是一个关于网站的说明，没有任何标签。");
        ArtifactValidationResult validation = validator.validateHtml(result);
        assertFalse(validation.isValid());
        assertEquals("index.html", validation.firstIssue().getFileName());
    }

    @Test
    void htmlRejectsMissingCloseTag() {
        HtmlCodeResult result = new HtmlCodeResult();
        result.setHtmlCode("""
                <!DOCTYPE html>
                <html>
                <head><title>Demo</title></head>
                <body><h1>Hello</h1></body>
                """);
        ArtifactValidationResult validation = validator.validateHtml(result);
        assertFalse(validation.isValid());
        assertTrue(validation.firstIssue().getMessage().contains("</html>"));
    }

    @Test
    void multiFileRequiresReferences() {
        MultiFileCodeResult result = new MultiFileCodeResult();
        result.setHtmlCode("""
                <!DOCTYPE html>
                <html>
                <head><title>Demo</title></head>
                <body><h1>Hello</h1></body>
                </html>
                """);
        result.setCssCode("body{margin:0}");
        result.setJsCode("1");
        ArtifactValidationResult validation = validator.validateMultiFile(result);
        assertFalse(validation.isValid());
        assertTrue(validation.getIssues().stream().anyMatch(i -> i.getMessage().contains("style.css")));
        assertTrue(validation.getIssues().stream().anyMatch(i -> i.getMessage().contains("script.js")));
    }

    @Test
    void multiFileAllowsShortNonEmptyCssJs() {
        MultiFileCodeResult result = new MultiFileCodeResult();
        result.setHtmlCode("""
                <!DOCTYPE html>
                <html>
                <head>
                  <link rel="stylesheet" href="style.css">
                  <title>Demo</title>
                </head>
                <body>
                  <h1>Hello</h1>
                  <script src="script.js"></script>
                </body>
                </html>
                """);
        result.setCssCode("a{}");
        result.setJsCode("1");
        ArtifactValidationResult validation = validator.validateMultiFile(result);
        assertTrue(validation.isValid());
    }

    @Test
    void multiFileEmptyCss() {
        MultiFileCodeResult result = new MultiFileCodeResult();
        result.setHtmlCode("""
                <!DOCTYPE html>
                <html>
                <head>
                  <link rel="stylesheet" href="style.css">
                </head>
                <body>
                  <script src="script.js"></script>
                </body>
                </html>
                """);
        result.setCssCode(" ");
        result.setJsCode("console.log(1);");
        ArtifactValidationResult validation = validator.validateMultiFile(result);
        assertFalse(validation.isValid());
        assertTrue(validation.getIssues().stream().anyMatch(i -> "style.css".equals(i.getFileName())));
    }
}
