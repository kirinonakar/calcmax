"""Equation-first explanations, with an optional matrix derivation."""
import copy
import sympy as s
from calc_display import display_tree, readable


def quadratic_candidates(poly):
    """Keep both signs unevaluated until the separate root simplification step."""
    a, b, c = poly.all_coeffs()
    discriminant = s.expand(b*b-4*a*c)
    radical = s.Pow(discriminant, s.Rational(1, 2), evaluate=False)
    roots = [s.Mul(s.Add(-b, radical if sign == 1 else s.Mul(-1, radical, evaluate=False), evaluate=False),
                   s.Pow(2*a, -1, evaluate=False), evaluate=False) for sign in (1, -1)]
    return discriminant, roots


def symmetric_quadratic_system_steps(polynomials, variables):
    """Recover both variables from their squared sum and product."""
    x, y = variables
    allowed = {(2, 0), (0, 2), (1, 1), (0, 0)}
    if any(set(poly.monoms())-allowed or poly.coeff_monomial(x*x) != poly.coeff_monomial(y*y)
           for poly in polynomials):
        return None
    a, b = [polynomials[0].coeff_monomial(term) for term in (x*x, x*y)]
    c, d = [polynomials[1].coeff_monomial(term) for term in (x*x, x*y)]
    determinant = s.cancel(a*d-b*c)
    if determinant.is_zero is not False: return None
    r, t = [-poly.coeff_monomial(1) for poly in polynomials]
    squares, product = s.cancel((r*d-b*t)/determinant), s.cancel((a*t-r*c)/determinant)
    if any(s.count_ops(value) > 30 for value in (squares, product)): return None
    used = {str(symbol) for poly in polynomials for symbol in poly.as_expr().free_symbols}
    def fresh(name):
        while name in used: name += "1"
        symbol = s.Symbol(name)
        used.add(name)
        return symbol
    u, v = fresh("u"), fresh("v")
    steps = []
    def eq(left, right): return s.Eq(left, right, evaluate=False)
    def add(title, explanation, values):
        steps.append({"title": title, "explanation": explanation,
                      "equations": [{"exact": readable(value), "tree": display_tree(value)}
                                    for value in dict.fromkeys(values)]})
    if {poly.as_expr() for poly in polynomials} != {x*x+y*y-squares, x*y-product}:
        add("Determine the sum of squares and the product", "Treat the sum of squares and the product as two quantities and combine the equations to determine them.",
            [eq(x*x+y*y, squares), eq(x*y, product)])
    add("Introduce the sum and difference", "Use the sum and difference of the two variables as new unknowns.", [eq(u, x+y), eq(v, x-y)])
    add("Form the squared sum and difference", "Expand the squares of the sum and difference. Their cross terms are twice the product of the original variables.",
        [eq((x+y)**2, x*x+2*x*y+y*y), eq((x-y)**2, x*x-2*x*y+y*y)])
    plus, minus = s.simplify(squares+2*product), s.simplify(squares-2*product)
    add("Substitute the known quadratic values", "Insert the known sum of squares and product, then simplify each squared equation.",
        [eq(u*u, s.Add(squares, s.Mul(2, product, evaluate=False), evaluate=False)), eq(u*u, plus),
         eq(v*v, s.Add(squares, s.Mul(-2, product, evaluate=False), evaluate=False)), eq(v*v, minus)])
    sums = list(dict.fromkeys(s.simplify(sign*s.sqrt(plus)) for sign in (1, -1)))
    differences = list(dict.fromkeys(s.simplify(sign*s.sqrt(minus)) for sign in (1, -1)))
    add("Take both square-root signs", "Each squared equation gives both square-root signs. Keep every independent combination of these candidates.",
        [*[eq(u, value) for value in sums], *[eq(v, value) for value in differences]])
    add("Recover the original variables", "Add and subtract the sum and difference equations, then divide by two.", [eq(x, (u+v)/2), eq(y, (u-v)/2)])
    pairs = [s.Tuple(eq(x, s.simplify((total+difference)/2)), eq(y, s.simplify((total-difference)/2)))
             for total in sums for difference in differences]
    add("Combine the candidate solution pairs", "Use each sum and difference combination to obtain a pair. The final solution applies the original variable and domain restrictions.", pairs)
    return {"method": "Sum and difference method", "steps": steps}


def polynomial_elimination_steps(equations, variables, answer):
    """Show an exact elimination basis and verify the solver's finite candidates.

    This explains the reduction and back-substitution, not Buchberger's entire
    internal polynomial-division trace. No additional solve is performed.
    """
    expressions = [item.lhs-item.rhs for item in equations]
    try:
        polys = [s.Poly(expr, *variables) for expr in expressions]
        if any(poly.total_degree() > 3 or len(poly.terms()) > 12
               or not all(c.is_Rational for c in poly.coeffs()) for poly in polys): return None
        basis = s.groebner(expressions, *variables, order='lex')
    except (s.PolynomialError, ValueError):
        return None
    if not basis.is_zero_dimensional or len(basis.polys) > 6: return None
    if any(s.count_ops(poly.as_expr()) > 80 for poly in basis.polys): return None
    remaining = variables[-1]
    eliminated = [poly.as_expr() for poly in basis.polys if not poly.as_expr().has(*variables[:-1])]
    if not eliminated or any(s.degree(expr, remaining) > 6 for expr in eliminated): return None
    if not isinstance(answer, list) or len(answer) > 16 or not all(isinstance(item, dict) for item in answer): return None
    steps = []
    def eq(left, right=0): return s.Eq(left, right, evaluate=False)
    def add(title, explanation, formulas):
        steps.append({'title': title, 'explanation': explanation,
                      'equations': [{'exact': readable(value), 'tree': display_tree(value)} for value in formulas]})
    add('Eliminate variables with a polynomial basis', 'Polynomial division and combinations of the equations give an equivalent lexicographic basis. The displayed basis exposes an equation in one unknown; internal polynomial-division steps are omitted.', [eq(poly.as_expr()) for poly in basis.polys])
    add('Solve the reduced polynomial', 'The last-variable equation supplies candidates. Use the remaining basis equations to recover the other variables, then check every candidate in the original system.', [eq(expr) for expr in eliminated])
    for candidate in answer:
        if not all(variable in candidate for variable in variables): return None
        if sum(s.count_ops(value) for value in candidate.values()) > 40: return None
        residuals = [s.simplify(expr.subs(candidate, simultaneous=True)) for expr in expressions]
        if any(value != 0 for value in residuals): return None
        add('Back-substitute each candidate root', 'Insert this candidate into the remaining equations to recover its matching values.', [eq(variable, candidate[variable]) for variable in reversed(variables)])
        add('Check candidates in the original equation', 'Substitution into every original equation gives zero residual. Retain the original domain restrictions.', [eq(expr.subs(candidate, simultaneous=True), 0) for expr in expressions])
    if not answer:
        add('No candidates satisfy the selected domain', 'The solver returned no solutions in the selected domain after applying the original equations and restrictions.', [s.EmptySet])
    return {'method': 'Polynomial elimination method', 'steps': steps}


def nonlinear_system_steps(equations, variables, answer=None):
    """Explain two-variable polynomial systems reducible to degree at most two.

    Derive candidates algebraically; the caller keeps the solver's actual
    answer and original domain restrictions as the final conclusion.
    """
    if len(equations) != 2 or len(variables) != 2: return None
    equations = [item if isinstance(item, s.Equality) else s.Eq(item, 0, evaluate=False) for item in equations]
    try:
        polynomials = [s.Poly(item.lhs-item.rhs, *variables) for item in equations]
    except s.PolynomialError:
        return None
    linear_index = next((index for index, poly in enumerate(polynomials) if poly.total_degree() == 1), None)
    if linear_index is None:
        return (symmetric_quadratic_system_steps(polynomials, variables)
                or polynomial_elimination_steps(equations, variables, answer))
    linear, other = polynomials[linear_index], equations[1-linear_index]
    columns = [index for index, var in enumerate(variables) if linear.coeff_monomial(var).is_zero is False]
    if not columns: return None
    column = min(columns, key=lambda index: (linear.coeff_monomial(variables[index]) not in (1, -1), -index))
    isolated, remaining = variables[column], variables[1-column]
    coefficient = linear.coeff_monomial(isolated)
    replacement = s.cancel(-(linear.as_expr()-coefficient*isolated)/coefficient)
    substituted = s.Eq(other.lhs.subs(isolated, replacement), other.rhs.subs(isolated, replacement), evaluate=False)
    expanded = s.expand(substituted.lhs-substituted.rhs)
    try:
        reduced = s.Poly(expanded, remaining)
    except s.PolynomialError:
        return None
    if s.count_ops(expanded) > 60 or reduced.degree() > 2:
        return polynomial_elimination_steps(equations, variables, answer)
    if not reduced.is_zero and reduced.LC().is_zero is not False: return None

    steps = []
    def eq(left, right=0): return s.Eq(left, right, evaluate=False)
    def add(title, explanation, values):
        steps.append({"title": title, "explanation": explanation,
                      "equations": [{"exact": readable(value), "tree": display_tree(value)} for value in values]})
    relation = eq(isolated, replacement)
    add("Isolate a variable in the linear equation", "Rearrange the linear equation so one variable is expressed in terms of the other.", [relation])
    add("Substitute into the other equation", "Replace the isolated variable with its expression to obtain an equation with one unknown.", [substituted])
    normalized = eq(expanded)
    if display_tree(substituted) != display_tree(normalized):
        add("Expand and collect like terms", "", [normalized])
    if reduced.is_zero:
        add("The equations describe the same relation", "Substitution gives an identity. One variable can be chosen freely, and the other follows from the linear equation.", [eq(0), relation])
    elif reduced.degree() == 0:
        add("The equations conflict", "Substitution gives a false equality. No values can satisfy both equations at once.", [normalized])
    else:
        if reduced.degree() == 2:
            discriminant, raw = quadratic_candidates(reduced)
            add("Compute the discriminant", "", [eq(s.Symbol("D"), discriminant)])
            add("Apply the quadratic formula", "", [eq(remaining, value) for value in raw])
            roots = list(dict.fromkeys(s.simplify(value) for value in raw))
            add("Simplify the candidate roots", "", [eq(remaining, value) for value in roots])
        else:
            a, b = reduced.all_coeffs()
            roots = [s.cancel(-b/a)]
            add("Solve the equation with one unknown", "Move the constant and divide by the nonzero variable coefficient.", [eq(remaining, roots[0])])
        pairs = []
        for root in roots:
            value = s.simplify(replacement.subs(remaining, root))
            pairs.append(s.Tuple(eq(remaining, root), eq(isolated, value)))
        add("Back-substitute each candidate root", "Substitute each candidate into the linear relation to find its matching value of the other variable.", [relation, *pairs])
    return {"method": "Substitution method", "steps": steps}


def can_explain_linear_system(matrix, rhs):
    """Only proceed when every pivot and consistency decision is certain."""
    reduced = matrix.row_join(rhs).applyfunc(s.cancel)
    row = 0
    for column in range(matrix.cols):
        pivot = next((index for index in range(row, reduced.rows)
                      if reduced[index, column].is_zero is False), None)
        if pivot is None:
            if any(reduced[index, column].is_zero is None for index in range(row, reduced.rows)):
                return False
            continue
        reduced.row_swap(row, pivot)
        divisor = reduced[row, column]
        for index in range(row+1, reduced.rows):
            multiplier = s.cancel(reduced[index, column]/divisor)
            reduced.row_op(index, lambda value, j: s.cancel(value-multiplier*reduced[row, j]))
        if any(s.count_ops(value) > 60 for value in reduced):
            return False
        row += 1
        if row == reduced.rows: break
    return all(reduced[index, -1].is_zero is not None for index in range(row, reduced.rows))


def linear_system_steps(matrix, rhs, variables, equations):
    # Use the same normalized coefficients as the pivot preflight.
    matrix, rhs = matrix.applyfunc(s.cancel), rhs.applyfunc(s.cancel)
    basic, advanced = [], []

    def equation(left, right=0):
        return s.Eq(left, right, evaluate=False)

    def formula(value):
        return {"exact": readable(value), "tree": display_tree(value)}

    def add(target, title, explanation, values=()):
        step = {"title": title, "explanation": explanation, "equations": [formula(value) for value in values]}
        target.append(step)
        return step

    def describe(step, parts):
        # Translate each template before inserting mathematical values in the UI.
        step["explanationParts"] = parts
        step["explanation"] = " ".join(part["text"].format(**part.get("values", {})) for part in parts)

    def subtract(term):
        negative = term.could_extract_minus_sign()
        return {"text": "Add {term} to both sides." if negative else "Subtract {term} from both sides.",
                "values": {"term": readable(-term if negative else term)}}

    def divide(coefficient):
        return ({"text": "Multiply both sides by −1."} if coefficient == -1 else
                {"text": "Divide both sides by {coefficient}.", "values": {"coefficient": readable(coefficient)}})

    def distinct(values):
        return list(dict.fromkeys(values))

    def row_equation(augmented, index):
        return equation(sum(augmented[index, j]*variable for j, variable in enumerate(variables)), augmented[index, -1])

    if matrix.rows == 2 and matrix.cols == 2 and any(matrix[0, j].is_zero is False for j in range(2)):
        # Prefer a coefficient of ±1, and y over x when equally simple.
        column = min((j for j in range(2) if matrix[0, j].is_zero is False), key=lambda j: (matrix[0, j] not in (1, -1), -j))
        other = 1-column
        isolated, remaining = variables[column], variables[other]
        replacement = s.cancel((rhs[0]-matrix[0, other]*remaining)/matrix[0, column])
        relation = equation(isolated, replacement)
        original_first = equations[0] if isinstance(equations[0], s.Equality) else equation(equations[0])
        first_left, first_right = s.expand(original_first.lhs), s.expand(original_first.rhs)
        right_coefficient = first_right.coeff(isolated)
        left_rest = s.expand(first_left-first_left.coeff(isolated)*isolated)
        parts = []
        if right_coefficient != 0: parts.append(subtract(right_coefficient*isolated))
        if left_rest != 0: parts.append(subtract(left_rest))
        if parts: parts.append({"text": "Combine like terms."})
        if matrix[0, column] != 1: parts.append(divide(matrix[0, column]))
        parts.append({"text": "Use this expression in the other equation."})
        describe(add(basic, "Rearrange the first equation", "", [relation]), parts)
        original = equations[1] if isinstance(equations[1], s.Equality) else equation(equations[1])
        # Replace in the display tree to preserve x - 2x before simplification.
        def replace_tree(node, variable, value):
            if node.get("kind") == "symbol" and node.get("value") == str(variable):
                return copy.deepcopy(display_tree(value))
            children = [replace_tree(child, variable, value) for child in node.get("args", [])]
            if node.get("kind") in ("product", "unary") and node != {**node, "args": children}:
                # Keep a substituted sum or negative operand grouped under
                # multiplication and negation, before any simplification.
                children = [{"kind": "parentheses", "args": [child]} if
                            child.get("kind") in ("sum", "unary") or child.get("value", "").startswith("-")
                            else child for child in children]
                if node.get("kind") == "product":
                    return {**node, "displayOperator": "×", "args": children}
            return {**node, "args": children}
        shown = replace_tree(display_tree(original), isolated, replacement)
        step = add(basic, "Substitute into the second equation", "Replace the isolated variable with its expression. The second equation now has only one unknown.")
        step["equations"].append({"exact": readable(original.subs(isolated, replacement)), "tree": shown})
        left, right = s.expand(original.lhs.subs(isolated, replacement)), s.expand(original.rhs.subs(isolated, replacement))
        reduced = s.Poly(left-right, remaining)
        a, b = s.cancel(reduced.coeff_monomial(remaining)), s.cancel(reduced.coeff_monomial(1))
        if a.is_zero is False:
            root = s.cancel(-b/a)
            parts = []
            if shown != display_tree(equation(left, right)): parts.append({"text": "Combine like terms."})
            if right.coeff(remaining) != 0: parts.append(subtract(right.coeff(remaining)*remaining))
            if left.coeff(remaining, 0) != 0: parts.append(subtract(left.coeff(remaining, 0)))
            if a != 1: parts.append(divide(a))
            if not parts: parts.append({"text": "The variable is already isolated."})
            describe(add(basic, "Solve the equation with one unknown", "", distinct([equation(left, right), equation(a*remaining, -b), equation(remaining, root)])), parts)
            substituted_tree = replace_tree(display_tree(relation), remaining, root)
            last = add(basic, "Calculate the remaining variable", "Put the value we found back into the rearranged first equation to find the other variable.")
            last["equations"] = [{"exact": readable(relation.subs(remaining, root)), "tree": substituted_tree}, formula(equation(isolated, s.simplify(replacement.subs(remaining, root))))]
        elif b == 0:
            add(basic, "The equations describe the same relation", "Substitution gives an identity. One variable can be chosen freely, and the other follows from the first equation.", [equation(0, 0), relation])
        else:
            add(basic, "The equations conflict", "Substitution gives a false equality. No values can satisfy both equations at once.", [equation(0, -b)])
        method = "Substitution method"
    else:
        method = "Elimination method"
        reduced = matrix.row_join(rhs)
        add(basic, "Collect variable terms on the left", "Put matching variables on the same side so we can combine equations and remove one unknown at a time.", [row_equation(reduced, index) for index in range(reduced.rows)])
        pivots = []
        row = 0
        for column in range(matrix.cols):
            pivot = next((index for index in range(row, reduced.rows) if reduced[index, column].is_zero is False), None)
            if pivot is None: continue
            if pivot != row: reduced.row_swap(row, pivot)
            divisor = reduced[row, column]
            for index in range(row+1, reduced.rows):
                if reduced[index, column] == 0: continue
                multiplier = s.cancel(reduced[index, column]/divisor)
                before, pivot_equation = row_equation(reduced, index), row_equation(reduced, row)
                # Preserve the operation, then expand each coefficient from
                # the same rows used by elimination without combining terms.
                scaled = -multiplier
                terms = [reduced[index, j]*variable for j, variable in enumerate(variables)
                         if reduced[index, j] != 0]
                terms += [s.cancel(scaled*reduced[row, j])*variable for j, variable in enumerate(variables)
                          if reduced[row, j] != 0]
                combined_rhs = s.Add(before.rhs, scaled*pivot_equation.rhs, evaluate=False)
                scaled_left = pivot_equation.lhs if scaled == 1 else s.Mul(scaled, pivot_equation.lhs, evaluate=False)
                operation = equation(s.Add(before.lhs, scaled_left, evaluate=False), combined_rhs)
                expanded = equation(s.Add(*terms, evaluate=False), combined_rhs)
                reduced.row_op(index, lambda value, j: s.cancel(value-multiplier*reduced[row, j]))
                add(basic, "Eliminate one variable", "Subtract a suitable multiple of another equation so one variable disappears. This leaves fewer unknowns to solve.", distinct([operation, expanded, row_equation(reduced, index)]))
            pivots.append((row, column))
            row += 1
            if row == reduced.rows: break
        conflict = next((index for index in range(reduced.rows) if all(reduced[index, j] == 0 for j in range(matrix.cols)) and reduced[index, -1] != 0), None)
        if conflict is not None:
            add(basic, "The equations conflict", "Elimination gives a false equality. No values can satisfy all the equations at once.", [row_equation(reduced, conflict)])
        else:
            known = {}
            for index, column in reversed(pivots):
                variable = variables[column]
                rest = sum(reduced[index, j]*variables[j] for j in range(matrix.cols) if j != column)
                value = s.cancel((reduced[index, -1]-rest.subs(known))/reduced[index, column])
                add(basic, "Back-substitute into an earlier equation", "Start with the equation with the fewest unknowns. Substitute each value into earlier equations to find the remaining variables.", [row_equation(reduced, index), equation(variable, value)])
                known[variable] = value
            if len(pivots) < matrix.cols:
                add(basic, "Some variables are free", "The independent equations do not determine every variable. Free variables describe the family of solutions.")

    augmented = matrix.row_join(rhs)
    def snapshot(title, explanation, operation=None):
        step = add(advanced, title, explanation, [augmented.copy()])
        step["equations"][0]["tree"]["augmentedColumn"] = len(variables)
        if operation is not None: step["operation"] = operation
    snapshot("Augmented matrix", "The columns follow the variable order shown below; the last column contains the constants.")
    advanced[0]["variableOrder"] = formula(variables)
    row = 0
    for column in range(augmented.cols):
        pivot = next((index for index in range(row, augmented.rows) if augmented[index, column].is_zero is False), None)
        if pivot is None: continue
        if pivot != row:
            augmented.row_swap(row, pivot)
            snapshot("Swap rows", "Swap the whole equations to place a nonzero coefficient in the pivot position.", {"kind": "relation", "value": "↔", "args": [display_tree(s.Symbol(f"R{row+1}")), display_tree(s.Symbol(f"R{pivot+1}"))]})
        divisor = augmented[row, column]
        if divisor != 1:
            augmented.row_op(row, lambda value, _: s.cancel(value/divisor))
            snapshot("Scale the pivot row", "Multiply every entry in this row by the same number so the pivot coefficient becomes 1.", {"kind": "relation", "value": "←", "args": [display_tree(s.Symbol(f"R{row+1}")), display_tree(s.Mul(1/divisor, s.Symbol(f"R{row+1}"), evaluate=False))]})
        for index in range(augmented.rows):
            multiplier = augmented[index, column]
            if index == row or multiplier == 0: continue
            augmented.row_op(index, lambda value, j: s.cancel(value-multiplier*augmented[row, j]))
            snapshot("Eliminate the pivot column from another row", "Subtract a multiple of the pivot row from the entire other row. Its entry in this column becomes 0.", {"kind": "relation", "value": "←", "args": [display_tree(s.Symbol(f"R{index+1}")), display_tree(s.Add(s.Symbol(f"R{index+1}"), -multiplier*s.Symbol(f"R{row+1}"), evaluate=False))]})
        row += 1
        if row == augmented.rows: break
    conflict = next((index for index in range(augmented.rows)
                     if all(augmented[index, j] == 0 for j in range(len(variables)))
                     and augmented[index, -1] != 0), None)
    if conflict is not None:
        add(advanced, "The equations conflict", "A row has zero variable coefficients but a nonzero constant. It represents a false equality, so there is no solution.", [row_equation(augmented, conflict)])
    else:
        relations = []
        for index in range(augmented.rows):
            column = next((j for j in range(len(variables)) if augmented[index, j] != 0), None)
            if column is not None:
                rest = sum(augmented[index, j]*variables[j] for j in range(column+1, len(variables)))
                relations.append(equation(variables[column], s.cancel((augmented[index, -1]-rest)/augmented[index, column])))
        explanation = ("Each pivot row gives one variable's value in the last column. Read the values in the displayed variable order."
                       if len(relations) == len(variables) else
                       "Read each pivot variable in terms of the variables without pivots. Those variables are free, so there are infinitely many solutions.")
        add(advanced, "Read the solution from the matrix", explanation, relations)
    return {"method": method, "steps": basic, "advancedSteps": advanced}
