"""Bounded rule explanations for calculus; answers remain owned by the evaluator."""
import sympy as s
from calc_display import display_tree, readable

SUMMARY = "A detailed derivation is not available for this expression. The computed result is shown below."


def calculus_steps(engine, method, values, answer):
    steps = []
    note = ""
    def add(title, explanation, *formulas):
        formulas=[value.xreplace({dummy:auxiliary("u") for dummy in value.atoms(s.Dummy)}) for value in formulas]
        steps.append({"title": title, "explanation": explanation,
                      "equations": [{"exact": readable(value), "tree": display_tree(value)} for value in formulas]})
    def eq(left, right): return s.Eq(left, right, evaluate=False)
    expression = values[0]
    variable = values[1].lhs if isinstance(values[1], s.Equality) else values[1]
    def auxiliary(name):
        used={str(symbol) for symbol in expression.free_symbols}
        while name in used: name+="1"
        return s.Symbol(name)
    if not isinstance(variable, s.Symbol) or s.count_ops(expression) > 60:
        return {"steps": [{"title": "Computed result", "tree": display_tree(answer), "exact": readable(answer)}], "note": SUMMARY}
    if method == "diff":
        order = values[2] if len(values) > 2 else s.Integer(1)
        add("Differentiate the expression", "Find how the expression changes with respect to the selected variable.", s.Derivative(expression, variable, order, evaluate=False))
        visited = [0]
        def derive(expr, depth=0):
            visited[0] += 1
            if depth > 5 or visited[0] > 24: return
            derivative = s.Derivative(expr, variable, evaluate=False)
            result = s.diff(expr, variable)
            if not expr.has(variable):
                add("Constant rule", "A value that does not depend on the variable has derivative 0.", eq(derivative, 0))
            elif expr == variable:
                add("Variable rule", "The derivative of the variable with respect to itself is 1.", eq(derivative, 1))
            elif expr.is_Add:
                terms = list(expr.args)
                add("Sum rule", "Differentiate each term separately, then add the derivatives.", eq(derivative, s.Add(*(s.Derivative(term, variable, evaluate=False) for term in terms), evaluate=False)))
                for term in terms: derive(term, depth+1)
            elif expr.is_Mul:
                constant, rest = expr.as_independent(variable, as_Add=False)
                if constant != 1:
                    add("Constant multiple rule", "Keep the constant multiplier and differentiate the remaining expression.", eq(derivative, s.Mul(constant, s.Derivative(rest, variable, evaluate=False), evaluate=False)))
                    derive(rest, depth+1)
                else:
                    numerator, denominator = s.fraction(expr)
                    if denominator.has(variable):
                        top = s.Add(s.Mul(s.Derivative(numerator, variable, evaluate=False), denominator, evaluate=False), -s.Mul(numerator, s.Derivative(denominator, variable, evaluate=False), evaluate=False), evaluate=False)
                        add("Quotient rule", "Differentiate the numerator times the denominator, subtract the numerator times the derivative of the denominator, and divide by the denominator squared.", eq(derivative, top/denominator**2))
                        derive(numerator, depth+1); derive(denominator, depth+1)
                    else:
                        factors = list(expr.args)
                        terms = [s.Mul(*(s.Derivative(factor, variable, evaluate=False) if index == chosen else factor for index, factor in enumerate(factors)), evaluate=False) for chosen in range(len(factors))]
                        add("Product rule", "Differentiate one factor at a time while keeping the other factors, then add the terms.", eq(derivative, s.Add(*terms, evaluate=False)))
                        for factor in factors: derive(factor, depth+1)
            elif expr.is_Pow and not expr.exp.has(variable):
                base, exponent = expr.args
                rule = s.Mul(exponent, s.Pow(base, exponent-1, evaluate=False), s.Derivative(base, variable, evaluate=False), evaluate=False)
                add("Power and chain rules", "Multiply by the exponent, reduce the exponent by 1, then multiply by the derivative of the inner expression.", eq(derivative, rule))
                if base != variable: derive(base, depth+1)
            elif expr.is_Function and len(expr.args) == 1 and expr.func in (s.sin, s.cos, s.tan, s.exp, s.log, s.asin, s.acos, s.atan, s.sinh, s.cosh):
                inner = expr.args[0]
                u = s.Dummy("u")
                outer = s.diff(expr.func(u), u).subs(u, inner)
                add("Function and chain rules", "Differentiate the outer function, keep its inner expression, and multiply by the derivative of that inner expression.", eq(derivative, s.Mul(outer, s.Derivative(inner, variable, evaluate=False), evaluate=False)))
                if inner != variable: derive(inner, depth+1)
            else:
                if result.has(s.Derivative):
                    add("Formal derivative", "This function is unspecified. Its derivative remains symbolic until a formula for the function is supplied.", derivative)
                else: add("Evaluate this derivative", "The symbolic differentiator evaluates this part directly.", eq(derivative, result))
        if order.is_Integer and 1 <= order <= 3:
            current = expression
            for index in range(int(order)):
                if index: add("Differentiate again", "For a higher derivative, apply the differentiation rules to the previous result.", current)
                derive(current)
                current = s.diff(current, variable)
                add("Combine the derivatives", "Combine the terms obtained from the rules to simplify the derivative.", current)
        else: note = SUMMARY
    elif method == "integrate":
        integral = s.Integral(expression, variable) if len(values) == 2 else s.Integral(expression, (variable, values[2], values[3]))
        add("Integrate the expression", "Find an antiderivative whose derivative is the integrand.", integral)
        if getattr(engine, "integral_strategy", None) == "log_arctan_polylog":
            u = s.log(variable)
            coefficient=s.cancel(expression*(1+variable**2)/s.log(variable))
            if coefficient!=1:
                add("Take out the constant", "A multiplier that does not depend on the integration variable can be taken outside the integral.", coefficient*s.Integral(s.log(variable)/(1+variable**2),variable))
            add("Integration by parts", "Choose the logarithm as u and integrate the rational factor as dv. Use ∫u dv = uv − ∫v du.", eq(auxiliary("u"), u), eq(auxiliary("v"), s.atan(variable)))
            add("Reduce to a special-function integral", "The remaining integral of atan(x)/x is expressed using the dilogarithm Li₂, rather than elementary functions.", s.log(variable)*s.atan(variable)-s.Integral(s.atan(variable)/variable, variable))
            add("Dilogarithm identity", "The derivative of Li₂(z) is −ln(1−z)/z. Express atan(x) as a difference of complex logarithms to integrate the remaining term.", eq(s.Integral(s.atan(variable)/variable, variable), (s.polylog(2, s.I*variable)-s.polylog(2, -s.I*variable))/(2*s.I)))
        elif (not getattr(answer, "has", lambda *_: False)(s.Integral)
                and not expression.free_symbols - {variable}
                and not any(power.exp.has(variable) or power.exp.is_Rational and power.exp.q>1 and power.base.has(s.tan,s.cot) for power in expression.atoms(s.Pow))):
            from sympy.integrals.manualintegrate import integral_steps
            rule = integral_steps(expression, variable)
            # Only use a matching primitive; a different branch must not become a false explanation.
            primitive = rule.eval()
            agrees = not primitive.has(s.Integral) and s.count_ops(primitive) <= 100 and s.simplify(s.diff(primitive, variable)-expression) == 0
            if agrees:
                def explain(rule, depth=0):
                    if depth > 6 or len(steps) >= 28: return
                    name = type(rule).__name__
                    if name == "AlternativeRule":
                        choice = next((choice for choice in rule.alternatives if not choice.contains_dont_know()), rule.alternatives[0])
                        explain(choice, depth+1); return
                    if name == "AddRule":
                        add("Integrate term by term", "The integral of a sum is the sum of the integrals. Work on each term separately.", s.Add(*(s.Integral(child.integrand, child.variable) for child in rule.substeps), evaluate=False))
                        for child in rule.substeps: explain(child, depth+1)
                    elif name == "ConstantTimesRule":
                        add("Take out the constant", "A multiplier that does not depend on the integration variable can be taken outside the integral.", s.Mul(rule.constant, s.Integral(rule.other, rule.variable), evaluate=False))
                        explain(rule.substep, depth+1)
                    elif name == "URule":
                        u = auxiliary("u")
                        add("Substitution rule", "Introduce a new variable for the inner expression. Its derivative changes dx to du, giving a simpler integral.", eq(u, rule.u_func), eq(s.Derivative(s.Function(str(u))(rule.variable),rule.variable,evaluate=False), s.diff(rule.u_func, rule.variable)), s.Integral(rule.substep.integrand.xreplace({rule.u_var:u}), u))
                        explain(rule.substep, depth+1)
                        add("Return to the original variable", "Replace the temporary variable with its original expression.", rule.eval())
                    elif name == "PartsRule":
                        v = rule.v_step.eval()
                        add("Integration by parts", "Choose u and dv, then use ∫u dv = uv − ∫v du to replace a product integral with a simpler one.", eq(auxiliary("u"), rule.u), eq(auxiliary("v"), v), rule.u*v-s.Integral(v*s.diff(rule.u, rule.variable), rule.variable))
                        explain(rule.second_step, depth+1)
                    elif name == "RewriteRule":
                        add("Rewrite the integrand", "Rewrite the expression into an equivalent form with a familiar integration rule.", eq(rule.integrand, rule.rewritten))
                        explain(rule.substep, depth+1)
                    else:
                        title = "Integral power rule" if name == "PowerRule" else "Logarithm integral rule" if name == "ReciprocalRule" else "Standard integral rule"
                        explanation = "Increase the exponent by 1 and divide by that new exponent. The exponent −1 uses the logarithm rule instead." if name == "PowerRule" else "Use the standard antiderivative for this expression; differentiating it gives the integrand."
                        add(title, explanation, eq(s.Integral(rule.integrand, rule.variable), rule.eval()))
                explain(rule)
                add("Combine antiderivatives", "Put the integrated terms and constant multipliers together.", primitive)
            else: note = SUMMARY
        else: note = "An antiderivative was not found. This does not prove that no closed form exists. For a numerical value, supply a finite integration interval." if answer.has(s.Integral) else SUMMARY
        if len(values) == 4 and not answer.has(s.Integral):
            # Avoid a second integration: the rule primitive, when available, supplies F.
            if 'primitive' in locals() and agrees:
                difference=primitive.subs(variable,values[3])-primitive.subs(variable,values[2])
                if not difference.has(s.nan,s.zoo,s.oo,-s.oo) and s.simplify(difference-answer)==0:
                    add("Evaluate at the bounds", "For a continuous integrand, use F(b) − F(a). Singularities require a separate improper-integral check.", eq(s.Symbol("F(b)-F(a)"), difference))
        elif len(values) == 2 and not answer.has(s.Integral):
            add("Add the integration constant", "Antiderivatives that differ by a constant have the same derivative, so the general answer includes C.")
    elif method == "limit":
        if isinstance(values[1], s.Equality):
            point = values[1].rhs
            direction = str(values[2]) if len(values) > 2 else "both"
        else:
            point = values[2]
            direction = str(values[3]) if len(values) > 3 else "both"
        add("Identify the approach", "Follow the selected approach to the point. Left and right limits may differ.", s.Limit(expression, variable, point, dir={"both":"+-", "left":"-", "right":"+"}.get(direction, direction)))
        substituted = expression.subs(variable, point)
        if not substituted.has(s.nan, s.zoo, s.oo, -s.oo) and s.simplify(substituted-answer) == 0:
            if any(condition.subs(variable,point)==s.false for condition in engine.conditions):
                add("Evaluate the continuous extension", "The original expression is undefined at the point. Use its simplified form on nearby allowed values to find the limit.", substituted)
            else: add("Direct substitution", "When the expression is continuous at the approach point, substitute the point directly.", substituted)
        else:
            simplified = s.cancel(expression) if expression.is_rational_function(variable) else expression
            if simplified != expression:
                add("Cancel a removable factor", "Cancel common factors away from the approach point. The simplified expression has the same limit there.", eq(expression, simplified))
            numerator, denominator = s.fraction(s.together(simplified))
            reduced_value=simplified.subs(variable,point)
            if simplified!=expression and not reduced_value.has(s.nan,s.zoo,s.oo,-s.oo) and s.simplify(reduced_value-answer)==0:
                add("Evaluate the continuous extension", "The original expression is undefined at the point. Use its simplified form on nearby allowed values to find the limit.", reduced_value)
            elif point.is_finite and numerator.subs(variable, point) == 0 and denominator.subs(variable, point) == 0 and not expression.has(s.Abs, s.Piecewise):
                approach={"both":"+-","left":"-","right":"+"}.get(direction,direction)
                for _ in range(3):
                    dn, dd = s.diff(numerator, variable), s.diff(denominator, variable)
                    if dd == 0 or s.count_ops(dn/dd)>80: break
                    add("L'Hôpital's rule for 0/0", "For this differentiable 0/0 form, differentiate the numerator and denominator separately, then evaluate their ratio along the same approach.", eq(s.Limit(numerator/denominator, variable, point,dir=approach), s.Limit(dn/dd, variable, point,dir=approach)))
                    candidate=(dn/dd).subs(variable,point)
                    if not candidate.has(s.nan,s.zoo,s.oo,-s.oo) and s.simplify(candidate-answer)==0:
                        add("Direct substitution", "When the expression is continuous at the approach point, substitute the point directly.",candidate);break
                    numerator,denominator=dn,dd
                    if numerator.subs(variable,point)!=0 or denominator.subs(variable,point)!=0: break
            elif point.is_infinite and expression.is_rational_function(variable):
                add("Compare highest powers", "For a rational function at infinity, the highest powers determine whether the ratio tends to 0, a finite coefficient ratio, or infinity.", simplified)
            else: note = SUMMARY
    add("Computed result", "The result keeps the original domain and the selected calculus settings.", answer)
    return {"steps": steps, "note": note, "method": {"diff":"Differentiation", "integrate":"Integration", "limit":"Limits"}[method]}
