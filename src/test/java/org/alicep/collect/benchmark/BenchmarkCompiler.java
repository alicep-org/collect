package org.alicep.collect.benchmark;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.reflect.Modifier.isStatic;

import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongConsumer;
import java.util.function.Predicate;

import javax.tools.DiagnosticListener;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

import com.google.common.collect.ImmutableList;

class BenchmarkCompiler {

  private static final AtomicInteger count = new AtomicInteger();

  /**
   * Returns an object that wraps a benchmark method and invokes it in a loop.
   *
   * <p>The object is freshly code-generated each call, and uses an unshared class-loader,
   * meaning any user classes will be re-JITted. Types under the java packages may still
   * cause polymorphic dispatch timing issues.
   */
  @SafeVarargs
  public static LongConsumer compileBenchmark(
      Class<?> cls,
      Method method,
      Field configurations,
      int index,
      Predicate<Class<?>>... forkingCoreClassesMatching) {
    checkArgument(cls.isAssignableFrom(method.getDeclaringClass()));
    checkArgument(isStatic(configurations.getModifiers()));
    String pkg = method.getDeclaringClass().getPackage().getName();
    if (pkg.startsWith("java.")) {
      pkg = "looper." + pkg;
    }
    String className = "Benchmark_" + count.incrementAndGet();

    String configurationName = configurations.getDeclaringClass().getName()
                + "." + configurations.getName();
    String src = "package " + pkg + ";\n"
        + "public class " + className + " implements " + LongConsumer.class.getName() + " {\n"
        + "  private final " + declaration(cls) + " test =\n"
        + "      " + construct(cls) + "(" + configurationName + ".get(" + index + "));\n"
        + "  @Override\n"
        + "  public void accept(long iterations) {\n"
        + "    for (long i = 0; i < iterations; i++) {\n"
        + "      test." + method.getName() + "();\n"
        + "    }\n"
        + "  }\n"
        + "}\n";
    ForkingClassLoader generatedClasses = compile(cls.getClassLoader(), pkg, className, src);
    Arrays.asList(forkingCoreClassesMatching).forEach(generatedClasses::forkingCoreClassesMatching);
    try {
      Class<?> generatedClass = generatedClasses.loadClass(pkg + "." + className);
      return (LongConsumer) generatedClass.newInstance();
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  private static String declaration(Class<?> cls) {
    StringBuilder declaration = new StringBuilder();
    declaration.append(cls.getName());
    if (cls.getTypeParameters().length > 0) {
      declaration.append("<?");
      for (int i = 1; i < cls.getTypeParameters().length; i++) {
        declaration.append(",?");
      }
      declaration.append(">");
    }
    return declaration.toString();
  }

  private static String construct(Class<?> cls) {
    StringBuilder construct = new StringBuilder();
    construct.append("new ").append(cls.getName());
    if (cls.getTypeParameters().length > 0) {
      construct.append("<>");
    }
    return construct.toString();
  }

  private static ForkingClassLoader compile(ClassLoader origin, String pkg, String className, String src) {
    URI uri = URI.create("temp://" + pkg.replace(".", "/") + "/" + className + ".java");
    JavaFileObject loopSource = new SourceObject(uri, Kind.SOURCE, src);
    StringWriter writer = new StringWriter();
    DiagnosticListener<? super JavaFileObject> diagnosticListener =
        diagnostic -> writer.write(diagnostic.toString() + "\n");
    InMemoryJavaFileManager fileManager = InMemoryJavaFileManager.create(diagnosticListener);
    CompilationTask task = ToolProvider.getSystemJavaCompiler().getTask(
        writer,
        fileManager,
        diagnosticListener,
        ImmutableList.of(),
        null,  // class names
        ImmutableList.of(loopSource));
    boolean compiled = task.call();
    if (!compiled) {
      String messages = writer.toString().trim();
      if (!messages.isEmpty()) {
        throw new IllegalStateException("Compilation failed:\n" + messages);
      }
      throw new IllegalStateException("Compilation failed");
    }
    return fileManager.getForkingClassLoader(origin);
  }

  private static class SourceObject extends SimpleJavaFileObject {

    private final String source;

    SourceObject(URI uri, Kind kind, String source) {
      super(uri, kind);
      this.source = source;
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
      return source;
    }
  }

  private BenchmarkCompiler() { }
}
