"""Equation-first explanations, with an optional matrix derivation."""
import copy
import sympy as s
from calc_display import display_tree, readable


def linear_system_steps(matrix, rhs, variables, equations):
    basic, advanced = [], []

    def equation(left, right=0):
        return s.Eq(left, right, evaluate=False)

    def formula(value):
        return {"exact": readable(value), "tree": display_tree(value)}

    def add(target, title, explanation, values=()):
        step = {"title": title, "explanation": explanation, "equations": [formula(value) for value in values]}
        target.append(step)
        return step

    def row_equation(augmented, index):
        return equation(sum(augmented[index, j]*variable for j, variable in enumerate(variables)), augmented[index, -1])

    if matrix.rows == 2 and matrix.cols == 2 and any(matrix[0, j] != 0 for j in range(2)):
        # Prefer a coefficient of ±1, and y over x when equally simple.
        column = min((j for j in range(2) if matrix[0, j] != 0), key=lambda j: (matrix[0, j] not in (1, -1), -j))
        other = 1-column
        isolated, remaining = variables[column], variables[other]
        replacement = s.cancel((rhs[0]-matrix[0, other]*remaining)/matrix[0, column])
        relation = equation(isolated, replacement)
        add(basic, "Rearrange the first equation", "Move the other terms to the opposite side and divide by the coefficient. We can then replace this variable in the other equation.", [equations[0], relation])
        original = equations[1] if isinstance(equations[1], s.Equality) else equation(equations[1])
        # Replace in the display tree to preserve x - 2x before simplification.
        def replace_tree(node, variable, value):
            if node.get("kind") == "symbol" and node.get("value") == str(variable):
                return copy.deepcopy(display_tree(value))
            return {**node, "args": [replace_tree(child, variable, value) for child in node.get("args", [])]}
        shown = replace_tree(display_tree(original), isolated, replacement)
        step = add(basic, "Substitute into the second equation", "Replace the isolated variable with its expression. The second equation now has only one unknown.")
        step["equations"].append({"exact": readable(original.subs(isolated, replacement)), "tree": shown})
        left, right = s.expand(original.lhs.subs(isolated, replacement)), s.expand(original.rhs.subs(isolated, replacement))
        reduced = s.Poly(left-right, remaining)
        a, b = reduced.coeff_monomial(remaining), reduced.coeff_monomial(1)
        if a != 0:
            root = s.cancel(-b/a)
            add(basic, "Solve the equation with one unknown", "Combine like terms, move the constant to the right, and divide by the coefficient to find the first value.", [equation(left, right), equation(a*remaining, -b), equation(remaining, root)])
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
            pivot = next((index for index in range(row, reduced.rows) if reduced[index, column] != 0), None)
            if pivot is None: continue
            if pivot != row: reduced.row_swap(row, pivot)
            divisor = reduced[row, column]
            for index in range(row+1, reduced.rows):
                if reduced[index, column] == 0: continue
                multiplier = s.cancel(reduced[index, column]/divisor)
                before, pivot_equation = row_equation(reduced, index), row_equation(reduced, row)
                reduced.row_op(index, lambda value, j: s.expand(value-multiplier*reduced[row, j]))
                combined = equation(s.Add(before.lhs, -multiplier*pivot_equation.lhs, evaluate=False), s.Add(before.rhs, -multiplier*pivot_equation.rhs, evaluate=False))
                add(basic, "Eliminate one variable", "Subtract a suitable multiple of another equation so one variable disappears. This leaves fewer unknowns to solve.", [combined, row_equation(reduced, index)])
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
        pivot = next((index for index in range(row, augmented.rows) if augmented[index, column] != 0), None)
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
            augmented.row_op(index, lambda value, j: s.expand(value-multiplier*augmented[row, j]))
            snapshot("Eliminate the pivot column from another row", "Subtract a multiple of the pivot row from the entire other row. Its entry in this column becomes 0.", {"kind": "relation", "value": "←", "args": [display_tree(s.Symbol(f"R{index+1}")), display_tree(s.Add(s.Symbol(f"R{index+1}"), -multiplier*s.Symbol(f"R{row+1}"), evaluate=False))]})
        row += 1
        if row == augmented.rows: break
    return {"method": method, "steps": basic, "advancedSteps": advanced}
