"""Execute a user-authored script in the app's separate Python service process."""
import contextlib
import builtins
import json
import traceback
import symvacas_catalog
from calc_runtime import Budget, ExecutionStopped


class LimitedOutput:
    def __init__(self): self.value = ""

    def write(self, chunk):
        self.value = (self.value + str(chunk))[-40000:]
        return len(chunk)

    def flush(self): pass

    def getvalue(self): return self.value


def run(payload, input_bridge=None, control=None):
    request = json.loads(payload)
    source = request.get("source", "")
    filename = request.get("filename", "script.py")
    output = LimitedOutput()
    namespace = {"__name__": "__main__", "__file__": filename}
    script_builtins = vars(builtins).copy()
    def script_input(prompt=""):
        if input_bridge is None:
            raise EOFError("No input is available")
        value = input_bridge.request(str(prompt), output.getvalue())
        if control is not None and control.isCancelled():
            raise ExecutionStopped("Calculation cancelled")
        output.write(str(prompt) + value + "\n")
        return value
    script_builtins["input"] = script_input
    namespace["__builtins__"] = script_builtins
    symvacas_catalog.set_context(request.get("functions"),request.get("variables"),request.get("assumptions"))
    try:
        # Android owns the wall deadline and pauses it during input(). Here we only
        # poll cancellation, including tight loops in user scripts and catalog calls.
        guard = Budget(float("inf"), float("inf"), control) if control is not None else contextlib.nullcontext()
        with contextlib.redirect_stdout(output), contextlib.redirect_stderr(output), guard:
            exec(compile(source, filename, "exec"), namespace)
        return json.dumps({"ok": True, "output": output.getvalue()}, ensure_ascii=False)
    except ExecutionStopped as exc:
        return json.dumps({"ok": False, "output": output.getvalue(), "error": str(exc)}, ensure_ascii=False)
    except BaseException:
        return json.dumps({"ok": False, "output": output.getvalue(),
                           "error": traceback.format_exc(limit=12)[-12000:]}, ensure_ascii=False)
    finally:
        symvacas_catalog.set_context()
