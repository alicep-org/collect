package org.alicep.collect.benchmark;

import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.description.field.FieldDescription.InDefinedShape;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.Implementation.Context;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.pool.TypePool;

class SubstituteClassReferences implements AsmVisitorWrapper {

  private static final Pattern JAVA_TYPE = Pattern.compile("L[\\w/$]+");

  private final UnaryOperator<String> renameMethod;

  public SubstituteClassReferences(UnaryOperator<String> renameMethod) {
    this.renameMethod = renameMethod;
  }

  private String rename(String cls) {
    if (cls == null) {
      return cls;
    }
    return renameMethod.apply(cls.replace('/', '.')).replace('.', '/');
  }

  private String resign(String signature) {
    if (signature == null) {
      return null;
    }
    Matcher matcher = JAVA_TYPE.matcher(signature);
    StringBuffer sb = new StringBuffer();
    while (matcher.find()) {
      String replacement = "L" + rename(matcher.group().substring(1));
      matcher.appendReplacement(sb, replacement.replace("$", "\\$"));
    }
    matcher.appendTail(sb);
    return sb.toString();
  }

  @Override
  public ClassVisitor wrap(
      TypeDescription instrumentedType,
      ClassVisitor classVisitor,
      Context implementationContext,
      TypePool typePool,
      FieldList<InDefinedShape> fields,
      MethodList<?> methods,
      int writerFlags,
      int readerFlags) {
    return new ClassVisitor(Opcodes.ASM6, classVisitor) {
      @Override
      public void visit(
          int version,
          int access,
          String name,
          String signature,
          String superName,
          String[] interfaces) {
        super.visit(version, access, name, signature, rename(superName), interfaces);
      }

      @Override
      public void visitInnerClass(String name, String outerName, String innerName, int access) {
        super.visitInnerClass(name, rename(outerName), rename(innerName), access);
      }

      @Override
      public MethodVisitor visitMethod(
          int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodVisitor delegate = super.visitMethod(access, name, resign(descriptor), resign(signature), exceptions);
        return new MethodVisitor(Opcodes.ASM6, delegate) {
          @Override
          public void visitMethodInsn(
              int opcode, String owner, String name, String descriptor, boolean isInterface) {
            super.visitMethodInsn(opcode, rename(owner), name, resign(descriptor), isInterface);
          }

          @Override
          public void visitTypeInsn(int opcode, String type) {
            super.visitTypeInsn(opcode, rename(type));
          }
        };
      }
    };
  }

  @Override
  public int mergeWriter(int flags) {
    return flags;
  }

  @Override
  public int mergeReader(int flags) {
    return flags;
  }
}