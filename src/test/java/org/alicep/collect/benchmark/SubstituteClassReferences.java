package org.alicep.collect.benchmark;

import java.util.Arrays;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.description.field.FieldDescription.InDefinedShape;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.Implementation.Context;
import net.bytebuddy.jar.asm.AnnotationVisitor;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.FieldVisitor;
import net.bytebuddy.jar.asm.Handle;
import net.bytebuddy.jar.asm.Label;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.jar.asm.Type;
import net.bytebuddy.jar.asm.TypePath;
import net.bytebuddy.pool.TypePool;

class SubstituteClassReferences implements AsmVisitorWrapper {

  private static final Pattern JAVA_TYPE = Pattern.compile("[\\w/$]+");
  private static final Pattern NESTED_TYPE = Pattern.compile("L[\\w/$]+");

  private final UnaryOperator<String> renameMethod;

  public SubstituteClassReferences(UnaryOperator<String> renameMethod) {
    this.renameMethod = renameMethod;
  }

  private String rename(String cls) {
    if (cls == null) {
      return cls;
    } else if (JAVA_TYPE.matcher(cls).matches()) {
      return renameMethod.apply(cls.replace('/', '.')).replace('.', '/');
    } else {
      Matcher matcher = NESTED_TYPE.matcher(cls);
      StringBuffer sb = new StringBuffer();
      while (matcher.find()) {
        String replacement = "L" + rename(matcher.group().substring(1));
        matcher.appendReplacement(sb, replacement.replace("$", "\\$"));
      }
      matcher.appendTail(sb);
      return sb.toString();
    }
  }

  private Type rename(Type type) {
    return Type.getType(rename(type.getDescriptor()));
  }

  private Handle rename(Handle handle) {
    return new Handle(
        handle.getTag(),
        rename(handle.getOwner()),
        handle.getName(),
        rename(handle.getDesc()),
        handle.isInterface());
  }

  @SuppressWarnings("unchecked")
  private <T> T[] rename(T[] array) {
    if (array == null) {
      return array;
    }
    T[] renamed = Arrays.copyOf(array, array.length);
    for (int i = 0; i < renamed.length; ++i) {
      if (renamed[i] instanceof String) {
        renamed[i] = (T) rename((String) renamed[i]);
      } else if (renamed[i] instanceof Type) {
        renamed[i] = (T) rename((Type) renamed[i]);
      } else if (renamed[i] instanceof Handle) {
        renamed[i] = (T) rename((Handle) renamed[i]);
      }
    }
    return renamed;
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
        super.visit(version, access, name, rename(signature), rename(superName), rename(interfaces));
      }

      @Override
      public void visitInnerClass(String name, String outerName, String innerName, int access) {
        super.visitInnerClass(rename(name), rename(outerName), innerName, access);
      }

      @Override
      public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        return super.visitField(access, name, rename(descriptor), rename(signature), value);
      }

      @Override
      public void visitOuterClass(String owner, String name, String descriptor) {
        super.visitOuterClass(owner, name, rename(descriptor));
      }

      @Override
      public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
        return super.visitTypeAnnotation(typeRef, typePath, rename(descriptor), visible);
      }

      @Override
      public MethodVisitor visitMethod(
          int access, String methodName, String descriptor, String signature, String[] exceptions) {
        MethodVisitor delegate = super.visitMethod(
            access, methodName, rename(descriptor), rename(signature), rename(exceptions));
        return new MethodVisitor(Opcodes.ASM6, delegate) {

          @Override
          public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            return super.visitAnnotation(rename(descriptor), visible);
          }

          @Override
          public void visitFieldInsn(int opcode, String owner, String fieldName, String descriptor) {
            super.visitFieldInsn(opcode, rename(owner), fieldName, rename(descriptor));
          }

          @Override
          public void visitFrame(int type, int nLocal, Object[] local, int nStack, Object[] stack) {
            super.visitFrame(type, nLocal, rename(local), nStack, rename(stack));
          }

          @Override
          public AnnotationVisitor visitInsnAnnotation(
              int typeRef,
              TypePath typePath,
              String descriptor,
              boolean visible) {
            return super.visitInsnAnnotation(typeRef, typePath, rename(descriptor), visible);
          }

          @Override
          public void visitInvokeDynamicInsn(
              String name,
              String descriptor,
              Handle bootstrapMethodHandle,
              Object... bootstrapMethodArguments) {
            super.visitInvokeDynamicInsn(
                name, rename(descriptor), rename(bootstrapMethodHandle), rename(bootstrapMethodArguments));
          }

          @Override
          public void visitLocalVariable(
              String variableName,
              String descriptor,
              String signature,
              Label start,
              Label end,
              int index) {
            super.visitLocalVariable(variableName, rename(descriptor), rename(signature), start, end, index);
          }

          @Override
          public AnnotationVisitor visitLocalVariableAnnotation(
              int typeRef,
              TypePath typePath,
              Label[] start,
              Label[] end,
              int[] index,
              String descriptor,
              boolean visible) {
            return super.visitLocalVariableAnnotation(
                typeRef, typePath, start, end, index, rename(descriptor), visible);
          }

          @Deprecated
          @Override
          public void visitMethodInsn(int opcode, String owner, String name, String descriptor) {
            super.visitMethodInsn(opcode, owner, name, rename(descriptor));
          }

          @Override
          public void visitMethodInsn(
              int opcode, String owner, String name, String descriptor, boolean isInterface) {
            super.visitMethodInsn(opcode, rename(owner), name, rename(descriptor), isInterface);
          }

          @Override
          public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
            super.visitMultiANewArrayInsn(rename(descriptor), numDimensions);
          }

          @Override
          public AnnotationVisitor visitParameterAnnotation(int parameter, String descriptor, boolean visible) {
            return super.visitParameterAnnotation(parameter, rename(descriptor), visible);
          }

          @Override
          public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
            super.visitTryCatchBlock(start, end, handler, rename(type));
          }

          @Override
          public AnnotationVisitor visitTryCatchAnnotation(
              int typeRef,
              TypePath typePath,
              String descriptor,
              boolean visible) {
            return super.visitTryCatchAnnotation(typeRef, typePath, rename(descriptor), visible);
          }

          @Override
          public AnnotationVisitor visitTypeAnnotation(
              int typeRef,
              TypePath typePath,
              String descriptor,
              boolean visible) {
            return super.visitTypeAnnotation(typeRef, typePath, rename(descriptor), visible);
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