package org.alicep.collect.benchmark;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.function.Predicate;

import net.bytebuddy.ByteBuddy;

/**
 * Clones a classloader, returning equal but not identical classes.
 *
 * <p>Gives a clean set of types for the JIT to target, avoiding cross-benchmark contamination.
 */
public class ForkingClassLoader extends ClassLoader {

  private static final String FORK_PACKAGE = "forked.";

  private static ClassLoader rootClassLoader() {
    ClassLoader classLoader = getSystemClassLoader();
    while (classLoader.getParent() != null) {
      classLoader = classLoader.getParent();
    }
    return classLoader;
  }

  private final ClassLoader original;
  private final List<Predicate<Class<?>>> corePredicates = new ArrayList<>();

  protected ForkingClassLoader(ClassLoader original) {
    super(rootClassLoader());
    this.original = original;
  }

  public ForkingClassLoader forkingCoreClassesMatching(Predicate<Class<?>> predicate) {
    corePredicates.add(predicate);
    return this;
  }

  private String rename(String cls) {
    if (cls.startsWith("java.")) {
      try {
        Class<?> outermostClass = outermostClass(cls);
        if (corePredicates.stream().anyMatch(p -> p.test(outermostClass))) {
          return FORK_PACKAGE + cls;
        }
      } catch (ClassNotFoundException e) {
        return cls;
      }
    }
    return cls;
  }

  private static Class<?> outermostClass(String cls) throws ClassNotFoundException {
    Class<?> classObject = ClassLoader.getSystemClassLoader().loadClass(cls);
    while (classObject.getEnclosingClass() != null) {
      classObject = classObject.getEnclosingClass();
    }
    return classObject;
  }

  @Override
  protected Class<?> findClass(String name) throws ClassNotFoundException {
    String originalName = name.startsWith(FORK_PACKAGE) ? name.substring(FORK_PACKAGE.length()) : name;
    byte[] bytes = new ByteBuddy()
        .redefine(original.loadClass(originalName))
        .name(name)
        .visit(new SubstituteClassReferences(this::rename))
        .make()
        .getBytes();
    return super.defineClass(name, bytes, 0, bytes.length);
  }

  @Override
  protected URL findResource(String name) {
    return original.getResource(name);
  }

  @Override
  protected Enumeration<URL> findResources(String name) throws IOException {
    return original.getResources(name);
  }
}
