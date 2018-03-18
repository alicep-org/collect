package org.alicep.collect.benchmark;

import static com.google.common.base.Preconditions.checkArgument;
import static javax.tools.JavaFileObject.Kind.CLASS;
import static javax.tools.JavaFileObject.Kind.OTHER;
import static javax.tools.StandardLocation.CLASS_OUTPUT;
import static javax.tools.StandardLocation.SOURCE_OUTPUT;
import static javax.tools.ToolProvider.getSystemJavaCompiler;

import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import javax.tools.DiagnosticListener;
import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.StandardJavaFileManager;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;

class InMemoryJavaFileManager implements JavaFileManager {

  public static InMemoryJavaFileManager create(
      DiagnosticListener<? super JavaFileObject> diagnosticListener) {
    return new InMemoryJavaFileManager(
        getSystemJavaCompiler().getStandardFileManager(diagnosticListener, null, null));
  }

  private static class FileKey {
    static FileKey forClass(Location location, String className, Kind kind) {
      return new FileKey(location, className.replace('.', '/') + kind.extension);
    }

    static FileKey forFile(Location location, String packageName, String relativeName) {
      return new FileKey(location, path(packageName, relativeName));
    }

    private final Location location;
    private final String path;

    private FileKey(Location location, String path) {
      this.location = location;
      this.path = path;
    }

    public Location getKey() {
      return location;
    }

    public String getValue() {
      return path;
    }

    @Override
    public int hashCode() {
      return Objects.hash(location, path);
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof FileKey)) {
        return false;
      }
      FileKey other = (FileKey) obj;
      return Objects.equals(location, other.location)
          && Objects.equals(path, other.path);
    }
  }

  private static final Set<Kind> KINDS = ImmutableSet.of(Kind.CLASS, Kind.SOURCE);
  private static final Set<Location> LOCATIONS = ImmutableSet.of(SOURCE_OUTPUT, CLASS_OUTPUT);

  private final StandardJavaFileManager delegate;
  private final Map<FileKey, InMemoryJavaFile> javaFiles = new LinkedHashMap<>();
  private final Map<FileKey, InMemoryFile> otherFiles = new LinkedHashMap<>();

  private InMemoryJavaFileManager(StandardJavaFileManager delegate) {
    this.delegate = delegate;
  }

  @Override
  public int isSupportedOption(String option) {
    return delegate.isSupportedOption(option);
  }

  public ForkingClassLoader getForkingClassLoader(ClassLoader parent) {
    return new ForkingClassLoader(parent) {
      @Override
      protected Class<?> findClass(String name) throws ClassNotFoundException {
        InMemoryJavaFile classFile = javaFiles.get(FileKey.forClass(CLASS_OUTPUT, name, CLASS));
        if (classFile != null) {
          return super.defineClass(name, classFile.getBuffer(), null);
        }
        return super.findClass(name);
      }
    };
  }

  @Override
  public ClassLoader getClassLoader(Location location) {
    if (LOCATIONS.contains(location)) {
      return new ClassLoader() {
        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
          InMemoryJavaFile classFile = javaFiles.get(FileKey.forClass(location, name, CLASS));
          if (classFile == null) {
            throw new ClassNotFoundException();
          }
          return super.defineClass(name, classFile.getBuffer(), null);
        }
      };
    } else {
      return delegate.getClassLoader(location);
    }
  }

  @Override
  public boolean isSameFile(FileObject a, FileObject b) {
    if (a instanceof InMemoryJavaFile || b instanceof InMemoryJavaFile) {
      return a == b;
    }
    return delegate.isSameFile(a, b);
  }

  @Override
  public Iterable<JavaFileObject> list(
      Location location, String packageName, Set<Kind> kinds, boolean recurse) throws IOException {
    Iterable<JavaFileObject> delegateList = delegate.list(location, packageName, kinds, recurse);
    String directory = packageName.replace('.', '/');
    return () -> {
      Stream<JavaFileObject> inMemoryFiles = javaFiles.entrySet()
          .stream()
          .filter(e -> e.getKey().getKey().equals(location))
          .filter(e -> {
            String fileName = e.getKey().getValue();
            String fileDir = fileName.substring(0, fileName.lastIndexOf('/'));
            return recurse ? fileDir.startsWith(directory) : fileDir.equals(directory);
          })
          .filter(e -> kinds.contains(e.getValue().getKind()))
          .map(e -> e.getValue());
      return Iterators.concat(inMemoryFiles.iterator(), delegateList.iterator());
    };
  }

  @Override
  public String inferBinaryName(Location location, JavaFileObject file) {
    if (file.toUri().getScheme().equals("mem")) {
      return file.getName();
    } else {
      return delegate.inferBinaryName(location, file);
    }
  }

  @Override
  public boolean handleOption(String current, Iterator<String> remaining) {
    return delegate.handleOption(current, remaining);
  }

  @Override
  public boolean hasLocation(Location location) {
    return LOCATIONS.contains(location) || delegate.hasLocation(location);
  }

  @Override
  public JavaFileObject getJavaFileForInput(Location location, String className, Kind kind)
      throws IOException {
    if (LOCATIONS.contains(location)) {
      return javaFiles.get(FileKey.forClass(location, className, kind));
    } else {
      return delegate.getJavaFileForInput(location, className, kind);
    }
  }

  @Override
  public JavaFileObject getJavaFileForOutput(
      Location location, String className, Kind kind, FileObject sibling) {
    checkArgument(LOCATIONS.contains(location),
        "Unsupported location %s (must be one of %s)", location, LOCATIONS);
    checkArgument(KINDS.contains(kind), "Unsupported kind %s (must be one of %s)", kind, KINDS);
    return javaFiles.computeIfAbsent(
        FileKey.forClass(location, className, kind),
        k -> new InMemoryJavaFile(qualifiedName(className), kind));
  }

  private static QualifiedName qualifiedName(String className) {
    int lastPeriod = className.lastIndexOf('.');
    String packageName = lastPeriod >= 0 ? className.substring(0, lastPeriod) : "";
    String remainder = className.substring(lastPeriod + 1);
    List<String> simpleNames = Splitter.on('$').splitToList(remainder);
    return QualifiedName.of(
        packageName,
        simpleNames.get(0),
        simpleNames.subList(1, simpleNames.size()).toArray(new String[simpleNames.size() - 1]));
  }

  @Override
  public FileObject getFileForInput(Location location, String packageName, String relativeName)
      throws IOException {
    if (LOCATIONS.contains(location)) {
      Kind kind = kindFromExtension(relativeName);
      FileKey key = FileKey.forFile(location, packageName, relativeName);
      return (KINDS.contains(kind) ? javaFiles : otherFiles).get(key);
    } else {
      return delegate.getFileForInput(location, packageName, relativeName);
    }
  }

  private static String path(String packageName, String relativeName) {
    return packageName.replace('.', '/')
        + (packageName.isEmpty() ? "" : "/")
        + relativeName;
  }

  private static Kind kindFromExtension(String relativeName) {
    return Arrays.stream(Kind.values())
        .filter(k -> relativeName.endsWith(k.extension) && k != OTHER)
        .findAny()
        .orElse(OTHER);
  }

  @Override
  public FileObject getFileForOutput(
      Location location, String packageName, String relativeName, FileObject sibling) {
    checkArgument(LOCATIONS.contains(location),
        "Unsupported location %s (must be one of %s)", location, LOCATIONS);
    Kind kind = kindFromExtension(relativeName);
    FileKey key = FileKey.forFile(location, packageName, relativeName);
    if (KINDS.contains(kind)) {
      return javaFiles.computeIfAbsent(key, k -> {
        QualifiedName qualifiedName = qualifiedName(relativeName
            .substring(0, relativeName.length() - kind.extension.length())
            .replace('/', '.'));
        return new InMemoryJavaFile(qualifiedName, kind);
      });
    } else {
      return otherFiles.computeIfAbsent(key, k -> new InMemoryFile(k.getValue()));
    }
  }

  @Override
  public void flush() {}

  @Override
  public void close() {
    javaFiles.clear();
    otherFiles.clear();
    try {
      delegate.close();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
