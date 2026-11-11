package setty.aisupport;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;

class OrderedJsonSourceTest {
    @Test void mainSourcesMustUseOrderedJsonForMapsWithMultipleEntries() throws IOException {
        var root = Path.of("src/main/java/setty/aisupport");
        List<JavaFileObject> sources = new ArrayList<>();
        try (var paths = Files.walk(root)) {
            for (var path : paths.filter(p -> p.toString().endsWith(".java")).sorted().toList())
                sources.add(source(path.toUri(), Files.readString(path)));
        }
        assertThat(sources).isNotEmpty();
        assertThat(unorderedMaps(sources))
                .as("Use OrderedJson.of instead of multi-entry Map.of to preserve JSON key order")
                .isEmpty();
    }

    @Test void detectorFindsMultilineNestedQualifiedAndGenericCalls() throws IOException {
        var code = """
                class Example {
                    Object value = Map.of("outer", Map.of(
                        "a", 1,
                        "b", 2));
                    Object qualified = java.util.Map.of("c", 3, "d", 4);
                    Object generic = Map.<String, Integer>of("e", 5, "f", 6);
                }
                """;
        assertThat(unorderedMaps(List.of(source(URI.create("string:///Example.java"), code))))
                .containsExactly("/Example.java:2", "/Example.java:5", "/Example.java:6");
    }

    @Test void emptySingleEntryMapsAndSetsAreAllowedAndCommentsAndStringsIgnored() throws IOException {
        var code = """
                class Example {
                    Object empty = Map.of();
                    Object single = Map.of("items", List.of(1, 2, 3));
                    Object nested = Map.of("outer", Map.of("inner", 1));
                    Object set = Set.of("a", "b", "c");
                    String text = "Map.of(1, 2, 3, 4)";
                    // Map.of(1, 2, 3, 4)
                    /* Map.of(1, 2, 3, 4) */
                }
                """;
        assertThat(unorderedMaps(List.of(source(URI.create("string:///Example.java"), code)))).isEmpty();
    }

    private static JavaFileObject source(URI uri, String code) {
        return new SimpleJavaFileObject(uri, JavaFileObject.Kind.SOURCE) {
            @Override public CharSequence getCharContent(boolean ignoreEncodingErrors) { return code; }
        };
    }

    private static List<String> unorderedMaps(List<JavaFileObject> sources) throws IOException {
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("Source guard requires the project's JDK").isNotNull();
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        var violations = new ArrayList<String>();
        try (var files = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            // Parse only: no annotation processing, type resolution or class-file output.
            var task = (JavacTask) compiler.getTask(null, files, diagnostics, List.of("-proc:none"), null, sources);
            var positions = Trees.instance(task).getSourcePositions();
            for (var unit : task.parse()) {
                new TreeScanner<Void, Void>() {
                    @Override public Void visitMethodInvocation(MethodInvocationTree call, Void unused) {
                        if (call.getMethodSelect() instanceof MemberSelectTree method
                                && method.getIdentifier().contentEquals("of")
                                && List.of("Map", "java.util.Map").contains(method.getExpression().toString())
                                && call.getArguments().size() >= 4) {
                            long line = unit.getLineMap().getLineNumber(positions.getStartPosition(unit, call));
                            violations.add(unit.getSourceFile().getName() + ":" + line);
                        }
                        return super.visitMethodInvocation(call, unused);
                    }
                }.scan(unit, null);
            }
            assertThat(diagnostics.getDiagnostics()).noneMatch(d -> d.getKind() == Diagnostic.Kind.ERROR);
        }
        return violations;
    }
}
