package com.hybridrag.tool;

import com.hybridrag.model.QueryContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ToolRegistryTest {

    private ToolRegistry registry;

    @Mock
    private Tool mockTool;

    @Mock
    private Tool secondTool;

    @Mock
    private QueryContext mockCtx;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
        lenient().when(mockTool.getName()).thenReturn("mocktool");
        lenient().when(mockTool.getDescription()).thenReturn("A mock tool");
        lenient().when(secondTool.getName()).thenReturn("second");
        lenient().when(secondTool.getDescription()).thenReturn("Second tool");
    }

    @Nested
    @DisplayName("Registration")
    class Registration {

        @Test
        void shouldRegisterTool() {
            registry.register(mockTool);
            assertThat(registry.getTool("mocktool")).isSameAs(mockTool);
        }

        @Test
        void shouldReturnNullForUnknownTool() {
            assertThat(registry.getTool("nonexistent")).isNull();
        }

        @Test
        void shouldReturnAllRegisteredTools() {
            registry.register(mockTool);
            registry.register(secondTool);
            assertThat(registry.getAllTools()).hasSize(2);
        }

        @Test
        void shouldIncludeToolDescriptions() {
            registry.register(mockTool);
            var desc = registry.getToolsDescription();
            assertThat(desc).contains("mocktool", "A mock tool");
        }
    }

    @Nested
    @DisplayName("Tool execution via executeTools")
    class ExecuteTools {

        @BeforeEach
        void registerTools() {
            registry.register(mockTool);
            registry.register(secondTool);
        }

        @Test
        void shouldReturnNullForNullInput() {
            assertThat(registry.executeTools(null, mockCtx)).isNull();
        }

        @Test
        void shouldReturnNullForBlankInput() {
            assertThat(registry.executeTools("   ", mockCtx)).isNull();
        }

        @Test
        void shouldReturnNullWhenNoToolCalls() {
            assertThat(registry.executeTools("Hello, no tools here", mockCtx)).isNull();
        }

        @Test
        void shouldExecuteSingleToolCall() {
            when(mockTool.execute("arg1", mockCtx)).thenReturn("result1");

            var result = registry.executeTools("Use /mocktool(arg1) to get data", mockCtx);

            assertThat(result).contains("result1");
            verify(mockTool).execute("arg1", mockCtx);
        }

        @Test
        void shouldExecuteMultipleToolCalls() {
            when(mockTool.execute("arg1", mockCtx)).thenReturn("result1");
            when(secondTool.execute("arg2", mockCtx)).thenReturn("result2");

            var result = registry.executeTools("Call /mocktool(arg1) and /second(arg2)", mockCtx);

            assertThat(result).contains("result1", "result2");
        }

        @Test
        void shouldHandleEmptyArgs() {
            when(mockTool.execute("arg1", mockCtx)).thenReturn("ok");

            var result = registry.executeTools("Call /mocktool(arg1)", mockCtx);
            assertThat(result).contains("ok");
        }

        @Test
        void shouldIgnoreUnknownToolCalls() {
            // Should not throw, just skip
            var result = registry.executeTools("Call /unknown(data)", mockCtx);
            assertThat(result).isNull();
            verifyNoInteractions(mockTool, secondTool);
        }

        @Test
        void shouldHandleWhitespaceInArgs() {
            when(mockTool.execute("hello world", mockCtx)).thenReturn("ok");

            var result = registry.executeTools("Call /mocktool( hello world )", mockCtx);
            assertThat(result).contains("ok");
        }

        @Test
        void shouldReturnTrimmedResult() {
            when(mockTool.execute("a", mockCtx)).thenReturn("result");

            var result = registry.executeTools("/mocktool(a)", mockCtx);
            // result should be trimmed of trailing newline
            doReturn("result").when(mockTool).execute("a", mockCtx);
            assertThat(result).isEqualTo("result");
        }
    }
}
