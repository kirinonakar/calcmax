"""Time-value-of-money and cash-flow calculations."""
from calc_limits import within_limit
import mpmath as mp
import sympy as s
from calc_shared import MathError, flatten, require
from calc_statistics import _mpf, _mp_result, _real_value

def _mode_argument(a, nodes):
    if nodes and isinstance(nodes[-1], dict) and nodes[-1].get("kind") == "symbol" and nodes[-1].get("value") in ("begin", "end"):
        return nodes[-1]["value"], a[:-1]
    return "end", a

def _tvm_value(n, i, pv, pmt, fv, begin):
    """Value of pv*(1+i)^n + pmt*((1+i)^n - 1)/i + fv for mpmath numbers."""
    if i == 0: return pv + pmt*n + fv
    growth = (1 + i)**n
    return pv*growth + pmt*(1 + i if begin else 1)*(growth - 1)/i + fv

def finance_value(engine, name, a, nodes):
    digits = engine.precision
    mode, args = _mode_argument(a, nodes)
    begin = mode == "begin"
    if name == "npv":
        require(len(args) in (2, 3), "npv takes a rate and a cash-flow list, or a rate, initial flow and list")
        rate = _real_value(args[0], "npv requires a numeric rate")
        require(rate > -1, "The rate must be greater than -100%")
        if len(args) == 2:
            require(isinstance(args[1], (list, tuple)), "npv needs a cash-flow list")
            flows = list(args[1])
        else:
            require(isinstance(args[2], (list, tuple)), "npv needs a cash-flow list")
            flows = [args[1]] + list(args[2])
        require(2 <= len(flows) and within_limit(len(flows),500), "npv needs between 2 and 500 cash flows")
        for flow in flows: _real_value(flow, "Cash flows must be numbers")
        return s.Add(*[flow/(1 + rate)**index for index, flow in enumerate(flows)])
    if name == "irr":
        require(len(args) in (1, 2), "irr takes a cash-flow list, or an initial flow and list")
        if len(args) == 1:
            require(isinstance(args[0], (list, tuple)), "irr needs a cash-flow list")
            flows = list(args[0])
        else:
            require(isinstance(args[1], (list, tuple)), "irr needs a cash-flow list")
            flows = [args[0]] + list(args[1])
        require(2 <= len(flows) and within_limit(len(flows),100), "irr needs between 2 and 100 cash flows")
        for flow in flows: _real_value(flow, "Cash flows must be numbers")
        if s.Add(*flows) == 0: return s.Integer(0)
        with mp.workdps(digits + 15):
            coefficients = [_mpf(flow, digits + 5) for flow in flows]
            while coefficients and coefficients[0] == 0: coefficients.pop(0)
            require(len(coefficients) >= 2, "irr needs at least one change of sign")
            candidates = []
            for root in mp.polyroots(coefficients, maxsteps=200, extraprec=10):
                if abs(mp.im(root)) > mp.mpf(10)**(-max(digits - 6, 6))*(1 + abs(root)): continue
                rate = mp.re(root) - 1
                if rate > -1: candidates.append(rate)
            require(candidates, "No rate of return solves this cash-flow list")
            if len(candidates) > 1: engine.note = "Several rates solve this cash-flow list; the value closest to zero is shown."
            return _mp_result(min(candidates, key=abs), engine)
    if name in ("tvmfv", "tvmpv", "tvmpmt", "tvmn"):
        orders = {"tvmfv": ("n", "i", "pv", "pmt"), "tvmpv": ("n", "i", "pmt", "fv"),
                  "tvmpmt": ("n", "i", "pv", "fv"), "tvmn": ("i", "pv", "pmt", "fv")}
        require(len(args) == 4, name + " takes " + ", ".join(orders[name]) + ", and optionally begin or end")
        values = dict(zip(orders[name], args))
        for value in values.values(): _real_value(value, name + " requires numeric arguments")
        require(values["i"] > -1, "The interest rate must be greater than -100%")
        if "n" in values: require(values["n"] >= 0, "The number of periods must not be negative")
        with mp.workdps(digits + 10):
            n = _mpf(values["n"], digits) if "n" in values else None
            i = _mpf(values["i"], digits)
            pv = _mpf(values.get("pv", s.Integer(0)), digits)
            pmt = _mpf(values.get("pmt", s.Integer(0)), digits)
            fv = _mpf(values.get("fv", s.Integer(0)), digits)
            adjustment = 1 + i if begin else 1
            if name == "tvmfv":
                answer = -_tvm_value(n, i, pv, pmt, mp.mpf(0), begin)
            elif name == "tvmpv":
                if i == 0: answer = -(pmt*n + fv)
                else: answer = -_tvm_value(n, i, mp.mpf(0), pmt, fv, begin)/(1 + i)**n
            elif name == "tvmpmt":
                require(n > 0, "The number of periods must be positive")
                if i == 0:
                    answer = -(pv + fv)/n
                else:
                    answer = -(pv*(1 + i)**n + fv)/_tvm_value(n, i, mp.mpf(0), mp.mpf(1), mp.mpf(0), begin)
            else:
                if i == 0:
                    require(pmt != 0, "Zero interest needs a non-zero payment")
                    answer = -(pv + fv)/pmt
                else:
                    payment = pmt*adjustment
                    require(i*pv + payment != 0, "No number of periods solves this schedule")
                    ratio = (payment - i*fv)/(i*pv + payment)
                    require(ratio > 0, "No number of periods solves this schedule")
                    answer = mp.log(ratio)/mp.log(1 + i)
        return _mp_result(answer, engine)
    if name == "tvmrate":
        require(len(args) == 4, "tvmrate takes n, pv, pmt and fv, and optionally begin or end")
        n, pv, pmt, fv = args
        for value in args: _real_value(value, "tvmrate requires numeric arguments")
        require(n > 0, "The number of periods must be positive")
        if pv + pmt*n + fv == 0: return s.Integer(0)
        with mp.workdps(digits + 10):
            n_mp, pv_mp, pmt_mp, fv_mp = _mpf(n, digits), _mpf(pv, digits), _mpf(pmt, digits), _mpf(fv, digits)
            def equation(rate): return _tvm_value(n_mp, rate, pv_mp, pmt_mp, fv_mp, begin)
            rates = [mp.mpf(0)]
            for k in range(1, 31): rates += [-1 + mp.mpf(2)**(-k), -mp.mpf(2)**(-k)]
            for k in range(1, 16): rates += [mp.mpf(10)**(-k), mp.mpf(2)**k]
            rates = sorted(set(rates))
            roots, previous = [], None
            for rate in rates:
                current = equation(rate)
                if current == 0: roots.append(rate)
                elif previous is not None and current*previous[1] < 0:
                    low, high = previous[0], rate
                    low_value = previous[1]
                    tolerance = mp.mpf(10)**(-(digits + 4))*(1 + abs(low))
                    for _ in range(400):
                        middle = (low + high)/2
                        middle_value = equation(middle)
                        if low_value*middle_value <= 0: high = middle
                        else: low, low_value = middle, middle_value
                        if high - low <= tolerance: break
                    roots.append((low + high)/2)
                previous = (rate, current)
            require(roots, "No interest rate solves this payment schedule")
            unique = []
            for root in sorted(roots):
                if not unique or root - unique[-1] > mp.mpf(10)**(-(digits + 4))*(1 + abs(root)): unique.append(root)
            if len(unique) > 1: engine.note = "Several rates solve this schedule; the value closest to zero is shown."
            return _mp_result(min(unique, key=abs), engine)
    if name == "amort":
        require(len(args) in (3, 4), "amort takes i, pv and n, and optionally k")
        i, pv, n = args[0], args[1], args[2]
        for value in args[:3]: _real_value(value, "amort requires numeric arguments")
        require(i > -1, "The interest rate must be greater than -100%")
        require(n.is_Integer and n > 0, "The number of payments must be a positive integer")
        if len(args) == 4:
            k = args[3]
            require(k.is_Integer and 0 <= k <= n, "The payment index must be an integer from 0 to n")
        else: k = n
        with mp.workdps(digits + 10):
            i_mp, principal = _mpf(i, digits), _mpf(pv, digits)
            n_mp, k_mp = _mpf(n, digits), _mpf(k, digits)
            growth = (1 + i_mp)**n_mp
            if i_mp == 0: payment = -principal/n_mp
            else: payment = -principal*i_mp*growth/((1 + i_mp if begin else 1)*(growth - 1))
            balance = _tvm_value(k_mp, i_mp, principal, payment, mp.mpf(0), begin)
            if abs(balance) <= mp.mpf(10)**(-(max(digits, 12) - 3))*max(1, abs(principal)): balance = mp.mpf(0)
            interest = -payment*k_mp - (principal - balance)
            return {"payment": _mp_result(payment, engine), "payments": s.Integer(k),
                    "balance": _mp_result(balance, engine), "principal paid": _mp_result(principal - balance, engine),
                    "interest paid": _mp_result(interest, engine)}
    if name == "cagr":
        require(len(args) == 3, "cagr takes a starting value, an ending value and a number of periods")
        start, end, periods = args
        for value in args: _real_value(value, "cagr requires numeric arguments")
        require(start > 0, "The starting value must be positive")
        require(end >= 0, "The ending value must not be negative")
        require(periods > 0, "The number of periods must be positive")
        return s.simplify((end/start)**(s.Integer(1)/periods) - 1)
    raise MathError("Unknown finance function: " + name)
