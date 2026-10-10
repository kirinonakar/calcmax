"""Offline calculator entry point used by Chaquopy and desktop clients.

The public Engine, Quantity, and dispatch names stay at this import path.
"""
import json
from calc_runtime import ExecutionStopped
from calc_limits import computation_limits, limits_removed, within_limit
import sympy as s
from sympy.core.relational import Relational
from quantities import Quantity
from calc_shared import (Budget, CONSTANTS, MathError, dms_parts, matrix, require)
from calc_display import (approximate, display_rounded, display_tree, dms_tree,
                          is_dms_expression, readable, result_ast)
from calc_evaluator import Engine
from calc_equation_steps import equation_steps, equation_solution_tree, SUMMARY, LIMIT
from calc_calculus_steps import calculus_steps
from calc_result_guidance import result_guidance
from calc_graph import graph, graph_analysis, regression_samples
from calc_programmer import programmer
from calc_statistics import pearson_correlation
from calc_probability import probability
from calc_advanced_statistics import FUNCTIONS as ADVANCED_STATISTICS
from calc_statistics_report import BASIC as BASIC_STATISTICS, statistics_report, statistics_copy_report
from calc_statistics_diagnostics import companion_report

# Symbolic calls whose cold first evaluation is heavy enough that the generic step allowance used
# to cut off legitimate work. Nested calls count too, so 1+fourier(exp(-t^2),t,w) is heavy as well.
HEAVY_CALLS=("solve","integrate","dsolve","desolve","laplace","ilaplace","fourier","ifourier","mellin","invmellin","ztrans","invztrans","pdsolve","domain","range","real_roots","rsolve","invt","tinterval","tukey","tvmrate","irr","regression","wilcoxon","mannwhitney")
MAX_SHOWN_INTEGER_DIGITS=10000
HEAVY_CALLS += tuple(ADVANCED_STATISTICS)
HEAVY_CALLS += ('anova','welchanova','tukey','gameshowell')
HEAVY_CALLS += ("factorint","divisors")
HEAVY_CALLS += ("mean", "median", "variance", "stdev", "sumdata", "quartiles", "stats",
                "covariance", "correlation", "ttest", "ttest2", "ttestpaired", "ztest", "ztest2",
                "chi2test", "chi2independence", "fisherexact", "anova", "shapiro", "kruskal", "zinterval")

def statistics_display_terms(value, labels):
    """Label term cells for display while keeping the reusable answer unchanged."""
    if isinstance(value, list):
        return [statistics_display_terms(item, labels) for item in value]
    if not isinstance(value, dict): return value
    result = {}
    for key, item in value.items():
        if key in ('term','group') and isinstance(item, str):
            if item in labels: item = labels[item]
            elif ': ' in item:  # Multinomial category contrast followed by a predictor.
                prefix, term = item.rsplit(': ', 1)
                item = prefix + ': ' + labels.get(term, term)
            result[key] = item
        else: result[labels.get(key,key) if key.startswith('response:') else key] = statistics_display_terms(item, labels)
    return result

def shown_exact(rounded):
    """Keep exact values reusable without sending a huge integer to the result view."""
    if isinstance(rounded,s.Rational) and max(abs(rounded.p).bit_length(),rounded.q.bit_length()) > 33219:
        full=readable(rounded)
        if len(full) > MAX_SHOWN_INTEGER_DIGITS:
            digits=max(len(str(abs(rounded.p))),len(str(rounded.q)))
            label=full[:100]+"…"+full[-20:]+f" ({digits} digits; full value in Ans)"
            return label,{"kind":"text","value":label}
    return readable(rounded),display_tree(rounded)
def contains_heavy_call(node):
    pending=[node]
    while pending:
        current=pending.pop()
        if not isinstance(current, dict): continue
        if current.get("kind")=="call" and current.get("value") in HEAVY_CALLS: return True
        if (current.get("kind")=="call" and current.get("value")=="regression"
                and len(current.get("args") or []) > 1 and current["args"][1].get("value")=="custom"): return True
        pending.extend(current.get("args") or [])
    return False

def dispatch(payload, control=None):
    # Monitoring a backward jump may raise at the edge of a local exception
    # table on CPython. Keep the public error boundary in a separate caller frame.
    try:
        with computation_limits(json.loads(payload).get("removeComputationLimit", False)):
            return _dispatch(payload, control)
    except ExecutionStopped as exc:
        return json.dumps({"ok":False,"error":str(exc)},ensure_ascii=False)


def _dispatch(payload, control=None):
    request=json.loads(payload)
    tree=request.get("tree",{})
    heavy=contains_heavy_call(tree)
    # Cold CAS work also fills SymPy caches. Heavy calls keep a generous step ceiling
    # so the time limit remains the binding guard.
    seconds=float(request.get("budget",60 if heavy else 8))
    if heavy:
        steps=100000000
        # As-you-type previews pass two seconds; a committed heavy call (eight seconds and up) may
        # use the full 60-second IPC window.
        if seconds>=8: seconds=max(seconds,60)
    else:
        steps=3000000
    if limits_removed(): seconds=steps=float("inf")
    budget=Budget(seconds,steps=steps,control=control)
    try:
        budget.__enter__()
        engine=Engine(request)
        action=request.get("action","evaluate")
        if action=="constants":
            entries=[{"symbol":"pi","name":"Pi","value":"3.141592653589793…","unit":"","exact":True},{"symbol":"e","name":"Euler's number","value":"2.718281828459045…","unit":"","exact":True}]
            entries += [{"symbol":key,"name":v[0],"value":v[1] or "h / (2π)","unit":v[2],"exact":v[3]} for key,v in CONSTANTS.items()]
            result={"constants":entries,"source":"NIST CODATA 2022"}
        elif action=="graph": result=graph(engine,request)
        elif action=="graphAnalysis": result=graph_analysis(engine,request)
        elif action=="programmer": result=programmer(request)
        elif action=="probability": result=probability(request)
        else:
            parameters=request.get("functionParameters", [])
            for name in parameters: engine.bindings[name]=engine.symbol(name)
            value=engine.build(request["tree"])
            if getattr(value,"is_number",False) and value.has(s.I): value=s.expand_complex(value)
            if isinstance(value,list) and value and all(isinstance(row,list) for row in value): value=matrix(value)
            if getattr(value,"has",lambda *_:False)(s.zoo,s.nan): raise MathError("Undefined or division by zero")
            # Keep the available precision in display trees. The Android result view
            # applies displayDigits as fractional places after choosing a notation.
            term_labels=request.get('statisticsTermLabels', {}) if tree.get('value') in ADVANCED_STATISTICS else {}
            shown_value=statistics_display_terms(value,term_labels) if term_labels else value
            if tree.get("kind")=="call" and tree.get("value")=="eigenvalues":
                # Presentation labels must not change the numeric pairs saved in Ans.
                shown_value={"eigenvalue "+str(i+1): {"value":pair[0], "multiplicity":pair[1]}
                             for i,pair in enumerate(value.tolist())}
            display_value=display_rounded(shown_value,engine.precision)
            exact,exact_tree=shown_exact(display_value)
            require(len(exact)<=40000,"Result exceeds display size limit")
            decimal_value=approximate(shown_value,engine.precision)
            dms_result=(is_dms_expression(request["tree"],request.get("variables",{}))
                        and getattr(value,"is_number",False) and not value.has(s.I))
            result={"exact":exact,"decimal":readable(decimal_value),"tree":exact_tree,"note":engine.note,
                    "conditions":[readable(c.lhs)+" ≠ "+readable(c.rhs) if isinstance(c,s.Unequality) else str(c) for c in dict.fromkeys(engine.conditions)],"symbolic":bool(getattr(value,"free_symbols",False))}
            result["approximate"]=bool(getattr(display_value,"has",lambda *_:False)(s.Float))
            result["decimalTree"]=display_tree(decimal_value)
            if tree.get('kind')=='call' and tree.get('value') in BASIC_STATISTICS | (ADVANCED_STATISTICS-{'survivalanalysis'}):
                result['statisticsReport']=statistics_report(tree['value'],shown_value,engine.precision,request.get('statisticsTermLabels', {}))
                if hasattr(engine,'statistics_plots'):
                    result['statisticsReport']['plots']=engine.statistics_plots+result['statisticsReport']['plots']
                companion_report(result['statisticsReport'],tree['value'],shown_value,getattr(engine,'statistics_inputs',None),engine.precision,request.get('statisticsTermLabels',{}),getattr(engine,'statistics_residuals',None))
            if request["tree"].get("value")=="survivalanalysis" and hasattr(engine,"survival_report"):
                result["survival"]=engine.survival_report
                result["statisticsCopyReport"]=statistics_copy_report('survivalanalysis',value,engine.survival_report,engine.precision)
            if hasattr(engine,'imputation_result'): result['imputation']=engine.imputation_result
            if dms_result:
                result["tree"]=dms_tree(display_value)
                result["decimalTree"]=dms_tree(decimal_value)
                result["numericTree"]=display_tree(display_value)
                result["numericDecimalTree"]=display_tree(decimal_value)
                result["dms"]=True
            if request["tree"].get("value")=="eng" and getattr(value,"is_number",False):
                offset=engine.build(request["tree"]["args"][1]) if len(request["tree"]["args"])>1 else 0
                require(within_limit(abs(offset),300),"Engineering exponent limit")
                exponent=(int(s.floor(s.log(s.Abs(value),10)/3))*3 if value!=0 else 0)+int(offset)
                mantissa=s.N(value/s.Integer(10)**exponent,engine.precision)
                power={"kind":"power","args":[{"kind":"text","value":"10"},{"kind":"text","value":str(exponent)}]}
                result["tree"]=result["decimalTree"]={"kind":"product","args":[display_tree(mantissa),power]}
            if request["tree"].get("value")=="dms" and isinstance(value,list):result["tree"]=result["decimalTree"]={"kind":"dms","args":[display_tree(x) for x in display_value]}
            if request["tree"].get("kind")=="call" and request["tree"].get("value")=="regression":
                rows=None
                try:
                    rows=engine.build(request["tree"]["args"][0])
                    result["curve"]=regression_samples(engine,value,rows,request) if all(len(row)==2 for row in rows) else []
                except Exception: result["curve"]=[]
                mode=request["tree"]["args"][1].get("value") if len(request["tree"]["args"])>1 else "linear"
                result["parameters"]=engine.regression_parameters
                result["regression"]=engine.regression_report
                if engine.regression_report:
                    result["statisticsCopyReport"]=statistics_copy_report('regression',value,engine.regression_report,engine.precision)
                if mode=="linear":
                    try:
                        xs,ys=zip(*rows)
                        result["correlation"]=float(s.N(pearson_correlation(xs,ys),max(12,engine.display_digits)))
                    except Exception: result["correlation"]=None
            try:
                ast=result_ast(value)
                if dms_result:
                    ast={"kind":"frozen_call","value":"sexagesimal","args":[result_ast(part) for part in dms_parts(value)]}
                # Calculus answers retain their independent variable, including constant results.
                source=request["tree"]
                answer_parameters=parameters or source.get("parameters", [])
                if (not answer_parameters and source.get("kind")=="call" and len(source.get("args", []))>1
                        and (source.get("value")=="diff" or source.get("value")=="integrate" and len(source["args"])==2)):
                    candidate=source["args"][1]
                    if candidate.get("kind")=="symbol": answer_parameters=[candidate["value"]]
                if not answer_parameters and source.get("kind")=="symbol" and source.get("value")=="Ans":
                    answer_parameters=request.get("variables", {}).get("Ans", {}).get("parameters", [])
                symbols=getattr(value,"free_symbols",set()) | {engine.symbol(name) for name in answer_parameters}
                guards=[c for c in dict.fromkeys(engine.conditions) if c.free_symbols & symbols]
                result["resultAst"]={"kind":"restricted","args":[ast]+[result_ast(c) for c in guards]} if guards else ast
                if answer_parameters: result["resultAst"]["parameters"]=answer_parameters
            except (MathError,TypeError,AttributeError): result["reusable"]=False
        if hasattr(engine, "equation_step_input"):
            method, inputs = engine.equation_step_input
            try:
                report = equation_steps(engine, method, inputs, value)
                if report["steps"]:
                    report["steps"][-1].update(exact=result["exact"], tree=equation_solution_tree(result["tree"]))
                if len(json.dumps(report, ensure_ascii=False)) > 40000:
                    report = {"steps": [{"title": "Solution", "exact": result["exact"], "tree": result["tree"]}], "note": LIMIT}
            except (ValueError, TypeError, NotImplementedError, AttributeError):
                report = {"steps": [{"title": "Solution", "exact": result["exact"], "tree": result["tree"]}], "note": SUMMARY}
            result["equationSteps"] = report
        if engine.solution_step_inputs:
            combined={"steps":[], "note":""}
            saved_strategy=getattr(engine,"integral_strategy",None)
            for method,inputs,answer,strategy in engine.solution_step_inputs:
                engine.integral_strategy=strategy
                try:
                    report=(calculus_steps if method in ("diff","integrate","limit") else equation_steps)(engine,method,inputs,answer)
                except (ValueError,TypeError,NotImplementedError,AttributeError):
                    report={"steps":[{"title":"Computed result","tree":display_tree(answer),"exact":readable(answer)}],"note":SUMMARY}
                combined["steps"].extend(report["steps"])
                if report.get("note"): combined["note"] += ("\n" if combined["note"] else "")+report["note"]
                for key in ("method","advancedSteps"):
                    if key in report: combined[key]=report[key]
                if len(json.dumps(combined,ensure_ascii=False))>40000:
                    combined={"steps":[],"note":LIMIT};break
            engine.integral_strategy=saved_strategy
            if len(engine.solution_step_inputs)>1 and (not combined["steps"] or answer != value):
                combined["steps"].append({"title":"Computed result","tree":result["tree"],"exact":result["exact"]})
            elif combined["steps"]:
                # Apply the same presentation precision as the answer view.
                # The final calculus step already has a formula in equations;
                # use one authoritative tree so both UIs render the answer once.
                combined["steps"][-1].pop("equations",None)
                combined["steps"][-1].update(tree=equation_solution_tree(result["tree"]) if method=="solve" else result["tree"],exact=result["exact"])
            result["solutionSteps"]=combined
        if hasattr(engine,"guidance_input"):
            try:
                guidance=result_guidance(engine,*engine.guidance_input)
                if guidance:
                    result["guidance"]=guidance
                    if guidance["status"]=="unresolved_equation":
                        for field in ("equationSteps","solutionSteps"):
                            report=result.get(field,{})
                            if engine.note: report["note"]=report.get("note","").replace(engine.note,"").strip()
                            if report.get("steps"):
                                last=report["steps"][-1]
                                last["title"]=("Numerical real roots (partial)" if guidance.get("knownRoots",{}).get("approximate")
                                               else "Known real roots (partial)") if "knownRoots" in guidance else guidance["message"]
                                last["explanation"]=guidance["detail"]
                                if "knownRoots" in guidance: last["tree"]=guidance["knownRoots"]["tree"]
                                else: last.pop("tree",None)
            except (ValueError,TypeError,NotImplementedError,AttributeError):
                pass
        if getattr(engine, "regression_report", None) and engine.regression_report.get("model") == "randomforest":
            result.pop("resultAst", None)
            result["reusable"] = False
            result["symbolic"] = False
        response=json.dumps({"ok":True,**result},ensure_ascii=False,allow_nan=False)
        budget.check()
        return response
    except (Exception, ExecutionStopped) as exc:
        message=str(exc) or type(exc).__name__
        if "NonInvertible" in type(exc).__name__: message="Singular matrix"
        elif "Shape" in type(exc).__name__: message="Matrix dimension mismatch"
        elif "Could not find root" in message: message="Numerical convergence failed. Try a different bracket or initial guess."
        return json.dumps({"ok":False,"error":message[:600]},ensure_ascii=False)
    finally:
        budget.stop()
