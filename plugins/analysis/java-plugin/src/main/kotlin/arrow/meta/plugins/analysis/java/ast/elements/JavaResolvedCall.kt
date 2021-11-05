@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package arrow.meta.plugins.analysis.java.ast.elements

import arrow.meta.plugins.analysis.java.AnalysisContext
import arrow.meta.plugins.analysis.java.ast.model
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.ResolvedCall
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.descriptors.CallableDescriptor
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.descriptors.ReceiverValue
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.descriptors.ResolvedValueArgument
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.descriptors.TypeParameterDescriptor
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.descriptors.ValueParameterDescriptor
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.elements.Element
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.types.Type
import com.sun.source.tree.Tree
import com.sun.tools.javac.code.Symbol
import com.sun.tools.javac.tree.JCTree

public class JavaResolvedCall(
  private val ctx: AnalysisContext,
  whole: Tree,
  private val method: Symbol,
  private val receiver: Tree?,
  typeArgs: List<Tree>,
  arguments: List<Tree>
) : ResolvedCall {

  override val callElement: Element = whole.model(ctx)

  override fun getReceiverExpression(): JavaElement? = receiver?.model(ctx)
  override val dispatchReceiver: ReceiverValue? =
    getReceiverExpression()?.let {
      object : ReceiverValue {
        override val type: Type = it.type()!!
      }
    }
  // there are no extension receivers in Java
  override val extensionReceiver: ReceiverValue? = null

  override val resultingDescriptor: CallableDescriptor = method.model(ctx)
  override fun getReturnType(): Type = (method as Symbol.MethodSymbol).returnType.model(ctx)

  override val typeArguments: Map<TypeParameterDescriptor, Type> =
    resultingDescriptor
      .typeParameters
      .zip(typeArgs)
      .map { (descr, tree) -> descr to ctx.resolver.resolveType(tree)!!.model(ctx) }
      .toMap()
  override val valueArguments: Map<ValueParameterDescriptor, ResolvedValueArgument> =
    resultingDescriptor
      .valueParameters
      .zip(arguments)
      .map { (descr, tree) -> descr to JavaResolvedValueArgument(ctx, tree, descr) }
      .toMap()
}

public fun Tree.resolvedCall(ctx: AnalysisContext): JavaResolvedCall? =
  when (this) {
    is JCTree.JCOperatorExpression ->
      JavaResolvedCall(ctx, this, operator, null, emptyList(), argumentsFromEverywhere)
    is JCTree.JCMethodInvocation ->
      when (val m = this.meth) {
        is JCTree.JCMemberReference ->
          JavaResolvedCall(
            ctx,
            this,
            m.sym,
            m.qualifierExpression,
            typeArguments + m.typeArguments,
            arguments
          )
        is JCTree.JCIdent -> JavaResolvedCall(ctx, this, m.sym, null, typeArguments, arguments)
        else -> null
      }
    is JCTree.JCNewClass -> JavaResolvedCall(ctx, this, constructor, null, typeArguments, arguments)
    else -> null
  }
