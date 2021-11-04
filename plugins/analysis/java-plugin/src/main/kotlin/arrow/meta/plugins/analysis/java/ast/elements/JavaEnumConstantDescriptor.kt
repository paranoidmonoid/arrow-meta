@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package arrow.meta.plugins.analysis.java.ast.elements

import arrow.meta.plugins.analysis.java.AnalysisContext
import arrow.meta.plugins.analysis.java.ast.model
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.descriptors.ClassDescriptor
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.descriptors.ConstructorDescriptor
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.descriptors.MemberScope
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.descriptors.ReceiverParameterDescriptor
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.descriptors.TypeConstructor
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.descriptors.TypeParameterDescriptor
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.types.Type
import javax.lang.model.element.Element
import javax.lang.model.element.Modifier
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement

public class JavaEnumConstantDescriptor(
  private val ctx: AnalysisContext,
  private val impl: VariableElement
) : ClassDescriptor, JavaMemberDescriptor(ctx, impl) {

  private fun memberScope(predicate: (Element) -> Boolean): JavaMemberScope =
    JavaMemberScope(ctx, impl.enclosingElement.enclosedElements.filter(predicate))

  override val unsubstitutedMemberScope: MemberScope = memberScope {
    it !is TypeElement && !it.modifiers.contains(Modifier.STATIC)
  }
  override val staticScope: MemberScope = memberScope {
    it !is TypeElement && it.modifiers.contains(Modifier.STATIC)
  }
  override val unsubstitutedInnerClassesScope: MemberScope = memberScope { it is TypeElement }

  override val constructors: Collection<JavaConstructorDescriptor> = emptyList()

  override val kind: ClassDescriptor.ClassKind = ClassDescriptor.ClassKind.ENUM_ENTRY

  override val superTypes: Collection<Type> = listOf(impl.enclosingElement.asType().model(ctx))
  override val declaredTypeParameters: List<TypeParameterDescriptor> = emptyList()

  override val typeConstructor: TypeConstructor?
    get() = TODO("Not yet implemented")
  override val defaultType: Type?
    get() = TODO("Not yet implemented")
  override val thisAsReceiverParameter: ReceiverParameterDescriptor =
    JavaReceiverParameterDescriptor(ctx, impl.enclosingElement.asType(), impl.enclosingElement)

  override val isCompanionObject: Boolean = false
  override val isData: Boolean = false
  override val isInline: Boolean = false
  override val isFun: Boolean =
    ctx.types.isSubtype(impl.asType(), ctx.symbolTable.functionalInterfaceType)
  override val isValue: Boolean = false
  override val isEnumEntry: Boolean = false
  override val isInner: Boolean = true

  override val companionObjectDescriptor: ClassDescriptor? = null
  override val sealedSubclasses: Collection<ClassDescriptor> = emptyList()
  override val unsubstitutedPrimaryConstructor: ConstructorDescriptor? = null
}
