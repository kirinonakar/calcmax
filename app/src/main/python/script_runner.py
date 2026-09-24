"""Execute a user-authored script in the app's separate Python service process."""
import contextlib
import builtins
import json
import traceback
import calcmax_catalog


class LimitedOutput:
    def __init__(self): self.value = ""

    def write(self, chunk):
        self.value = (self.value + str(chunk))[-40000:]
        return len(chunk)

    def flush(self): pass

    def getvalue(self): return self.value


def run(payload, input_bridge=None):
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
        output.write(str(prompt) + value + "\n")
        return value
    script_builtins["input"] = script_input
    namespace["__builtins__"] = script_builtins
    calcmax_catalog.set_context(request.get("functions"),request.get("variables"),request.get("assumptions"))
    try:
        with contextlib.redirect_stdout(output), contextlib.redirect_stderr(output):
            exec(compile(source, filename, "exec"), namespace)
        return json.dumps({"ok": True, "output": output.getvalue()}, ensure_ascii=False)
    except BaseException:
        return json.dumps({"ok": False, "output": output.getvalue(),
                           "error": traceback.format_exc(limit=12)[-12000:]}, ensure_ascii=False)
    finally:
        calcmax_catalog.set_context()
