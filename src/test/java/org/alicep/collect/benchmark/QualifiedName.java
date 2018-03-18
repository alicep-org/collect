package org.alicep.collect.benchmark;

import static com.google.common.collect.Iterables.getLast;

import javax.lang.model.element.TypeElement;

import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

class QualifiedName {

  /**
   * Returns a {@link QualifiedName} for a type in {@code packageName}. If {@code nestedTypes} is
   * empty, it is a top level type called {@code topLevelType}; otherwise, it is nested in that
   * type.
   */
  public static QualifiedName of(String packageName, String topLevelType, String... nestedTypes) {
    Preconditions.checkNotNull(!packageName.isEmpty());
    Preconditions.checkArgument(!topLevelType.isEmpty());
    return new QualifiedName(
        packageName, ImmutableList.<String>builder().add(topLevelType).add(nestedTypes).build());
  }

  private final String packageName;
  private final ImmutableList<String> simpleNames;

  private QualifiedName(String packageName, Iterable<String> simpleNames) {
    this.packageName = packageName;
    this.simpleNames = ImmutableList.copyOf(simpleNames);
  }

  /**
   * Returns this qualified name as a string.
   *
   * <p>Returns the same as {@link Class#getName()} and {@link TypeElement#getQualifiedName()}
   * would for the same type, e.g. "java.lang.Integer" or "com.example.OuterType.InnerType".
   */
  @Override
  public String toString() {
    return packageName + "." + Joiner.on('.').join(simpleNames);
  }

  public String getPackage() {
    return packageName;
  }

  public ImmutableList<String> getSimpleNames() {
    return simpleNames;
  }

  public String getSimpleName() {
    return getLast(simpleNames);
  }

  public boolean isTopLevel() {
    return simpleNames.size() == 1;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(packageName, simpleNames);
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof QualifiedName)) {
      return false;
    }
    QualifiedName other = (QualifiedName) obj;
    return Objects.equal(packageName, other.packageName)
        && Objects.equal(simpleNames, other.simpleNames);
  }
}
