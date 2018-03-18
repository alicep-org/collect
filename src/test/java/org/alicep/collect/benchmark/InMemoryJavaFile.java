package org.alicep.collect.benchmark;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.tools.JavaFileObject;

import com.google.common.base.Joiner;

class InMemoryJavaFile extends InMemoryFile implements JavaFileObject {

  private final QualifiedName qualifiedName;
  private final Kind kind;

  public InMemoryJavaFile(QualifiedName qualifiedName, Kind kind) {
    super(name(qualifiedName, kind));
    this.qualifiedName = qualifiedName;
    this.kind = kind;
  }

  @Override
  public Kind getKind() {
    return kind;
  }

  @Override
  public boolean isNameCompatible(String simpleName, Kind kind) {
    return qualifiedName.getSimpleName().equals(simpleName) && this.kind == kind;
  }

  @Override
  public NestingKind getNestingKind() {
    return qualifiedName.isTopLevel() ? NestingKind.TOP_LEVEL : NestingKind.MEMBER;
  }

  @Override
  public Modifier getAccessLevel() {
    return null;
  }

  @Override
  public String toString() {
    return "InMemoryJavaFile(name=" + getName() + ")";
  }

  private static String name(QualifiedName qualifiedName, Kind kind) {
    StringBuilder name = new StringBuilder();
    if (!qualifiedName.getPackage().isEmpty()) {
      name.append(qualifiedName.getPackage().replace('.', '/')).append('/');
    }
    Joiner.on('$').appendTo(name, qualifiedName.getSimpleNames());
    name.append(kind.extension);
    return name.toString();
  }
}
