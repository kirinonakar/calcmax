"""Inspectable algebra steps, using the solver's evaluated input and final answer.

No second solve is run, and every transformation preserves the original domain.
Unsupported symbolic work is explicitly described as a solver summary.
"""
import sympy as s
from math import gcd
from sympy.solvers.solveset import NonlinearError
from sympy.core.relational import Relational
from calc_display import display_tree, readable
from calc_equation_system_steps import linear_system_steps, can_explain_linear_system, nonlinear_system_steps, quadratic_candidates


SUMMARY = "Detailed transformations are unavailable for this equation; the steps below summarize the solver input and result."
LIMIT = "The equation is too large for a detailed derivation; showing a solver summary."


def equation_solution_tree(tree):
    """Present system mappings as equalities, retaining answer display precision."""
    if tree.get("kind") != "list" or not tree.get("args") or not all(item.get("kind") == "rows" for item in tree["args"]):
        return tree
    solutions = [{"kind": "tuple", "args": [
        {"kind": "relation", "value": "=", "args": [{"kind": "symbol", "value": row["value"]}, row["args"][0]]}
        for row in item["args"]]} for item in tree["args"]]
    return solutions[0] if len(solutions) == 1 else {"kind": "list", "args": solutions}


EXPLANATIONS = {
    "Move all terms to the left": "Subtract the right-hand side from both sides. The equation now has 0 on the right, which makes its structure easier to see.",
    "Expand and collect like terms": "Expand products and combine terms with the same power of the variable.",
    "Move the constant to the right": "Subtract the constant from both sides, leaving the variable term on the left.",
    "Divide by the coefficient of the variable": "Divide both sides by the same nonzero coefficient to leave the variable by itself.",
    "Compute the discriminant": "For ax² + bx + c = 0, calculate D = b² − 4ac. This is the quantity under the square root in the quadratic formula.",
    "Apply the quadratic formula": "Substitute a, b, c and D into x = (−b ± √D)/(2a). The two signs give the candidate roots.",
    "Simplify the candidate roots": "Evaluate the square root and simplify each fraction to obtain the candidate roots.",
    "Exclude zero denominators": "Division by zero is undefined. Keep this restriction when removing the denominator so invalid roots are not included.",
    "Multiply by the nonzero denominator": "Multiply both sides by the denominator. This is valid only where the denominator is nonzero.",
    "Factor the polynomial": "Rewrite the polynomial as a product. A product is zero when at least one factor is zero.",
    "Set each factor equal to zero": "Solve the smaller equations separately, then combine their allowed roots.",
    "Solve the linear factor": "Move the constant and divide by the variable coefficient in this factor.",
    "Solution": "These are the solver's final values after applying the original equation and domain restrictions.",
    "Numerical root": "Use the starting value or interval to find a nearby root numerically.",
    "Substitute the root: residual should be near zero": "Put the computed value into the left side minus the right side. A residual near zero checks that it satisfies the equation numerically.",
    "Isolate the function": "Move and divide the other terms so the function is alone on one side.",
    "Principal inverse value": "Apply the inverse function to get a principal value. Periodic functions can have more solutions, so this is only the first candidate.",
    "Include periodic branches (n is an integer)": "Add the function's periods to include all candidate branches before applying domain restrictions.",
    "Integrating factor": "Choose this factor so the left side becomes the derivative of a product, which can be integrated directly.",
    "Remove the quadratic term": "Introduce a new variable to remove the squared term. The cubic becomes simpler to solve.",
    "Depressed cubic": "Collect the transformed equation into t³ + pt + q = 0, using the new variable chosen above.",
    "Cardano discriminant": "Calculate (q/2)² + (p/3)³. This determines the square root used in Cardano's formula.",
    "Cardano substitution": "Write the new variable as the sum of two values. Their product must satisfy the displayed constraint.",
    "Choose cube roots satisfying the product constraint": "Choose cube roots of the two displayed quantities while keeping the required product. Arbitrary cube-root pairs do not always give a solution.",
    "Substitute back": "Use the original substitution to convert back to the variable in the input equation.",
    "Isolate the variable in each branch": "Move the constant and divide by the variable coefficient in each candidate branch.",
    "Exponentiate both sides": "Apply the exponential function to undo the logarithm, then check the original logarithm's domain.",
    "Normalize the first-order linear equation": "Divide by the derivative's coefficient to put the equation in the form y′ + p(t)y = q(t).",
    "Multiply by the integrating factor": "Multiply both sides by the integrating factor. The product rule turns the left side into a single derivative.",
    "Integrate both sides": "Integrate the product derivative and the right-hand side. Include an arbitrary constant for the general solution.",
    "Solve for the dependent function": "Divide by the integrating factor to leave the unknown function by itself.",
    "Apply initial conditions": "Use the supplied values of the function to determine the integration constants.",
    "Check the original domain restrictions": "These excluded values still apply even if the simplified equation no longer contains the original denominator.",
    "Substitute a power of the variable": "All nonconstant powers share a common exponent. Replace that power with a new variable to reduce the polynomial's degree.",
    "Solve the reduced polynomial": "Solve the lower-degree equation for the new variable before returning to the original variable.",
    "Recover roots of the original variable": "For each value of the new variable, take all complex roots of the displayed power equation. The index k is an integer in the displayed range; the final answer applies the original domain restrictions.",
    "Form the characteristic equation": "For a homogeneous second-order linear equation with constant coefficients, try an exponential solution. Its exponent satisfies this quadratic equation.",
    "Characteristic roots": "Solve the characteristic quadratic to find the exponents of the independent solutions.",
    "Build the homogeneous solution": "Distinct roots give two exponential solutions. For a repeated root, multiply one exponential solution by the independent variable. The arbitrary constants describe the general solution.",
}


def equation_steps(engine, method, values, answer):
    steps = []
    extra = {}

    def add(title, value=None):
        step = {"title": title}
        if value is not None:
            step.update(exact=readable(value), tree=display_tree(value))
        steps.append(step)

    def eq(left, right=0):
        return s.Eq(left, right, evaluate=False)

    def add_changed(title, value):
        # Skip a transformation that leaves the immediately visible formula
        # unchanged. Conclusions and domain checks still have their own steps.
        tree = display_tree(value)
        if not steps or steps[-1].get("tree") != tree:
            add(title, value)

    def residual(value):
        return value.lhs-value.rhs if isinstance(value, s.Equality) else value

    source = values[0]
    if method == "solve" and (isinstance(source, list) or isinstance(values[1], list)):
        equations = source if isinstance(source, list) else [source]
        variables = values[1] if isinstance(values[1], list) else [values[1]]
        if len(equations) == 1:
            active = [variable for variable in variables if equations[0].has(variable)]
            if len(active) == 1 or not active and len(variables) == 1:
                # System inputs use lists even for one equation. Reuse the
                # scalar derivation when only one requested variable occurs,
                # keeping the solver's original result shape at the end.
                source = equations[0]
                values = [source, active[0] if active else variables[0], *values[2:]]
    add("Original equation", source)
    note = ""
    expressions = source if isinstance(source, list) else [source]
    if any(s.count_ops(item) > 160 or any(power.exp.is_Integer and abs(power.exp) > 8 for power in item.atoms(s.Pow)) for item in expressions):
        note = LIMIT
    elif isinstance(source, Relational) and not isinstance(source, s.Equality):
        note = SUMMARY
    elif method in ("dsolve", "desolve", "pdsolve"):
        expression = residual(source)
        add_changed("Move all terms to the left", eq(expression))
        note = SUMMARY
        if method in ("dsolve", "desolve"):
            function, variable = values[1:3]
            derivative = s.Derivative(function, variable)
            try:
                linear = s.Poly(expression, derivative, function)
            except s.PolynomialError:
                linear = None
            if linear is not None and linear.total_degree() <= 1:
                a = linear.coeff_monomial(derivative)
                b = linear.coeff_monomial(function)
                c = linear.coeff_monomial(1)
                if a.is_zero is False and not any(term.has(function) for term in (a, b, c)):
                    p, q = s.cancel(b/a), s.cancel(-c/a)
                    from sympy.integrals.manualintegrate import integral_steps
                    primitive = integral_steps(p, variable).eval() if s.count_ops(p) <= 30 else s.Integral(p, variable)
                    if (not primitive.has(s.Integral, s.Piecewise) and s.count_ops(primitive) <= 60
                            and s.simplify(s.diff(primitive, variable)-p) == 0):
                        mu = s.exp(primitive)
                        add("Normalize the first-order linear equation", eq(derivative+p*function, q))
                        add("Integrating factor", eq(s.Symbol("mu"), mu))
                        add("Multiply by the integrating factor", eq(s.Derivative(mu*function, variable, evaluate=False), mu*q))
                        integral = s.Integral(mu*q, variable)
                        constant_name = "C1"
                        while s.Symbol(constant_name) in expression.free_symbols: constant_name += "1"
                        integrated = integral+s.Symbol(constant_name)
                        add("Integrate both sides", eq(mu*function, integrated))
                        add("Solve for the dependent function", eq(function, integrated/mu))
                        note = ""
            if note == SUMMARY:
                second = s.Derivative(function, variable, 2)
                try:
                    linear = s.Poly(expression, second, derivative, function)
                except s.PolynomialError:
                    linear = None
                if linear is not None and linear.total_degree() == 1:
                    a, b, c = [linear.coeff_monomial(term) for term in (second, derivative, function)]
                    constant = linear.coeff_monomial(1)
                    delta = s.expand(b*b-4*a*c)
                    if (a.is_zero is False and constant == 0 and delta.is_zero is not None
                            and not any(term.has(variable, function) for term in (a, b, c))):
                        used = {str(symbol) for symbol in expression.free_symbols}
                        def fresh(name):
                            while name in used: name += "1"
                            used.add(name)
                            return s.Symbol(name)
                        r, c1, c2 = fresh("r"), fresh("C1"), fresh("C2")
                        roots = [s.cancel((-b+sign*s.sqrt(delta))/(2*a)) for sign in (1, -1)]
                        candidate = ((c1+c2*variable)*s.exp(roots[0]*variable) if delta == 0 else
                                     c1*s.exp(roots[0]*variable)+c2*s.exp(roots[1]*variable))
                        if s.simplify(expression.subs(function, candidate).doit()) == 0:
                            add("Form the characteristic equation", eq(a*r*r+b*r+c))
                            add("Characteristic roots", [eq(r, root) for root in dict.fromkeys(roots)])
                            add("Build the homogeneous solution", eq(function, candidate))
                            note = ""
        if method in ("dsolve", "desolve") and len(values) == 4:
            add("Apply initial conditions", values[3])
    elif method == "nsolve":
        var = values[1]
        expression = residual(source)
        add_changed("Move all terms to the left", eq(expression))
        add("Initial bracket" if len(values) == 4 else "Initial guess", values[2:] if len(values) == 4 else eq(var, values[2]))
        add("Numerical root", eq(var, answer))
        add("Substitute the root: residual should be near zero", s.N(expression.subs(var, answer), engine.precision))
        note = "Numerical solving finds a root near the initial guess or within the bracket; it does not enumerate all roots."
    elif isinstance(source, list) or isinstance(values[1], list):
        equations = source if isinstance(source, list) else [source]
        variables = values[1] if isinstance(values[1], list) else [values[1]]
        expressions = [residual(item) for item in equations]
        try:
            matrix, rhs = s.linear_eq_to_matrix(expressions, variables)
        except (NonlinearError, ValueError, TypeError):
            matrix = None
        # Parameter coefficients are safe when the elimination never assumes a pivot
        # or a consistency residual is nonzero.
        if (matrix is not None and len(variables) <= 6 and len(equations) <= 6
                and can_explain_linear_system(matrix, rhs)):
            explanation = linear_system_steps(matrix, rhs, variables, equations)
            steps.extend(explanation.pop("steps"))
            extra.update(explanation)
        else:
            explanation = nonlinear_system_steps(equations, variables)
            if explanation is not None:
                steps.extend(explanation.pop("steps"))
                extra.update(explanation)
            else:
                note = SUMMARY
    else:
        var = values[1]
        expression = residual(source)
        already_isolated = (isinstance(source, s.Equality) and isinstance(source.lhs, s.Function)
                            and source.lhs.func in (s.sin, s.cos, s.tan, s.exp, s.log) and not source.rhs.has(var))
        if not already_isolated: add_changed("Move all terms to the left", eq(expression))
        numerator, denominator = s.fraction(s.together(expression))
        if denominator != 1:
            if denominator.is_zero is not False:
                add("Exclude zero denominators", s.Ne(denominator, 0, evaluate=False))
            if denominator.is_zero is False and not denominator.has(var):
                # Clearing a known constant denominator adds no useful step
                # when the next operation simply isolates a function again.
                numerator = expression
            else:
                add_changed("Multiply by the nonzero denominator", eq(numerator))
        expanded = s.expand(numerator)
        if expanded != numerator: add_changed("Expand and collect like terms", eq(expanded))
        try:
            polynomial = s.Poly(expanded, var)
        except s.PolynomialError:
            polynomial = None
        if polynomial is not None and not polynomial.is_zero and polynomial.LC().is_zero is None:
            add("Assume the leading coefficient is nonzero", s.Ne(polynomial.LC(), 0, evaluate=False))
            note = "This derivation assumes the leading coefficient is nonzero; degenerate parameter cases require separate solving."

        def quadratic(poly, unknown=var):
            discriminant, roots = quadratic_candidates(poly)
            add("Compute the discriminant", eq(s.Symbol("D"), discriminant))
            add("Apply the quadratic formula", [eq(unknown, root) for root in roots])
            add_changed("Simplify the candidate roots", list(dict.fromkeys(eq(unknown, s.simplify(root)) for root in roots)))

        if polynomial is None:
            functions = [function for function in expanded.atoms(s.Function) if function.has(var)]
            function = functions[0] if len(functions) == 1 else None
            coefficient = expanded.coeff(function) if function is not None else 0
            rest = expanded-coefficient*function if function is not None else expanded
            supported = function is not None and function.func in (s.sin, s.cos, s.tan, s.exp, s.log)
            if (supported and coefficient.is_zero is False and not coefficient.has(var) and not rest.has(var)
                    and answer != s.S.EmptySet and answer != []):
                target = s.cancel(-rest/coefficient)
                add_changed("Isolate the function", eq(function, target))
                argument = function.args[0]
                inverse = {s.sin: s.asin, s.cos: s.acos, s.tan: s.atan, s.exp: s.log, s.log: s.exp}[function.func](target)
                if function.func == s.log:
                    add("Exponentiate both sides", eq(argument, inverse))
                    branches = [inverse]
                else:
                    add("Principal inverse value", eq(argument, inverse))
                    used = {str(symbol) for symbol in expanded.free_symbols}
                    name = "n"
                    while name in used: name += "1"
                    n = s.Symbol(name, integer=True)
                    if function.func == s.sin: branches = [inverse+2*s.pi*n, s.pi-inverse+2*s.pi*n]
                    elif function.func == s.cos: branches = [inverse+2*s.pi*n, -inverse+2*s.pi*n]
                    elif function.func == s.tan: branches = [inverse+s.pi*n]
                    else: branches = [inverse+2*s.pi*s.I*n]
                    add("Include periodic branches (n is an integer)", [eq(argument, branch) for branch in branches])
                try:
                    argument_poly = s.Poly(argument, var)
                except s.PolynomialError:
                    argument_poly = None
                if argument_poly is not None and argument_poly.degree() == 1:
                    a, b = argument_poly.all_coeffs()
                    if argument != var:
                        add("Isolate the variable in each branch", [eq(var, s.expand((branch-b)/a)) for branch in branches])
                else:
                    note = SUMMARY
            else:
                note = SUMMARY
        elif polynomial.is_zero or polynomial.degree() == 0:
            add("Identity: every allowed value is a solution" if expanded == 0 else "Contradiction: there is no solution", eq(expanded))
        elif polynomial.degree() == 1:
            a, b = polynomial.all_coeffs()
            add("Move the constant to the right", eq(a*var, -b))
            add("Divide by the coefficient of the variable", eq(var, s.cancel(-b/a)))
        elif polynomial.degree() == 2:
            quadratic(polynomial)
        elif polynomial.degree() <= 8:
            _, factors = s.factor_list(polynomial.as_expr(), var)
            common_power = 0
            for (exponent,), coefficient in polynomial.terms():
                if exponent and coefficient != 0: common_power = gcd(common_power, exponent)
            if common_power > 1 and polynomial.degree()//common_power <= 2:
                used = {str(symbol) for symbol in expanded.free_symbols}
                name = "t"
                while name in used: name += "1"
                t = s.Symbol(name)
                reduced = s.Poly(sum(coefficient*t**(exponent//common_power)
                                     for (exponent,), coefficient in polynomial.terms()), t)
                add("Substitute a power of the variable", eq(t, var**common_power))
                add("Solve the reduced polynomial", eq(reduced.as_expr()))
                if reduced.degree() == 2:
                    quadratic(reduced, t)
                else:
                    a, b = reduced.all_coeffs()
                    add("Divide by the coefficient of the variable", eq(t, s.cancel(-b/a)))
                name = "k"
                while name in used or name == str(t): name += "1"
                k = s.Symbol(name, integer=True)
                add("Recover roots of the original variable", [eq(var**common_power, t),
                    eq(var, t**s.Rational(1, common_power)*s.exp(2*s.pi*s.I*k/common_power)),
                    s.And(s.Ge(k, 0), s.Lt(k, common_power))])
            elif len(factors) > 1 or factors[0][1] > 1:
                factored = s.Mul(*(s.Pow(factor, power, evaluate=False) if power > 1 else factor for factor, power in factors), evaluate=False)
                add("Factor the polynomial", eq(factored))
                add("Set each factor equal to zero", [eq(factor) for factor, _ in factors])
                for factor, _ in factors:
                    part = s.Poly(factor, var)
                    if part.degree() == 1:
                        a, b = part.all_coeffs()
                        add("Solve the linear factor", eq(var, -b/a))
                    elif part.degree() == 2:
                        quadratic(part)
                    else:
                        note = SUMMARY
            elif polynomial.degree() == 3:
                a, b, c, d = polynomial.all_coeffs()
                used = {str(symbol) for symbol in expanded.free_symbols} | {str(var)}
                def auxiliary(name):
                    candidate = name
                    while candidate in used:
                        candidate += "1"
                    used.add(candidate)
                    return s.Symbol(candidate)
                t, u, v = [auxiliary(name) for name in ("t", "u", "v")]
                p = s.cancel((3*a*c-b*b)/(3*a*a))
                q = s.cancel((2*b**3-9*a*b*c+27*a*a*d)/(27*a**3))
                add("Remove the quadratic term", eq(var, t-b/(3*a)))
                add("Depressed cubic", eq(t**3+p*t+q))
                delta = s.factor((q/2)**2+(p/3)**3)
                add("Cardano discriminant", eq(s.Symbol("Delta"), delta))
                add("Cardano substitution", [eq(t, u+v), eq(u*v, -p/3)])
                add("Choose cube roots satisfying the product constraint", [eq(u**3, -q/2+s.sqrt(delta)), eq(v**3, -q/2-s.sqrt(delta))])
                add("Substitute back", eq(var, u+v-b/(3*a)))
            else:
                note = SUMMARY
        else:
            note = SUMMARY
    if engine.conditions:
        add("Check the original domain restrictions", list(dict.fromkeys(engine.conditions)))
    add("Solution" if not getattr(answer, "has", lambda *_: False)(s.ConditionSet) else "Unresolved solution set", answer)
    steps[-1]["tree"] = equation_solution_tree(steps[-1]["tree"])
    if engine.note:
        note = (note+"\n" if note else "")+engine.note
    for step in steps:
        if step["title"] in EXPLANATIONS: step["explanation"] = EXPLANATIONS[step["title"]]
    return {"steps": steps, "note": note, **extra}
