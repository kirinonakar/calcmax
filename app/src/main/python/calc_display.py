"""Human-readable results, display trees, and lossless result ASTs."""
import sympy as s
from sympy.core.relational import Relational
from quantities import Quantity
from calc_shared import MathError, dms_parts, require

def is_dms_expression(node, variables=None):
    if not isinstance(node, dict): return False
    kind=node.get("kind")
    if kind == "sexagesimal": return True
    if kind == "frozen_call" and node.get("value") == "sexagesimal": return True
    if kind in ("group", "restricted", "unary"):
        return bool(node.get("args")) and is_dms_expression(node["args"][0], variables)
    if kind == "symbol" and node.get("value") == "Ans":
        return is_dms_expression((variables or {}).get("Ans", {}), variables)
    if kind == "binary" and node.get("value") in ("+", "-", "*", "/"):
        return any(is_dms_expression(arg, variables) for arg in node.get("args", []))
    return False

def display_tree(x):
    def t(kind,value="",args=()): return {"kind":kind,"value":value,"args":list(args)}
    def negative(value):
        coefficient, factors = value.as_coeff_Mul()
        if coefficient.is_negative and factors != 1:
            positive = factors if coefficient == -1 else s.Mul(-coefficient, factors, evaluate=False)
        else:
            positive = -value
        argument = display_tree(positive)
        if isinstance(positive,s.Add): argument=t("parentheses",args=[argument])
        return t("unary","-",[argument])
    if isinstance(x,Quantity): return t("quantity",x.unit_text(),[display_tree(x.base)])
    if isinstance(x,dict): return t("rows",args=[t("row",str(k),[display_tree(v)]) for k,v in x.items()])
    if isinstance(x,s.ImageSet) and len(x.lamda.variables)==1 and x.base_set==s.S.Integers:
        return t("rows",args=[t("row","solutions",[display_tree(x.lamda.expr)]),
                              t("row","branch",[t("text",str(x.lamda.variables[0])+" ∈ ℤ")])])
    if isinstance(x,(list,tuple,s.Tuple)): return t("list",args=[display_tree(v) for v in x])
    if isinstance(x,s.MatrixBase): return t("matrix",args=[t("list",args=[display_tree(x[i,j]) for j in range(x.cols)]) for i in range(x.rows)])
    if isinstance(x,s.Integral):
        tree=display_tree(x.function)
        for limit in x.limits:
            tree=t("call","integrate",[tree]+[display_tree(item) for item in limit])
        return tree
    if isinstance(x,s.Derivative):
        tree=display_tree(x.expr)
        for variable,count in x.variable_count:
            tree=t("call","diff",[tree,display_tree(variable)]+([display_tree(count)] if count!=1 else []))
        return tree
    if isinstance(x,s.Limit):
        args=[display_tree(item) for item in x.args[:3]]
        if str(x.args[3]) in ("+","-") and x.args[2].is_finite:
            args[2]=t("power",args=[args[2],t("text",str(x.args[3]))])
        return t("call","limit",args)
    if isinstance(x,s.Rational) and x.q != 1: return t("fraction",args=[t("text",str(x.p)),t("text",str(x.q))])
    if isinstance(x,s.Pow):
        if x.exp == s.Rational(1,2): return t("root",args=[display_tree(x.base)])
        if x.exp.is_negative:
            denominator = x.base if x.exp == -1 else s.Pow(x.base,-x.exp,evaluate=False)
            return t("fraction",args=[t("text","1"),display_tree(denominator)])
        return t("power",args=[display_tree(x.base),display_tree(x.exp)])
    if isinstance(x,s.Add):
        terms=x.as_ordered_terms()
        terms=[a for a in terms if not (a.is_Symbol and str(a)=="C")]+[a for a in terms if a.is_Symbol and str(a)=="C"]
        # Unevaluated sums can contain entire sums as operands. Keep their
        # grouping before extracting a minus: -(a-b) must not display as -a-b.
        return t("sum",args=[t("parentheses",args=[display_tree(a)]) if isinstance(a,s.Add) else
                             negative(a) if a.could_extract_minus_sign() else display_tree(a) for a in terms])
    if isinstance(x,s.Mul):
        if x.could_extract_minus_sign(): return negative(x)
        if any(isinstance(a,s.Pow) and a.is_number and a.exp.is_Integer and abs(a.exp)>10000 for a in x.args):
            return t("product",args=[display_tree(a) for a in x.args])
        num,den = s.fraction(x)
        if den != 1: return t("fraction",args=[display_tree(num),display_tree(den)])
        return t("product",args=[display_tree(a) for a in x.args])
    if isinstance(x,s.FiniteSet): return t("set",args=[display_tree(a) for a in sorted(x,key=s.default_sort_key)])
    if isinstance(x,Relational): return t("relation",x.rel_op,[display_tree(x.lhs),display_tree(x.rhs)])
    if isinstance(x,s.Function):
        name=x.func.__name__
        if name=="log": name="ln"
        return t("function",name,[display_tree(a) for a in x.args])
    if isinstance(x,s.Symbol): return t("symbol",readable(x))
    if isinstance(x,s.Number): return t("number",readable(x))
    return t("text",readable(x))

def dms_tree(value):
    return {"kind":"dms","args":[display_tree(part) for part in dms_parts(value)]}

def readable(x):
    if isinstance(x,Quantity): return readable(x.base)+" "+x.unit_text()
    if isinstance(x,dict): return "\n".join(str(k)+": "+readable(v) for k,v in x.items())
    if isinstance(x,s.ImageSet) and len(x.lamda.variables)==1 and x.base_set==s.S.Integers:
        return "{"+readable(x.lamda.expr)+" | "+str(x.lamda.variables[0])+" ∈ ℤ}"
    if isinstance(x,(list,tuple)): return "["+", ".join(readable(v) for v in x)+"]"
    if isinstance(x,s.Add):
        constants=[term for term in x.args if term.is_Symbol and str(term)=="C"]
        if constants:
            remainder=x-sum(constants)
            if remainder!=0: return readable(remainder)+" + C"
    from sympy.printing.str import StrPrinter
    class CompactPrinter(StrPrinter):
        def _print_Float(self,expr):
            text=super()._print_Float(expr)
            parts=text.lower().split("e")
            mantissa=parts[0].rstrip("0").rstrip(".") if "." in parts[0] else parts[0]
            return mantissa+("e"+parts[1] if len(parts)>1 else "")
    return CompactPrinter().doprint(x)

def approximate(x, digits):
    if isinstance(x,Quantity): return Quantity(s.N(x.base,digits),x.dimensions,x.absolute_temperature)
    if isinstance(x,dict): return {k:approximate(v,digits) for k,v in x.items()}
    if isinstance(x,(list,tuple)): return [approximate(v,digits) for v in x]
    if isinstance(x,s.Set):
        return [approximate(v,digits) for v in sorted(x,key=s.default_sort_key)] if isinstance(x,s.FiniteSet) else x
    if x in (s.true,s.false): return x
    if isinstance(x,s.Basic) and x.has(s.CRootOf):
        # Secant refinement checks the root's isolating bounds, avoiding costly
        # bisection to every decimal digit. Keep the exact RootOf in the result.
        x=x.xreplace({root:root.eval_approx(digits+5) for root in x.atoms(s.CRootOf)})
    return s.N(x,digits) if hasattr(x,"evalf") else x

def display_rounded(x, digits):
    """Round floats and show implicit algebraic roots as readable numeric values."""
    if isinstance(x,Quantity): return Quantity(display_rounded(x.base,digits),x.dimensions,x.absolute_temperature)
    if isinstance(x,dict): return {k:display_rounded(v,digits) for k,v in x.items()}
    if isinstance(x,(list,tuple)): return [display_rounded(v,digits) for v in x]
    if isinstance(x,s.MatrixBase): return x.applyfunc(lambda v: display_rounded(v,digits))
    if isinstance(x,s.Set): return s.FiniteSet(*(display_rounded(v,digits) for v in x)) if isinstance(x,s.FiniteSet) else x
    if isinstance(x,s.Basic):
        # RootOf is an internal exact root representation, not useful display
        # notation. Replace it only in the presentation copy of the value.
        roots={root:approximate(root,digits) for root in x.atoms(s.CRootOf)}
        if roots: x=x.xreplace(roots)
        floats={f:s.N(f,digits) for f in x.atoms(s.Float)}
        if floats: return x.xreplace(floats)
    return x

def result_ast(x):
    """Lossless result transfer for Ans and STO; never reparse printed mathematics."""
    def node(kind,value="",args=()): return {"kind":kind,"value":value,"args":list(args)}
    if isinstance(x,Quantity): return {**node("quantity",args=[result_ast(x.base)]),"dimensions":list(x.dimensions),"absolute":x.absolute_temperature}
    if isinstance(x,s.MatrixBase): return node("list",args=[node("list",args=[result_ast(x[i,j]) for j in range(x.cols)]) for i in range(x.rows)])
    if isinstance(x,(list,tuple,s.Tuple)): return node("list",args=[result_ast(v) for v in x])
    if isinstance(x,dict): return node("mapping",args=[node("pair",args=[result_ast(k),result_ast(v)]) for k,v in x.items()])
    if isinstance(x,s.FiniteSet): return node("set",args=[result_ast(v) for v in x])
    if str(x) in ("pi","E","I","oo","-oo","EmptySet","True","False"): return node("constant",str(x))
    if isinstance(x,s.Symbol): return node("snapshot_symbol",str(x))
    if isinstance(x,s.Float): return node("float",readable(x))
    if isinstance(x,s.Integer): return node("number",str(x))
    if isinstance(x,s.Rational): return node("binary","/",[node("number",str(x.p)),node("number",str(x.q))])
    if isinstance(x,(s.Add,s.Mul)):
        op="+" if isinstance(x,s.Add) else "*"
        result=result_ast(x.args[0])
        for v in x.args[1:]: result=node("binary",op,[result,result_ast(v)])
        return result
    if isinstance(x,s.Pow): return node("binary","^",[result_ast(x.base),result_ast(x.exp)])
    if isinstance(x,Relational): return node("relation",x.rel_op,[result_ast(x.lhs),result_ast(x.rhs)])
    if isinstance(x,s.Function):
        name=x.func.__name__
        reusable={"log":"ln","Abs":"abs","LambertW":"lambertw","conjugate":"conj","Piecewise":"piecewise","exp":"exp",
                  "sin":"sin","cos":"cos","tan":"tan","sec":"sec","csc":"csc","cot":"cot","asin":"asin","acos":"acos","atan":"atan",
                  "sinh":"sinh","cosh":"cosh","tanh":"tanh","asinh":"asinh","acosh":"acosh","atanh":"atanh",
                  "sinc":"sinc","gamma":"gamma","erf":"erf","erfc":"erfc","Ei":"Ei","Si":"Si","Ci":"Ci",
                  "zeta":"zeta","re":"re","im":"im","arg":"arg","sign":"sign","floor":"floor","ceiling":"ceil",
                  "atan2":"atan2", "polylog":"polylog"}
        require(name in reusable,"This result cannot be stored as a reusable expression")
        return node("frozen_call",reusable[name],[result_ast(v) for v in x.args])
    raise MathError("This result cannot be stored as a reusable expression")
