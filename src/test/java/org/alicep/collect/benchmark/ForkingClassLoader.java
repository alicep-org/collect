package org.alicep.collect.benchmark;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;

import com.google.common.io.ByteStreams;

/**
 * Clones a classloader, returning equal but not identical classes.
 *
 * <p>Gives a clean set of types for the JIT to target, avoiding cross-benchmark contamination.
 */
class ForkingClassLoader extends ClassLoader {

  private static ClassLoader rootClassLoader() {
    ClassLoader classLoader = getSystemClassLoader();
    while (classLoader.getParent() != null) {
      classLoader = classLoader.getParent();
    }
    return classLoader;
  }

  private final ClassLoader original;

  protected ForkingClassLoader(ClassLoader original) {
    super(rootClassLoader());
    this.original = original;
  }

  @Override
  protected Class<?> findClass(String name) throws ClassNotFoundException {
    try (InputStream resource = original.getResourceAsStream(name.replace('.', '/').concat(".class"))) {
      if (resource == null) {
        throw new ClassNotFoundException(name);
      }
      byte[] bytes = ByteStreams.toByteArray(resource);
      return super.defineClass(name, bytes, 0, bytes.length);
    } catch (IOException e) {
      throw new ClassNotFoundException(name, e);
    }
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
