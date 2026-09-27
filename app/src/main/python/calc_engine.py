"""Offline calculator entry point used by Chaquopy and desktop clients.

The public Engine, Quantity, and dispatch names stay at this import path.
"""
import json
import sys
import sympy as s
from sympy.core.relational import Relational
from quantities import Quantity
from calc_shared import (Budget, CONSTANTS, MathError, dms_parts, matrix, require)
from calc_display import (approximate, display_rounded, display_tree, dms_tree,
                          is_dms_expression, readable, result_ast)
from calc_evaluator import Engine
from calc_graph import graph, graph_analysis, regression_samples
from calc_programmer import programmer

# Symbolic calls whose cold first evaluation is heavy enough that the generic step allowance used
# to cut off legitimate work. Nested calls count too, so 1+fourier(exp(-t^2),t,w) is heavy as well.
HEAVY_CALLS=("integrate","dsolve","desolve","laplace","ilaplace","fourier","ifourier","domain","range","invt","tinterval","tvmrate","irr")
def contains_heavy_call(node):
    pending=[node]
    while pending:
        current=pending.pop()
        if not isinstance(current, dict): continue
        if current.get("kind")=="call" and current.get("value") in HEAVY_CALLS: return True
        pending.extend(current.get("args") or [])
    return False

def dispatch(payload):
    request=json.loads(payload)
    tree=request.get("tree",{})
    heavy=contains_heavy_call(tree)
    # The first evaluation of an expression also fills SymPy's caches, so a cold computation can
    # need several times the steps of a warm repeat. 1.5M and 6M steps both cut off legitimate
    # first evaluations: fourier(exp(-t^2),t,w) spends about 7.5M traced steps cold although the
    # real work takes a fraction of a second. Heavy calls keep a step ceiling above what the time
    # budget reaches on typical hardware, so the time limit stays the binding guard.
    seconds=float(request.get("budget",20 if heavy else 8))
    if heavy:
        steps=100000000
        # As-you-type previews pass two seconds; a committed heavy call (eight seconds and up) may
        # use the rest of the 20-second IPC window.
        if seconds>=8: seconds=max(seconds,16)
    else:
        steps=3000000
    budget=Budget(seconds,steps=steps)
    try:
        sys.settrace(budget.trace)
        engine=Engine(request)
        action=request.get("action","evaluate")
        if action=="constants":
            entries=[{"symbol":"pi","name":"Pi","value":"3.141592653589793…","unit":"","exact":True},{"symbol":"e","name":"Euler's number","value":"2.718281828459045…","unit":"","exact":True}]
            entries += [{"symbol":key,"name":v[0],"value":v[1] or "h / (2π)","unit":v[2],"exact":v[3]} for key,v in CONSTANTS.items()]
            result={"constants":entries,"source":"NIST CODATA 2022"}
        elif action=="graph": result=graph(engine,request)
        elif action=="graphAnalysis": result=graph_analysis(engine,request)
        elif action=="programmer": result=programmer(request)
        else:
            value=engine.build(request["tree"])
            if getattr(value,"is_number",False) and value.has(s.I): value=s.expand_complex(value)
            if isinstance(value,list) and value and all(isinstance(row,list) for row in value): value=matrix(value)
            if getattr(value,"has",lambda *_:False)(s.zoo,s.nan): raise MathError("Undefined or division by zero")
            # The result view rounds floating-point values to the display digits; the numeric work keeps engine.precision.
            display_value=display_rounded(value,engine.display_digits)
            exact=readable(display_value)
            require(len(exact)<=40000,"Result exceeds display size limit")
            decimal_value=approximate(value,engine.display_digits)
            dms_result=(is_dms_expression(request["tree"],request.get("variables",{}))
                        and getattr(value,"is_number",False) and not value.has(s.I))
            result={"exact":exact,"decimal":readable(decimal_value),"tree":display_tree(display_value),"note":engine.note,
                    "conditions":[readable(c.lhs)+" ≠ "+readable(c.rhs) if isinstance(c,s.Unequality) else str(c) for c in dict.fromkeys(engine.conditions)],"symbolic":bool(getattr(value,"free_symbols",False))}
            result["approximate"]=bool(getattr(value,"has",lambda *_:False)(s.Float))
            result["decimalTree"]=display_tree(decimal_value)
            if dms_result:
                result["tree"]=dms_tree(display_value)
                result["decimalTree"]=dms_tree(decimal_value)
                result["numericTree"]=display_tree(display_value)
                result["numericDecimalTree"]=display_tree(decimal_value)
                result["dms"]=True
            if request["tree"].get("value")=="eng" and getattr(value,"is_number",False):
                offset=engine.build(request["tree"]["args"][1]) if len(request["tree"]["args"])>1 else 0
                require(-300<=offset<=300,"Engineering exponent limit")
                exponent=(int(s.floor(s.log(s.Abs(value),10)/3))*3 if value!=0 else 0)+int(offset)
                mantissa=s.N(value/s.Integer(10)**exponent,engine.display_digits)
                power={"kind":"power","args":[{"kind":"text","value":"10"},{"kind":"text","value":str(exponent)}]}
                result["tree"]=result["decimalTree"]={"kind":"product","args":[display_tree(mantissa),power]}
            if request["tree"].get("value")=="dms" and isinstance(value,list):result["tree"]=result["decimalTree"]={"kind":"dms","args":[display_tree(x) for x in display_value]}
            if request["tree"].get("kind")=="call" and request["tree"].get("value")=="regression":
                try: result["curve"]=regression_samples(engine,value,engine.build(request["tree"]["args"][0]),request)
                except Exception: result["curve"]=[]
            try:
                ast=result_ast(value)
                if dms_result:
                    ast={"kind":"frozen_call","value":"sexagesimal","args":[result_ast(part) for part in dms_parts(value)]}
                symbols=getattr(value,"free_symbols",set())
                guards=[c for c in dict.fromkeys(engine.conditions) if c.free_symbols & symbols]
                result["resultAst"]={"kind":"restricted","args":[ast]+[result_ast(c) for c in guards]} if guards else ast
            except (MathError,TypeError,AttributeError): result["reusable"]=False
        return json.dumps({"ok":True,**result},ensure_ascii=False,allow_nan=False)
    except Exception as exc:
        message=str(exc) or type(exc).__name__
        if "NonInvertible" in type(exc).__name__: message="Singular matrix"
        elif "Shape" in type(exc).__name__: message="Matrix dimension mismatch"
        elif "Could not find root" in message: message="Numerical convergence failed. Try a different bracket or initial guess."
        return json.dumps({"ok":False,"error":message[:600]},ensure_ascii=False)
    finally:
        sys.settrace(None)
