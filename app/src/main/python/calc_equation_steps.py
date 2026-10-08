"""Inspectable algebra steps, using the solver's evaluated input and final answer.

No second solve is run, and every transformation preserves the original domain.
Unsupported symbolic work is explicitly described as a solver summary.
"""
import sympy as s
from sympy.solvers.solveset import NonlinearError
from sympy.core.relational import Relational
from calc_display import display_tree, readable
from calc_equation_system_steps import linear_system_steps


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
    "Exclude zero denominators": "Division by zero is undefined. Keep this restriction when removing the denominator so invalid roots are not included.",
    "Multiply by the nonzero denominator": "Multiply both sides by the denominator. This is valid only where the denominator is nonzero.",
    "Factor the polynomial": "Rewrite the polynomial as a product. A product is zero when at least one factor is zero.",
    "Set each factor equal to zero": "Solve the smaller equations separately, then combine their allowed roots.",
    "Solve the linear factor": "Move the constant and divide by the variable coefficient in this factor.",
    "Keep solutions allowed by the original equation and domain": "Discard candidates excluded by the original equation, denominators or variable assumptions.",
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

    def residual(value):
        return value.lhs-value.rhs if isinstance(value, s.Equality) else value

    source = values[0]
    add("Original equation", source)
    note = ""
    expressions = source if isinstance(source, list) else [source]
    if any(s.count_ops(item) > 160 or any(power.exp.is_Integer and abs(power.exp) > 8 for power in item.atoms(s.Pow)) for item in expressions):
        note = LIMIT
    elif isinstance(source, Relational) and not isinstance(source, s.Equality):
        note = SUMMARY
    elif method in ("dsolve", "desolve", "pdsolve"):
        expression = residual(source)
        add("Move all terms to the left", eq(expression))
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
                    # A bounded polynomial antiderivative keeps this explanation inexpensive.
                    if p.is_polynomial(variable) and s.Poly(p, variable).degree() <= 2:
                        mu = s.exp(s.integrate(p, variable))
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
        if method in ("dsolve", "desolve") and len(values) == 4:
            add("Apply initial conditions", values[3])
    elif method == "nsolve":
        var = values[1]
        expression = residual(source)
        add("Move all terms to the left", eq(expression))
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
        # Explicit numerical pivots avoid introducing unrecorded parameter assumptions.
        if (matrix is not None and len(variables) <= 6 and len(equations) <= 6
                and all(item.is_number for item in matrix) and all(item.is_number for item in rhs)):
            explanation = linear_system_steps(matrix, rhs, variables, equations)
            steps.extend(explanation.pop("steps"))
            extra.update(explanation)
        else:
            note = SUMMARY
    else:
        var = values[1]
        expression = residual(source)
        add("Move all terms to the left", eq(expression))
        numerator, denominator = s.fraction(s.together(expression))
        if denominator != 1:
            add("Exclude zero denominators", s.Ne(denominator, 0, evaluate=False))
            add("Multiply by the nonzero denominator", eq(numerator))
        expanded = s.expand(numerator)
        add("Expand and collect like terms", eq(expanded))
        try:
            polynomial = s.Poly(expanded, var)
        except s.PolynomialError:
            polynomial = None
        if polynomial is not None and not polynomial.is_zero and polynomial.LC().is_zero is None:
            add("Assume the leading coefficient is nonzero", s.Ne(polynomial.LC(), 0, evaluate=False))
            note = "This derivation assumes the leading coefficient is nonzero; degenerate parameter cases require separate solving."

        def quadratic(poly):
            a, b, c = poly.all_coeffs()
            discriminant = s.expand(b*b-4*a*c)
            add("Compute the discriminant", eq(s.Symbol("D"), discriminant))
            # Unevaluated operations keep the formula visible even for double roots.
            radical = s.Pow(discriminant, s.Rational(1, 2), evaluate=False)
            roots = [s.Mul(s.Add(-b, sign*radical, evaluate=False), s.Pow(2*a, -1, evaluate=False), evaluate=False) for sign in (1, -1)]
            add("Apply the quadratic formula", [eq(var, root) for root in roots])

        if polynomial is None:
            functions = [function for function in expanded.atoms(s.Function) if function.has(var)]
            function = functions[0] if len(functions) == 1 else None
            coefficient = expanded.coeff(function) if function is not None else 0
            rest = expanded-coefficient*function if function is not None else expanded
            supported = function is not None and function.func in (s.sin, s.cos, s.tan, s.exp, s.log)
            if supported and coefficient.is_zero is False and not coefficient.has(var) and not rest.has(var) and answer != s.S.EmptySet:
                target = s.cancel(-rest/coefficient)
                add("Isolate the function", eq(function, target))
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
        elif polynomial.degree() <= 4 and all(coefficient.is_Rational for coefficient in polynomial.all_coeffs()):
            _, factors = s.factor_list(polynomial.as_expr(), var)
            if len(factors) > 1 or factors[0][1] > 1:
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
    if method == "solve" and not isinstance(source, list) and not isinstance(values[1], list):
        add("Keep solutions allowed by the original equation and domain")
    add("Solution" if not getattr(answer, "has", lambda *_: False)(s.ConditionSet) else "Unresolved solution set", answer)
    steps[-1]["tree"] = equation_solution_tree(steps[-1]["tree"])
    if engine.note:
        note = (note+"\n" if note else "")+engine.note
    for step in steps:
        if step["title"] in EXPLANATIONS: step["explanation"] = EXPLANATIONS[step["title"]]
    return {"steps": steps, "note": note, **extra}
