package arrow.meta.plugins.analysis.phases.analysis.solver.search

import arrow.meta.plugins.analysis.phases.analysis.solver.RESULT_VAR_NAME
import arrow.meta.plugins.analysis.phases.analysis.solver.THIS_VAR_NAME
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.ResolutionContext
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.ResolvedCall
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.descriptors.ClassDescriptor
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.descriptors.DeclarationDescriptor
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.descriptors.withAliasUnwrapped
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.types.Type
import arrow.meta.plugins.analysis.phases.analysis.solver.collect.model.DeclarationConstraints
import arrow.meta.plugins.analysis.phases.analysis.solver.collect.model.NamedConstraint
import arrow.meta.plugins.analysis.phases.analysis.solver.isComparison
import arrow.meta.plugins.analysis.phases.analysis.solver.isCompatibleWith
import arrow.meta.plugins.analysis.phases.analysis.solver.overriddenDescriptors
import arrow.meta.plugins.analysis.phases.analysis.solver.primitiveFormula
import arrow.meta.plugins.analysis.phases.analysis.solver.renameConditions
import arrow.meta.plugins.analysis.phases.analysis.solver.state.SolverState
import arrow.meta.plugins.analysis.smt.ObjectFormula
import arrow.meta.plugins.analysis.smt.substituteObjectVariables
import arrow.meta.plugins.analysis.types.PrimitiveType
import arrow.meta.plugins.analysis.types.primitiveType
import arrow.meta.plugins.analysis.types.unwrapIfNullable
import org.sosy_lab.java_smt.api.BooleanFormula
import org.sosy_lab.java_smt.api.NumeralFormula

/**
 * Looks up in the solver state previously collected constraints and returns the constraints
 * associated to this [resolvedCall] resulting descriptor if any
 */
public fun SolverState.getConstraintsFor(resolvedCall: ResolvedCall): DeclarationConstraints? =
  getConstraintsFor(resolvedCall.resultingDescriptor)

/**
 * Looks up in the solver state previously collected constraints and returns the constraints
 * associated to this [descriptor], or any of the declaration it has overridden, if any
 */
public fun SolverState.getConstraintsFor(
  descriptor: DeclarationDescriptor
): DeclarationConstraints? =
  getImmediateConstraintsFor(descriptor) ?: getOverriddenConstraintsFor(descriptor)

/**
 * Looks up in the solver state previously collected constraints for the given [descriptor], but not
 * for their possible parents
 */
internal fun SolverState.getImmediateConstraintsFor(
  descriptor: DeclarationDescriptor
): DeclarationConstraints? =
  callableConstraints[descriptor.withAliasUnwrapped.fqNameSafe]
    ?.firstOrNull { d -> d.descriptor.isCompatibleWith(descriptor) }
    ?.takeIf { d -> d.pre.isNotEmpty() || d.post.isNotEmpty() }

/**
 * This combinator allows us to use any of the previous functions, but operating on the primary
 * constructor, from which we usually get post-conditions as invariants
 */
internal fun <A> SolverState.overType(
  f: SolverState.(DeclarationDescriptor) -> A?,
  descriptor: ClassDescriptor
): A? = descriptor.unsubstitutedPrimaryConstructor?.let { f(it) }

/**
 * This combinator allows us to use any of the previous functions, but operating on the primary
 * constructor, from which we usually get post-conditions as invariants
 */
internal fun <A> SolverState.overType(
  context: ResolutionContext,
  f: SolverState.(DeclarationDescriptor) -> A?,
  type: Type
): A? = type.descriptor?.let { overType(f, it) }

/** Obtain the invariants associated with a certain type */
internal fun SolverState.typeInvariants(
  context: ResolutionContext,
  type: Type,
  resultName: String
): List<NamedConstraint> = typeInvariants(context, type, solver.makeObjectVariable(resultName))

internal fun SolverState.typeInvariants(
  context: ResolutionContext,
  type: Type,
  result: ObjectFormula
): List<NamedConstraint> {
  val invariants =
    overType(context, { getConstraintsFor(it) }, type)?.post?.let { constraints ->
      // replace $result by new name
      constraints.map {
        NamedConstraint(
          "${it.msg} (invariant from $type)",
          solver.substituteObjectVariables(it.formula, mapOf(RESULT_VAR_NAME to result))
        )
      }
    }
      ?: emptyList()
  val isNotNull =
    if (!type.isNullable()) {
      listOf(NamedConstraint("$result is not null", solver.isNotNull(result)))
    } else {
      emptyList()
    }
  return invariants + isNotNull
}

/**
 * Looks up in the solver state previously collected constraints for every declaration the
 * [descriptor] may have overridden
 */
internal fun SolverState.getOverriddenConstraintsFor(
  descriptor: DeclarationDescriptor
): DeclarationConstraints? =
  descriptor
    .withAliasUnwrapped
    .overriddenDescriptors()
    ?.mapNotNull { overriddenDescriptor -> getConstraintsFor(overriddenDescriptor) }
    ?.takeIf { it.isNotEmpty() }
    ?.map {
      // rename the argument names and similar things
      solver.renameConditions(it, descriptor)
    }
    ?.let { overriddenConstraints ->
      // and finally put all the pre- and post-conditions together
      DeclarationConstraints(
        descriptor,
        overriddenConstraints.flatMap { it.pre },
        overriddenConstraints.flatMap { it.post }
      )
    }

/** Special primitive constraints */
internal fun SolverState.primitiveConstraints(
  context: ResolutionContext,
  call: ResolvedCall,
): DeclarationConstraints? {
  val descriptor = call.resultingDescriptor
  val returnTy =
    if (descriptor.isComparison()) PrimitiveType.BOOLEAN
    else descriptor.returnType?.unwrapIfNullable()?.primitiveType()
  val dispatch =
    listOfNotNull(descriptor.extensionReceiverParameter, descriptor.dispatchReceiverParameter)
      .map { param ->
        param.type.unwrapIfNullable().primitiveType()?.let { ty -> Pair(THIS_VAR_NAME, ty) }
      }
  val argTys =
    dispatch +
      descriptor.valueParameters.map { param ->
        param.type.unwrapIfNullable().primitiveType()?.let { ty -> Pair(param.name.value, ty) }
      }
  return if (returnTy == null || argTys.any { it == null }) {
    null
  } else {
    val innerArgs =
      argTys.map {
        val name = it!!.first
        when (it.second) {
          PrimitiveType.BOOLEAN -> solver.makeBooleanObjectVariable(name)
          PrimitiveType.INTEGRAL -> solver.makeIntegerObjectVariable(name)
          PrimitiveType.RATIONAL -> solver.makeDecimalObjectVariable(name)
          else -> solver.makeObjectVariable(name)
        }
      }
    solver.primitiveFormula(context, call, innerArgs)?.let { formula ->
      when (returnTy) {
        PrimitiveType.BOOLEAN ->
          solver.booleans {
            equivalence(
              solver.makeBooleanObjectVariable(RESULT_VAR_NAME),
              formula as BooleanFormula
            )
          }
        PrimitiveType.INTEGRAL ->
          solver.ints {
            equal(
              solver.makeIntegerObjectVariable(RESULT_VAR_NAME),
              formula as NumeralFormula.IntegerFormula
            )
          }
        PrimitiveType.RATIONAL ->
          solver.rationals {
            equal(
              solver.makeIntegerObjectVariable(RESULT_VAR_NAME),
              formula as NumeralFormula.RationalFormula
            )
          }
        else -> solver.objects { equal(solver.resultVariable, formula as ObjectFormula) }
      }?.let {
        val named = NamedConstraint("checkCallArguments(${descriptor.fqNameSafe})", it)
        DeclarationConstraints(descriptor, emptyList(), listOf(named))
      }
    }
  }
}
