package org.bovinegenius.rygg.jil

import java.util.Arrays

import org.objectweb.asm.Opcodes
import org.objectweb.asm.{Type => AsmType}

case class PackageName(val name: String) {
  val bytecodeName: String = name.replace(".", "/")
}

case class ClassName(val packageName: PackageName, val name: String) {
  val bytecodeName: String = s"${packageName.bytecodeName}/${name}"
  val classpath: String = s"${bytecodeName}.class"
  val prettyName: String = s"${packageName.name}.${name}"
  def apply(memberName: String): MemberName = MemberName(this, memberName)
}
object ClassName {
  def apply(qualifiedName: String): ClassName = {
    val parts: Array[String] = qualifiedName.split("\\.")
    val partsStr = Arrays.toString(parts.asInstanceOf[Array[Object]])
    val packageName = PackageName(parts.slice(0, parts.length - 1).mkString("."))
    ClassName(packageName, parts.last)
  }
}

case class MemberName(val className: ClassName, val name: String) {
  def asMethodName: MethodName = MethodName(className, name)
  def asFieldName: FieldName = FieldName(className, name)
}

case class InternalName(parts: Array[String]) {
  val className: ClassName = {
    val packageName: PackageName = PackageName(parts.slice(0, parts.length - 1).mkString("."))
    ClassName(packageName, parts.last)
  }
}
object InternalName {
  def apply(name: String): InternalName = {
    InternalName(name.split("/"))
  }
}

sealed trait AccessLevel {
  def prettyName: String
}
case object Public extends AccessLevel { val prettyName: String = "public" }
case object Private extends AccessLevel { val prettyName: String = "private" }
case object Protected extends AccessLevel { val prettyName: String = "protected" }
case object Package extends AccessLevel { val prettyName: String = "package" }

object AccessLevel {
  def apply(flags: AccessFlags): AccessLevel = {
    if (flags.isSet(Opcodes.ACC_PUBLIC)) {
      Public
    } else if (flags.isSet(Opcodes.ACC_PRIVATE)) {
      Private
    } else if (flags.isSet(Opcodes.ACC_PROTECTED)) {
      Protected
    } else {
      Package
    }
  }
}

sealed trait Staticness {
  def isStatic: Boolean
}
case object Static extends Staticness {
  override val isStatic: Boolean = true
}
case object NonStatic extends Staticness {
  override val isStatic: Boolean = false
}
object Staticness {
  def apply(static: Boolean): Staticness =
    if (static) {
      Static
    } else {
      NonStatic
    }
}

sealed trait Finalness {
  def isFinal: Boolean
}
case object Final extends Finalness {
  override val isFinal: Boolean = true
}
case object NonFinal extends Finalness {
  override val isFinal: Boolean = false
}
object Finalness {
  def apply(isFinal: Boolean): Finalness =
    if (isFinal) {
      Final
    } else {
      NonFinal
    }
}

sealed trait Classy {
  def className: ClassName
  def pretty: String
  def field(fieldName: FieldName): Option[Field]
  def methodSignatures: List[MethodSignature]
  def resolveMethod(name: MethodName, argTypes: List[Type]): Option[MethodSignature] = {
    methodSignatures.view.filter(s => methodMatches(name, argTypes, s)).headOption
  }

  private def methodMatches(name: MethodName, argTypes: List[Type], sig: MethodSignature): Boolean = {
    sig.name == name &&
    sig.args.length == argTypes.length &&
    sig.args.map(a => a.argType).zip(argTypes).forall(p => assignable(p._2, p._1))
  }

  // Kept simple for now
  private def assignable(inputType: Type, slot: Type): Boolean = {
    inputType.sameAs(slot) ||
    inputType.bytecodeType.sameAs(slot)
  }
}
case class Class(val sourceFile: String, val access: AccessLevel, val classType: ClassType, val fields: List[Field], val methods: List[Method], val interfaces: List[ClassName]) extends Classy {
  val className: ClassName = classType.name
  val pretty: String = {
    val prettyFields = fields.map(_.pretty).mkString(";\n  ")
    val prettyMethods = methods.map(_.signature.pretty).mkString(";\n  ")
    s"${access.prettyName} class ${classType.prettyName} {\n  ${prettyFields}\n  ${prettyMethods}\n}"
  }
  def field(name: FieldName): Option[Field] = fields.view.filter(f => f.name == name).headOption
  lazy val methodSignatures: List[MethodSignature] = methods.map(_.signature)
}
case class Interface(val sourceFile: String, val access: AccessLevel, val classType: ClassType, val methods: List[MethodSignature]) extends Classy{
  val className: ClassName = classType.name
  val pretty: String = {
    val prettyMethods = methods.map(_.pretty).mkString(";\n  ")
    s"${access.prettyName} interface ${classType.prettyName} {\n  ${prettyMethods}\n}"
  }
  val methodSignatures: List[MethodSignature] = methods
  // For now, return None
  def field(name: FieldName): Option[Field] = None
}

case class FieldName(val className: ClassName, val name: String) {
  val prettyName: String = s"${className.prettyName}.${name}"
}
object FieldName {
  def apply(qualifiedName: String): FieldName = {
    val parts = qualifiedName.split("\\.")
    val className = ClassName(parts.slice(0, parts.length - 1).mkString("."))
    FieldName(className, parts(parts.length - 1))
  }
}

case class MethodName(val className: ClassName, val name: String) {
  val prettyName: String = s"${className.prettyName}.${name}"
}
object MethodName {
  def apply(qualifiedName: String): MethodName = {
    val parts = qualifiedName.split("\\.")
    val className = ClassName(parts.slice(0, parts.length - 1).mkString("."))
    MethodName(className, parts(parts.length - 1))
  }
}

case class LocalVariableName(name: String)
case class LocalVariable(name: LocalVariableName, varType: Type)

case class Field(val name: FieldName, val access: AccessLevel, val isFinal: Boolean, val static: Boolean, val fieldType: Type) {
  val pretty: String = {
    val staticStr = if (static) " static" else ""
    val finalStr = if(isFinal) " final" else ""
    s"${access.prettyName}${staticStr}${finalStr} ${fieldType.prettyName} ${name.prettyName}"
  }
}

case class Arg(val name: String, val argType: Type)
object Arg {
  implicit def pair2Arg(pair: (String,Type)): Arg = Arg(pair._1, pair._2)
}
case class MethodSignature(val name: MethodName, val access: AccessLevel, val static: Boolean, val returnType: Type, val args: List[Arg]) {
  val pretty: String = {
    val staticStr = if (static) " static" else ""
    val argsStr = args.map(arg => s"${arg.argType.prettyName} ${arg.name}").mkString(", ")
    s"${access.prettyName}${staticStr} ${returnType.prettyName} ${name.prettyName}(${argsStr})"
  }
}
case class Method(val signature: MethodSignature, val bodyThunk: Option[() => Expression]) {
  lazy val body: Option[Expression] = bodyThunk.map(_.apply())
}
object Method {
  def apply(signature: MethodSignature)(body: () => Expression): Method = Method(signature, Some(body))
}
sealed trait Expression {
  def expressionType: Type
}
//case class StaticMethodCall(val signature: MethodSignature, val args: List[Expression]) extends Expression {
//  val expressionType: Type = signature.returnType
//}
case class StaticFieldAccess(val fieldName: FieldName, val fieldType: Type) extends Expression {
  val expressionType: Type = fieldType
}
case class FieldAccess(val obj: Expression, val fieldName: FieldName, val fieldType: Type) extends Expression {
  val expressionType: Type = fieldType
}
case class AccessThis(val classType: ClassType) extends Expression {
  val expressionType: Type = classType
}
case class AccessArgument(val index: Int, val argType: Type) extends Expression {
  val expressionType: Type = argType
}
case class SetField(val obj: Expression, val fieldName: FieldName, val value: Expression) extends Expression {
  val expressionType: Type = value.expressionType
}
case class New(val classType: ClassType, args: List[Expression]) extends Expression {
  val expressionType: Type = classType
}
case class VirtualMethodCall(val obj: Expression, val signature: MethodSignature, val args: List[Expression]) extends Expression {
  val expressionType: Type = signature.returnType
}
case class InvokeStatic(val classType: ClassType, val signature: MethodSignature, val args: List[Expression]) extends Expression {
  val expressionType: Type = signature.returnType
}
case class InvokeSpecial(val obj: Expression, val signature: MethodSignature, val args: List[Expression]) extends Expression {
  val expressionType: Type = signature.returnType
}
case class StringLiteral(val value: String) extends Expression {
  val expressionType: Type = ClassType(ClassName(PackageName("java.lang"), "String"))
}
case class IntLiteral(val value: Int) extends Expression {
  val expressionType: Type = IntType
}
case class CharLiteral(val value: Char) extends Expression {
  val expressionType: Type = CharType
}
case class ShortLiteral(val value: Short) extends Expression {
  val expressionType: Type = ShortType
}
case class LongLiteral(val value: Long) extends Expression {
  val expressionType: Type = LongType
}
case class BooleanLiteral(val value: Boolean) extends Expression {
  val expressionType: Type = BooleanType
}
case class Sequence(expr1: Expression, expr2: Expression) extends Expression {
  val expressionType: Type = expr2.expressionType
}
case class Let(name: LocalVariable, value: Expression, body: Expression) extends Expression {
  val expressionType: Type = body.expressionType
}
case class LocalVariableLookup(variable: LocalVariable) extends Expression {
  val expressionType: Type = variable.varType
}

sealed trait Type {
  def bytecodeName: String
  def prettyName: String
  def boxed: Type
  def sameAs(other: Type): Boolean
  def stackSize: Int
  def primitive: Boolean
  def bytecodeType: Type
}
object Type {
  implicit def fromAsmType(asmType: AsmType): Type = {
    asmType.getSort match {
      case AsmType.ARRAY => ArrayType(fromAsmType(asmType.getElementType))
      case AsmType.BOOLEAN => BooleanType
      case AsmType.BYTE => ByteType
      case AsmType.CHAR => CharType
      case AsmType.DOUBLE => DoubleType
      case AsmType.FLOAT => FloatType
      case AsmType.INT => IntType
      case AsmType.LONG => LongType
      case AsmType.METHOD => throw new RuntimeException(s"Method types are currently unsupported: ${asmType}")
      case AsmType.OBJECT => ClassType(InternalName(asmType.getInternalName).className)
      case AsmType.SHORT => ShortType
      case AsmType.VOID => VoidType
      case _ => throw new RuntimeException(s"Unknown type: ${asmType}")
    }
  }
}
case class ClassType(val name: ClassName) extends Type {
  val bytecodeName: String = name.bytecodeName
  val prettyName: String = name.prettyName
  def sameAs(otherType: Type): Boolean = this == otherType
  val boxed: Type = this
  val stackSize: Int = 1
  val primitive = false
  val bytecodeType: Type = this
}
object ClassType {
  def apply(qualifiedName: String): ClassType = ClassType(ClassName(qualifiedName))
}

case class ArrayType(val innerType: Type) extends Type {
  val bytecodeName: String = s"[${innerType.bytecodeName}"
  val prettyName: String = s"${innerType.prettyName}[]"
  def sameAs(otherType: Type): Boolean = otherType match {
    case ArrayType(inner) => inner.sameAs(innerType)
    case _ => false
  }
  val boxed: Type = this
  val stackSize: Int = 1
  val primitive = false
  val bytecodeType: Type = this
}
object ArrayType {
  def apply(qualifiedName: String): ArrayType = ArrayType(ClassType(qualifiedName))
}
case object VoidType extends Type {
  val name: String = "void"
  val bytecodeName: String = name;
  val prettyName: String = name;
  val boxed: Type = ClassType(ClassName("java.lang.Void"))
  def sameAs(otherType: Type): Boolean = this == otherType || boxed == otherType.boxed
  val stackSize: Int = 0
  val primitive = true
  val bytecodeType: Type = this
}
case object IntType extends Type {
  val name: String = "int"
  val bytecodeName: String = name;
  val prettyName: String = name;
  val boxed: Type = ClassType(ClassName("java.lang.Integer"))
  def sameAs(otherType: Type): Boolean = this == otherType || boxed == otherType.boxed
  val stackSize: Int = 1
  val primitive = true
  val bytecodeType: Type = IntType
}
case object ByteType extends Type {
  val name: String = "byte"
  val bytecodeName: String = name;
  val prettyName: String = name;
  val boxed: Type = ClassType(ClassName("java.lang.Byte"))
  def sameAs(otherType: Type): Boolean = this == otherType || boxed == otherType.boxed
  val stackSize: Int = 1
  val primitive = true
  val bytecodeType: Type = IntType
}
case object ShortType extends Type {
  val name: String = "short"
  val bytecodeName: String = name;
  val prettyName: String = name;
  val boxed: Type = ClassType(ClassName("java.lang.Short"))
  def sameAs(otherType: Type): Boolean = this == otherType || boxed == otherType.boxed
  val stackSize: Int = 1
  val primitive = true
  val bytecodeType: Type = IntType
}
case object LongType extends Type {
  val name: String = "long"
  val bytecodeName: String = name;
  val prettyName: String = name;
  val boxed: Type = ClassType(ClassName("java.lang.Long"))
  def sameAs(otherType: Type): Boolean = this == otherType || boxed == otherType.boxed
  val stackSize: Int = 2
  val primitive = true
  val bytecodeType: Type = this
}
case object FloatType extends Type {
  val name: String = "float"
  val bytecodeName: String = name;
  val prettyName: String = name;
  val boxed: Type = ClassType(ClassName("java.lang.Float"))
  def sameAs(otherType: Type): Boolean = this == otherType || boxed == otherType.boxed
  val stackSize: Int = 1
  val primitive = true
  val bytecodeType: Type = this
}
case object DoubleType extends Type {
  val name: String = "double"
  val bytecodeName: String = name;
  val prettyName: String = name;
  val boxed: Type = ClassType(ClassName("java.lang.Double"))
  def sameAs(otherType: Type): Boolean = this == otherType || boxed == otherType.boxed
  val stackSize: Int = 2
  val primitive = true
  val bytecodeType: Type = this
}
case object BooleanType extends Type {
  val name: String = "boolean"
  val bytecodeName: String = name;
  val prettyName: String = name;
  val boxed: Type = ClassType(ClassName("java.lang.Boolean"))
  def sameAs(otherType: Type): Boolean = this == otherType || boxed == otherType.boxed
  val stackSize: Int = 1
  val primitive = true
  val bytecodeType: Type = IntType
}
case object CharType extends Type {
  val name: String = "char"
  val bytecodeName: String = name;
  val prettyName: String = name;
  val boxed: Type = ClassType(ClassName("java.lang.Character"))
  def sameAs(otherType: Type): Boolean = this == otherType || boxed == otherType.boxed
  val stackSize: Int = 1
  val primitive = true
  val bytecodeType: Type = this
}
