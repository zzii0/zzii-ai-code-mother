package com.nylg.zziiaicodemother.core.builder;

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

class ViteBuildErrorParserTest {

    @Test
    void parseNavBarVElseError() {
        String output = """
                vite v4.5.14 building for production...
                transforming...
                ✓ 6 modules transformed.
                ✓ built in 450ms
                [vite:vue] v-else/v-else-if has no adjacent v-if or v-else-if.

                C:/ai00/zzii-ai-code-mother/tmp/code_output/vue_project_1/src/components/NavBar.vue
                37 |            <line v-if="!menuOpen" x1="3" y1="18" x2="21" y2="18" />
                38 |            <line v-else x1="6" y1="6" x2="18" y2="18" />
                39 |            <line v-else x1="18" y1="6" x2="6" y2="18" />
                   |            ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
                40 |          </svg>
                41 |        </button>

                file: C:/ai00/zzii-ai-code-mother/tmp/code_output/vue_project_1/src/components/NavBar.vue:undefined:undefined
                error during build:
                SyntaxError: v-else/v-else-if has no adjacent v-if or v-else-if.
                """;
        File projectDir = new File("C:/ai00/zzii-ai-code-mother/tmp/code_output/vue_project_1");
        BuildResult result = ViteBuildErrorParser.parseFailure(1, output, projectDir);
        assertFalse(result.isSuccess());
        assertEquals("src/components/NavBar.vue", result.getErrorFile());
        assertEquals(39, result.getErrorLine());
        assertTrue(result.getErrorMessage().contains("v-else"));
    }

    @Test
    void buildFixPromptContainsFile() {
        BuildResult result = BuildResult.fail(1, "err", "src/App.vue", 12, "Unexpected token");
        String prompt = VueBuildFixOrchestrator.buildFixPrompt(result);
        assertTrue(prompt.contains("src/App.vue"));
        assertTrue(prompt.contains("12"));
        assertTrue(prompt.contains("Unexpected token"));
    }
}
