/*
 * Copyright 2017 Magnus Madsen
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ca.uwaterloo.flix.language.phase

import ca.uwaterloo.flix.api.Flix
import ca.uwaterloo.flix.language.CompilationError
import ca.uwaterloo.flix.language.ast.TypedAst._
import ca.uwaterloo.flix.language.ast._
import ca.uwaterloo.flix.language.ast.ops.TypedAstOps._
import ca.uwaterloo.flix.language.phase.unification.Unification
import ca.uwaterloo.flix.util.Validation._
import ca.uwaterloo.flix.util.{InternalCompilerException, Validation}

import scala.collection.mutable

/**
  * This phase generates function definitions for equality and toString on enums and tuples.
  */
object Synthesize extends Phase[Root, Root] {

  // TODO: Remove this class completely once the ToString business is resolved.

  /**
    * Performs synthesis on the given ast `root`.
    */
  def run(root: Root)(implicit flix: Flix): Validation[Root, CompilationError] = flix.phase("Synthesize") {
    // A mutable map from symbols to definitions. Populated during traversal.
    val newDefs = mutable.Map.empty[Symbol.DefnSym, Def]

    // A mutable map from types to their toString operator. Populated during traversal.
    val mutToStringOps = mutable.Map.empty[Type, Symbol.DefnSym]

    // The source location used for all code generated by the current phase.
    val sl = SourceLocation.Unknown

    /**
      * Performs synthesis on the given definition `def`.
      */
    def visitDef(defn: Def): Def = {
      defn.copy(exp = visitExp(defn.exp))
    }

    /**
      * Performs synthesis on the given expression `exp0`.
      *
      * Rewrites equality operations to call generated equality functions.
      */
    def visitExp(exp0: Expression): Expression = exp0 match {
      case Expression.Wild(tpe, loc) => exp0

      case Expression.Var(sym, tpe, loc) => exp0

      case Expression.Def(sym, tpe, loc) => exp0

      case Expression.Sig(sym, tpe, loc) => exp0

      case Expression.Hole(sym, tpe, eff, loc) => exp0

      case Expression.Unit(loc) => exp0

      case Expression.Null(tpe, loc) => exp0

      case Expression.True(loc) => exp0

      case Expression.False(loc) => exp0

      case Expression.Char(lit, loc) => exp0

      case Expression.Float32(lit, loc) => exp0

      case Expression.Float64(lit, loc) => exp0

      case Expression.Int8(lit, loc) => exp0

      case Expression.Int16(lit, loc) => exp0

      case Expression.Int32(lit, loc) => exp0

      case Expression.Int64(lit, loc) => exp0

      case Expression.BigInt(lit, loc) => exp0

      case Expression.Str(lit, loc) => exp0

      case Expression.Default(tpe, loc) => exp0

      case Expression.Lambda(fparams, exp, tpe, loc) =>
        val e = visitExp(exp)
        Expression.Lambda(fparams, e, tpe, loc)

      case Expression.Apply(exp, exps, tpe, eff, loc) =>
        val e = visitExp(exp)
        val es = exps.map(visitExp)
        Expression.Apply(e, es, tpe, eff, loc)

      case Expression.Unary(sop, exp, tpe, eff, loc) =>
        val e = visitExp(exp)
        Expression.Unary(sop, e, tpe, eff, loc)

      case Expression.Binary(sop, exp1, exp2, tpe, eff, loc) =>
        val e1 = visitExp(exp1)
        val e2 = visitExp(exp2)
        Expression.Binary(sop, e1, e2, tpe, eff, loc)

      case Expression.Let(sym, exp1, exp2, tpe, eff, loc) =>
        val e1 = visitExp(exp1)
        val e2 = visitExp(exp2)
        Expression.Let(sym, e1, e2, tpe, eff, loc)

      case Expression.IfThenElse(exp1, exp2, exp3, tpe, eff, loc) =>
        val e1 = visitExp(exp1)
        val e2 = visitExp(exp2)
        val e3 = visitExp(exp3)
        Expression.IfThenElse(e1, e2, e3, tpe, eff, loc)

      case Expression.Stm(exp1, exp2, tpe, eff, loc) =>
        val e1 = visitExp(exp1)
        val e2 = visitExp(exp2)
        Expression.Stm(e1, e2, tpe, eff, loc)

      case Expression.Match(exp, rules, tpe, eff, loc) =>
        val e = visitExp(exp)
        val rs = rules map {
          case MatchRule(pat, guard, body) => MatchRule(pat, visitExp(guard), visitExp(body))
        }
        Expression.Match(e, rs, tpe, eff, loc)

      case Expression.Choose(exps, rules, tpe, eff, loc) =>
        val es = exps.map(visitExp)
        val rs = rules.map {
          case ChoiceRule(pat, exp) => ChoiceRule(pat, visitExp(exp))
        }
        Expression.Choose(es, rs, tpe, eff, loc)

      case Expression.Tag(sym, tag, exp, tpe, eff, loc) =>
        val e = visitExp(exp)
        Expression.Tag(sym, tag, e, tpe, eff, loc)

      case Expression.Tuple(elms, tpe, eff, loc) =>
        val es = elms map visitExp
        Expression.Tuple(es, tpe, eff, loc)

      case Expression.RecordEmpty(tpe, loc) =>
        Expression.RecordEmpty(tpe, loc)

      case Expression.RecordSelect(base, field, tpe, eff, loc) =>
        val b = visitExp(base)
        Expression.RecordSelect(b, field, tpe, eff, loc)

      case Expression.RecordExtend(field, value, rest, tpe, eff, loc) =>
        val v = visitExp(value)
        val r = visitExp(rest)
        Expression.RecordExtend(field, v, r, tpe, eff, loc)

      case Expression.RecordRestrict(field, rest, tpe, eff, loc) =>
        val r = visitExp(rest)
        Expression.RecordRestrict(field, r, tpe, eff, loc)

      case Expression.ArrayLit(elms, tpe, eff, loc) =>
        val es = elms map visitExp
        Expression.ArrayLit(es, tpe, eff, loc)

      case Expression.ArrayNew(elm, len, tpe, eff, loc) =>
        val e = visitExp(elm)
        val ln = visitExp(len)
        Expression.ArrayNew(e, ln, tpe, eff, loc)

      case Expression.ArrayLoad(base, index, tpe, eff, loc) =>
        val b = visitExp(base)
        val i = visitExp(index)
        Expression.ArrayLoad(b, i, tpe, eff, loc)

      case Expression.ArrayStore(base, index, elm, loc) =>
        val b = visitExp(base)
        val i = visitExp(index)
        val e = visitExp(elm)
        Expression.ArrayStore(b, i, e, loc)

      case Expression.ArrayLength(base, eff, loc) =>
        val b = visitExp(base)
        Expression.ArrayLength(b, eff, loc)

      case Expression.ArraySlice(base, startIndex, endIndex, tpe, loc) =>
        val b = visitExp(base)
        val i1 = visitExp(startIndex)
        val i2 = visitExp(endIndex)
        Expression.ArraySlice(b, i1, i2, tpe, loc)

      case Expression.Ref(exp, tpe, eff, loc) =>
        val e = visitExp(exp)
        Expression.Ref(e, tpe, eff, loc)

      case Expression.Deref(exp, tpe, eff, loc) =>
        val e = visitExp(exp)
        Expression.Deref(e, tpe, eff, loc)

      case Expression.Assign(exp1, exp2, tpe, eff, loc) =>
        val e1 = visitExp(exp1)
        val e2 = visitExp(exp2)
        Expression.Assign(e1, e2, tpe, eff, loc)

      case Expression.Existential(fparam, exp, loc) =>
        val e = visitExp(exp)
        Expression.Existential(fparam, e, loc)

      case Expression.Universal(fparam, exp, loc) =>
        val e = visitExp(exp)
        Expression.Universal(fparam, e, loc)

      case Expression.Ascribe(exp, tpe, eff, loc) =>
        val e = visitExp(exp)
        Expression.Ascribe(e, tpe, eff, loc)

      case Expression.Cast(exp, tpe, eff, loc) =>
        val e = visitExp(exp)
        Expression.Cast(e, tpe, eff, loc)

      case Expression.TryCatch(exp, rules, tpe, eff, loc) =>
        val e = visitExp(exp)
        val rs = rules map {
          case CatchRule(sym, clazz, body) =>
            val b = visitExp(body)
            CatchRule(sym, clazz, b)
        }
        Expression.TryCatch(e, rs, tpe, eff, loc)

      case Expression.InvokeConstructor(constructor, args, tpe, eff, loc) =>
        val as = args map visitExp
        Expression.InvokeConstructor(constructor, as, tpe, eff, loc)

      case Expression.InvokeMethod(method, exp, args, tpe, eff, loc) =>
        val e = visitExp(exp)
        val as = args.map(visitExp)
        Expression.InvokeMethod(method, e, as, tpe, eff, loc)

      case Expression.InvokeStaticMethod(method, args, tpe, eff, loc) =>
        val as = args.map(visitExp)
        Expression.InvokeStaticMethod(method, as, tpe, eff, loc)

      case Expression.GetField(field, exp, tpe, eff, loc) =>
        val e = visitExp(exp)
        Expression.GetField(field, e, tpe, eff, loc)

      case Expression.PutField(field, exp1, exp2, tpe, eff, loc) =>
        val e1 = visitExp(exp1)
        val e2 = visitExp(exp2)
        Expression.PutField(field, e1, e2, tpe, eff, loc)

      case Expression.GetStaticField(field, tpe, eff, loc) =>
        Expression.GetStaticField(field, tpe, eff, loc)

      case Expression.PutStaticField(field, exp, tpe, eff, loc) =>
        val e = visitExp(exp)
        Expression.PutStaticField(field, e, tpe, eff, loc)

      case Expression.NewChannel(exp, tpe, eff, loc) =>
        val e = visitExp(exp)
        Expression.NewChannel(e, tpe, eff, loc)

      case Expression.GetChannel(exp, tpe, eff, loc) =>
        val e = visitExp(exp)
        Expression.GetChannel(e, tpe, eff, loc)

      case Expression.PutChannel(exp1, exp2, tpe, eff, loc) =>
        val e1 = visitExp(exp1)
        val e2 = visitExp(exp2)
        Expression.PutChannel(e1, e2, tpe, eff, loc)

      case Expression.SelectChannel(rules, default, tpe, eff, loc) =>
        val rs = rules map {
          case SelectChannelRule(sym, chan, exp) =>
            val c = visitExp(chan)
            val e = visitExp(exp)
            SelectChannelRule(sym, c, e)
        }

        val d = default.map(visitExp)

        Expression.SelectChannel(rs, d, tpe, eff, loc)

      case Expression.Spawn(exp, tpe, eff, loc) =>
        val e = visitExp(exp)
        Expression.Spawn(e, tpe, eff, loc)

      case Expression.Lazy(exp, tpe, loc) =>
        val e = visitExp(exp)
        Expression.Lazy(e, tpe, loc)

      case Expression.Force(exp, tpe, eff, loc) =>
        val e = visitExp(exp)
        Expression.Force(e, tpe, eff, loc)

      case Expression.FixpointConstraintSet(cs0, stf, tpe, loc) =>
        val cs = cs0.map(visitConstraint)
        Expression.FixpointConstraintSet(cs, stf, tpe, loc)

      case Expression.FixpointCompose(exp1, exp2, stf, tpe, eff, loc) =>
        val e1 = visitExp(exp1)
        val e2 = visitExp(exp2)
        Expression.FixpointCompose(e1, e2, stf, tpe, eff, loc)

      case Expression.FixpointSolve(exp, stf, tpe, eff, loc) =>
        val e = visitExp(exp)
        Expression.FixpointSolve(e, stf, tpe, eff, loc)

      case Expression.FixpointProject(pred, exp, tpe, eff, loc) =>
        val e = visitExp(exp)
        Expression.FixpointProject(pred, e, tpe, eff, loc)

      case Expression.FixpointEntails(exp1, exp2, tpe, eff, loc) =>
        val e1 = visitExp(exp1)
        val e2 = visitExp(exp2)
        Expression.FixpointEntails(e1, e2, tpe, eff, loc)

      case Expression.FixpointFold(pred, exp1, exp2, exp3, tpe, eff, loc) =>
        val e1 = visitExp(exp1)
        val e2 = visitExp(exp2)
        val e3 = visitExp(exp3)
        Expression.FixpointFold(pred, e1, e2, e3, tpe, eff, loc)
    }

    /**
      * Performs synthesis on the given constraint `c0`.
      */
    def visitConstraint(c0: Constraint): Constraint = c0 match {
      case Constraint(cparams, head0, body0, loc) =>
        val head = visitHeadPred(head0)
        val body = body0.map(visitBodyPred)
        Constraint(cparams, head, body, loc)
    }

    /**
      * Performs synthesis on the given head predicate `h0`.
      */
    def visitHeadPred(h0: Predicate.Head): Predicate.Head = h0 match {
      case Predicate.Head.Atom(pred, den, terms, tpe, loc) =>
        // Introduce equality, hash code, and toString for the types of the terms.
        for (term <- terms) {
          getOrMkToString(term.tpe, term.loc)
        }
        val ts = terms.map(visitExp)
        Predicate.Head.Atom(pred, den, ts, tpe, loc)

      case Predicate.Head.Union(exp, tpe, loc) =>
        val e = visitExp(exp)
        Predicate.Head.Union(e, tpe, loc)
    }

    /**
      * Performs synthesis on the given body predicate `h0`.
      */
    def visitBodyPred(b0: Predicate.Body): Predicate.Body = b0 match {
      case Predicate.Body.Atom(pred, den, polarity, terms, tpe, loc) =>
        // Introduce equality, hash code, and toString for the types of the terms.
        for (term <- terms) {
          getOrMkToString(term.tpe, term.loc)
        }
        Predicate.Body.Atom(pred, den, polarity, terms, tpe, loc)

      case Predicate.Body.Guard(exp, loc) =>
        val e = visitExp(exp)
        Predicate.Body.Guard(e, loc)
    }

    /**
      * Returns an expression that computes the string representation of the value of the given expression `exp2`.
      */
    def mkApplyToString(exp2: Expression): Expression = {
      // The type of the expression.
      val tpe = exp2.tpe

      // Construct the symbol of the toString operator.
      val sym = getOrMkToString(tpe, exp2.loc)

      // Construct an expression to call the symbol with the argument `exp0`.
      val exp1 = Expression.Def(sym, Type.mkPureArrow(tpe, Type.Str), sl)
      Expression.Apply(exp1, List(exp2), Type.Str, Type.Pure, sl)
    }

    /**
      * Returns the symbol of the toString operator associated with the given type `tpe`.
      *
      * If no such definition exists, it is created.
      */
    def getOrMkToString(tpe: Type, loc: SourceLocation): Symbol.DefnSym = mutToStringOps.getOrElse(tpe, {
      // Introduce a fresh symbol for the toString operator.
      val sym = Symbol.freshDefnSym("toString", loc)

      // Immediately add the symbol to the toString map.
      // This is necessary to support recursive data types.
      mutToStringOps += (tpe -> sym)

      // Construct one fresh variable symbols for the formal parameter.
      val freshX = Symbol.freshVarSym("x", sl)

      // Construct the formal parameter.
      val paramX = FormalParam(freshX, Ast.Modifiers.Empty, tpe, sl)

      // Annotations and modifiers.
      val ann = Nil
      val mod = Ast.Modifiers(Ast.Modifier.Synthetic :: Nil)

      // Type and formal parameters.
      val tparams = Nil
      val fparams = paramX :: Nil

      // The body expression.
      val exp = mkToStringExp(tpe, freshX)

      // The definition type.
      val lambdaType = Type.mkPureArrow(tpe, Type.Str)

      // Assemble the definition.
      val sc = Scheme(Nil, List.empty, lambdaType)
      val defn = Def(Ast.Doc(Nil, sl), ann, mod, sym, tparams, fparams, exp, sc, sc, Type.Pure, sl)

      // Add it to the map of new definitions.
      newDefs += (defn.sym -> defn)

      // And return its symbol.
      defn.sym
    })

    /**
      * Returns an expression that computes the string representation of the value of the given expression `exp0` of type `tpe`.
      */
    def mkToStringExp(tpe: Type, varX: Symbol.VarSym): Expression = {
      // An expression that evaluates to the value of varX.
      val exp0 = Expression.Var(varX, tpe, sl)

      // Compute the type constructor.
      val typeConstructor = tpe.typeConstructor

      // Determine the string representation based on the type `tpe`.
      typeConstructor match {
        case None =>
          throw InternalCompilerException(s"Unknown type constructor '$tpe'.")

        case Some(tc) => tc match {
          case TypeConstructor.Unit =>
            Expression.Str("()", sl)

          case TypeConstructor.Bool =>
            val method = classOf[java.lang.Boolean].getMethod("toString", classOf[Boolean])
            Expression.InvokeStaticMethod(method, List(exp0), Type.Str, Type.Pure, sl)

          case TypeConstructor.Char =>
            val method = classOf[java.lang.Character].getMethod("toString", classOf[Char])
            Expression.InvokeStaticMethod(method, List(exp0), Type.Str, Type.Pure, sl)

          case TypeConstructor.Float32 =>
            val method = classOf[java.lang.Float].getMethod("toString", classOf[Float])
            Expression.InvokeStaticMethod(method, List(exp0), Type.Str, Type.Pure, sl)

          case TypeConstructor.Float64 =>
            val method = classOf[java.lang.Double].getMethod("toString", classOf[Double])
            Expression.InvokeStaticMethod(method, List(exp0), Type.Str, Type.Pure, sl)

          case TypeConstructor.Int8 =>
            val method = classOf[java.lang.Byte].getMethod("toString", classOf[Byte])
            Expression.InvokeStaticMethod(method, List(exp0), Type.Str, Type.Pure, sl)

          case TypeConstructor.Int16 =>
            val method = classOf[java.lang.Short].getMethod("toString", classOf[Short])
            Expression.InvokeStaticMethod(method, List(exp0), Type.Str, Type.Pure, sl)

          case TypeConstructor.Int32 =>
            val method = classOf[java.lang.Integer].getMethod("toString", classOf[Int])
            Expression.InvokeStaticMethod(method, List(exp0), Type.Str, Type.Pure, sl)

          case TypeConstructor.Int64 =>
            val method = classOf[java.lang.Long].getMethod("toString", classOf[Long])
            Expression.InvokeStaticMethod(method, List(exp0), Type.Str, Type.Pure, sl)

          case TypeConstructor.BigInt =>
            val method = classOf[java.math.BigInteger].getMethod("toString")
            Expression.InvokeMethod(method, exp0, Nil, Type.Str, Type.Pure, sl)

          case TypeConstructor.Str => exp0

          case TypeConstructor.Arrow(l) =>
            Expression.Str("<<closure>>", sl)

          case TypeConstructor.Array =>
            Expression.Str("<<array>>", sl)

          case TypeConstructor.Channel =>
            Expression.Str("<<channel>>", sl)

          case TypeConstructor.Lazy =>
            Expression.Str("<<Lazy>>", sl)

          case TypeConstructor.Ref => Expression.Str("<<ref>>", sl)

          case TypeConstructor.Native(clazz) =>
            val method = classOf[java.lang.Object].getMethod("toString")
            Expression.InvokeMethod(method, exp0, Nil, Type.Str, Type.Pure, sl)

          case TypeConstructor.RecordEmpty =>
            Expression.Str("<<record>>", sl)

          case TypeConstructor.RecordExtend(field) =>
            Expression.Str("<<record>>", sl)

          case TypeConstructor.SchemaEmpty =>
            val method = classOf[java.lang.Object].getMethod("toString")
            Expression.InvokeMethod(method, exp0, Nil, Type.Str, Type.Pure, sl)

          case TypeConstructor.SchemaExtend(_) =>
            val method = classOf[java.lang.Object].getMethod("toString")
            Expression.InvokeMethod(method, exp0, Nil, Type.Str, Type.Pure, sl)

          case TypeConstructor.Tuple(_) =>
            //
            // Assume we have a tuple (a, b, c)
            //
            // then we generate the expression:
            //
            //   match exp0 {
            //     case (x1, x2, x3) => "(" + recurse(x1) + ", " + recurse(x2) + ", " + recurse(x3) + ")"
            //   }
            //
            // where recurse is a recursive call to this procedure.
            //

            // The types of the tuple elements.
            val elementTypes = getElementTypes(tpe)

            // The expression `exp0` to match against, simply `exp0`.
            val matchValue = exp0

            // Introduce fresh variables for each component of the tuple.
            val freshVarsX = (0 to getArity(tpe)).map(_ => Symbol.freshVarSym("x", sl)).toList

            // The pattern of the rule.
            val p = Pattern.Tuple((freshVarsX zip elementTypes).map {
              case (freshVar, elmType) => Pattern.Var(freshVar, elmType, sl)
            }, tpe, sl)

            // The guard of the rule (simply true).
            val g = Expression.True(sl)

            // The elements of the tuple.
            val inner = (freshVarsX zip elementTypes).map {
              case (freshX, elementType) => mkApplyToString(Expression.Var(freshX, elementType, sl))
            }

            // Construct the string expression (e1, e2, e3, ...)
            val b = concatAll(
              Expression.Str("(", sl) ::
                intersperse(inner, Expression.Str(", ", sl)) :::
                Expression.Str(")", sl) :: Nil
            )

            // Put the components together.
            val rule = MatchRule(p, g, b)

            // Assemble the entire match expression.
            Expression.Match(matchValue, rule :: Nil, Type.Str, Type.Pure, sl)

          case TypeConstructor.Enum(_, _) =>
            //
            // Assume we have an enum:
            //
            //   enum Option[Int] {
            //     case None,
            //     case Some(Int)
            //    }
            //
            // then we generate the expression:
            //
            //   match e {
            //     case (None(freshX)) => "None(" + recurse(freshX) + ")"
            //     case (Some(freshX)) => "Some(" + recurse(freshX) + ")"
            //   }
            //
            // where recurse is a recursive call to this procedure.
            //
            // Retrieve the enum symbol and enum declaration.
            val enumSym = getEnumSym(tpe)
            val enumDecl = root.enums(enumSym)

            // The expression `exp0` to match against, simply `exp0`.
            val matchValue = exp0

            // Compute the cases specialized to the current type.
            val cases = casesOf(enumDecl, tpe)

            // Generate a match rule for each tag.
            val rs = cases map {
              case (tag, caseType) =>
                // Generate a case of the form:
                // (Tag(freshX)) => "Tag(" + recurse(freshX) + ")"

                // Generate a fresh variable symbols.
                val freshX = Symbol.freshVarSym("x", sl)

                // Generate the tag pattern: Tag(freshX).
                val p = Pattern.Tag(enumSym, tag, Pattern.Var(freshX, caseType, sl), tpe, sl)

                // Generate the guard (simply true).
                val g = Expression.True(sl)

                // Generate the rule body.
                val b = concatAll(List(
                  Expression.Str(tag.name, sl),
                  Expression.Str("(", sl),
                  mkApplyToString(Expression.Var(freshX, caseType, sl)),
                  Expression.Str(")", sl)
                ))

                // Put the components together.
                MatchRule(p, g, b)
            }
            // Assemble the entire match expression.
            Expression.Match(matchValue, rs, Type.Str, Type.Pure, sl)

          case _ => throw InternalCompilerException(s"Unexpected type constructor: '$tc'.")
        }
      }
    }

    /**
      * Returns the enum symbol of the given enum type `tpe`.
      */
    def getEnumSym(tpe: Type): Symbol.EnumSym = {
      val Some(TypeConstructor.Enum(sym, _)) = tpe.typeConstructor
      sym
    }

    /**
      * Returns the tuple arity of the given type `tpe`.
      */
    def getArity(tpe: Type): Int = getElementTypes(tpe).length

    /**
      * Returns the element types of the given tuple type `tpe`.
      */
    def getElementTypes(tpe: Type): List[Type] = tpe.typeArguments

    /**
      * Returns `true` if `tpe` is a type variable.
      */
    // TODO: Deprecated
    def isVar(tpe: Type): Boolean = tpe.typeConstructor match {
      case None => true
      case _ => false
    }

    /**
      * Returns `true` if `tpe` is an arrow type.
      */
    // TODO: Deprecated
    def isArrow(tpe: Type): Boolean = tpe.typeConstructor match {
      case Some(TypeConstructor.Arrow(_)) => true
      case _ => false
    }

    /**
      * Constructs the tuple type (A, B, ...) where the types are drawn from the list `ts`.
      */
    def mkTupleType(ts: Type*): Type = Type.mkTuple(ts.toList)

    /**
      * Returns an association list of the (tag, type)s of the given `enum` specialized to the given type `tpe`.
      */
    def casesOf(enum: Enum, tpe: Type): List[(Name.Tag, Type)] = {
      // Compute a substitution for the parametric enum specialized to the specific type.
      val subst = Unification.unifyTypes(enum.tpeDeprecated, tpe).get

      // Apply the substitution to each case.
      enum.cases.map {
        case (tag, Case(enumSym, tagName, tagType, tagScheme, tagLoc)) => tag -> subst(tagType)
      }.toList
    }

    /**
      * Returns an expression that computes the string concatenation of `exp1` and `exp2`.
      */
    def concat(exp1: Expression, exp2: Expression): Expression =
      Expression.Binary(SemanticOperator.StringOp.Concat, exp1, exp2, Type.Str, Type.Pure, sl)

    /**
      * Returns an expression that computes the string concatenation of the given expressions `exps`.
      */
    def concatAll(exps: List[Expression]): Expression =
      exps.foldLeft(Expression.Str("", sl): Expression)(concat)

    /**
      * Inserts the element `a` between every element of the list `l`.
      */
    def intersperse[A](l: List[A], a: A): List[A] = l match {
      case Nil => Nil
      case x :: Nil => x :: Nil
      case x :: y :: xs => x :: a :: intersperse(y :: xs, a)
    }

    //
    // Generate Special Operators.
    //
    /*
     * (a) Every type that appears as return type of some definition.
     */
    val typesInDefs: Set[Type] = root.defs.collect {
      case (_, Def(_, ann, _, sym, _, _, exp, _, _, _, _)) if (isBenchmark(ann) || isTest(ann) || sym.isMain) => exp.tpe
    }.toSet

    /*
     * (b) Every type that appears as some lattice type.
     */
    val typesInLattices: Set[Type] = root.latticeOps.keySet

    /*
     * Introduce ToString special operators.
     */
    // TODO: Refactor these
    typesInDefs.foldLeft(Map.empty[Type, Symbol.DefnSym]) {
      case (macc, tpe) if !isArrow(tpe) && !isVar(tpe) => macc + (tpe -> getOrMkToString(tpe, sl))
      case (macc, tpe) => macc
    }

    /*
     * Rewrite every equality expression in a definition to explicitly call the equality operator.
     */
    val defs = root.defs.map {
      case (sym, defn) => sym -> visitDef(defn)
    }

    /*
     * Construct the map of special operators.
     */
    val specialOps: Map[SpecialOperator, Map[Type, Symbol.DefnSym]] = Map(
      SpecialOperator.Equality -> root.specialOps.getOrElse(SpecialOperator.Equality, Map.empty),
      SpecialOperator.HashCode -> root.specialOps.getOrElse(SpecialOperator.HashCode, Map.empty),
      SpecialOperator.ToString -> (mutToStringOps.toMap ++ root.specialOps.getOrElse(SpecialOperator.ToString, Map.empty))
    )

    // Reassemble the ast with the new definitions.
    root.copy(defs = defs ++ newDefs, specialOps = specialOps).toSuccess

  }

}
