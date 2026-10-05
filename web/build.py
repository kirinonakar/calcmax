"""Build a self-hosted static SymvaCAS distribution; no server-side Python at runtime."""
import argparse
import hashlib
import json
import pathlib
import re
import shutil
import urllib.request
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent
PROJECT = ROOT.parent
VERSION = "314.0.7"
BASE = f"https://cdn.jsdelivr.net/pyodide/v{VERSION}/full/"


def download(name, url=None):
    target = ROOT / "vendor" / name
    if target.is_file():
        return target
    print(f"Downloading {name}", flush=True)
    target.parent.mkdir(parents=True, exist_ok=True)
    with urllib.request.urlopen(url or BASE + name, timeout=120) as response:
        data = response.read()
    temporary = target.with_suffix(target.suffix + ".tmp")
    temporary.write_bytes(data)
    temporary.replace(target)
    return target


def build(skip_download=False, output=None):
    shutil.copyfile(PROJECT / "LICENSE", ROOT / "LICENSE")
    app_version = re.search(r'versionName\s*=\s*"([^"]+)"', (PROJECT / "app/build.gradle.kts").read_text(encoding="utf-8")).group(1)
    (ROOT / "app-version.js").write_text("export const appVersion = " + json.dumps(app_version) + ";\n", encoding="utf-8")
    ui_source = PROJECT / "app/src/main/java/com/kirinonakar/symvacas/ui"
    probability_schema = json.loads((PROJECT / "app/src/main/assets/probability.json").read_text(encoding="utf-8"))
    (ROOT / "probability-schema.js").write_text("export const probabilitySchema = " + json.dumps(probability_schema,ensure_ascii=False,indent=2) + ";\n",encoding="utf-8")
    statistics_schema = json.loads((PROJECT / "app/src/main/assets/advanced_statistics.json").read_text(encoding="utf-8"))
    (ROOT / "advanced-statistics-schema.js").write_text("export const advancedStatisticsSchema = " + json.dumps(statistics_schema,ensure_ascii=False,indent=2) + ";\n",encoding="utf-8")
    groups = {name: re.findall(r'"([^"\n]+)"', items) for name, items in re.findall(r'"([^"\n]+)" to listOf\(([^\n]+)\)', (ui_source / "UnitsConstantsScreens.kt").read_text(encoding="utf-8"))}
    native_locale = dict(re.findall(r'"([^"\n]+)" to "([^"\n]+)"', (ui_source / "Localization.kt").read_text(encoding="utf-8")))
    for filename, symbol, data in [("unit-groups.js", "unitGroups", groups), ("native-locale.js", "nativeKorean", native_locale)]:
        (ROOT / filename).write_text(f"export const {symbol} = " + json.dumps(data, ensure_ascii=False, indent=2) + ";\n", encoding="utf-8")
    with zipfile.ZipFile(ROOT / "engine.zip", "w", zipfile.ZIP_DEFLATED) as archive:
        for source in sorted((PROJECT / "app/src/main/python").glob("*.py")):
            archive.write(source, source.name)
    catalog_source = (PROJECT / "app/src/main/java/com/kirinonakar/symvacas/ui/Catalog.kt").read_text(encoding="utf-8")
    catalog = {}
    for category, entries in re.findall(r'"([^"\n]+)" to listOf\(([^\n]+)\)', catalog_source):
        catalog[category] = re.findall(r'"([^"\n]+)"', entries)
    (ROOT / "catalog.json").write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    for language in ("", "_ko"):
        name = f"catalog_help{language}.md"
        (ROOT / name).write_bytes((PROJECT / "app/src/main/assets" / name).read_bytes())
    fixtures = ROOT / "tests/fixtures"
    fixtures.mkdir(exist_ok=True)
    kotlin_cases = PROJECT / "build/math-cases.json"
    if kotlin_cases.exists():
        shutil.copyfile(kotlin_cases, fixtures / "math-cases.json")
    if skip_download:
        assets_manifest()
        if output:
            publish_directory(output)
        return
    (ROOT / "vendor").mkdir(exist_ok=True)
    for name in ("pyodide.js", "pyodide.mjs", "pyodide.asm.mjs", "pyodide.asm.wasm", "python_stdlib.zip", "pyodide-lock.json"):
        download(name)
    lock = json.loads((ROOT / "vendor/pyodide-lock.json").read_text(encoding="utf-8"))
    installed = set()

    def package(name):
        if name in installed:
            return
        installed.add(name)
        item = lock["packages"][name]
        for dependency in item.get("depends", []):
            package(dependency)
        path = download(item["file_name"])
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        if digest != item["sha256"]:
            path.unlink()
            raise RuntimeError(f"Checksum mismatch: {path.name}; run the build again")

    package("sympy")
    download("licenses/pyodide-LICENSE.txt", f"https://raw.githubusercontent.com/pyodide/pyodide/{VERSION}/LICENSE")
    download("licenses/python-LICENSE.txt", f"https://raw.githubusercontent.com/python/cpython/v{lock['info']['python']}/LICENSE")
    licenses = ROOT / "vendor/licenses"
    licenses.mkdir(exist_ok=True)
    for wheel in (ROOT / "vendor").glob("*.whl"):
        with zipfile.ZipFile(wheel) as archive:
            for name in archive.namelist():
                if name.endswith(("/LICENSE", "/LICENSE.txt", "/COPYING")):
                    (licenses / (wheel.name.split('-')[0] + '-LICENSE.txt')).write_bytes(archive.read(name))
    assets_manifest()
    if output:
        publish_directory(output)
    print(f"Ready: {ROOT} (Pyodide {VERSION}, SymPy {lock['packages']['sympy']['version']})", flush=True)


def assets_manifest():
    files = sorted(path for path in ROOT.iterdir() if (path.suffix in (".html", ".css", ".js", ".json", ".zip", ".md", ".webp", ".png") or path.name == "LICENSE") and path.name not in ("assets.js", "package.json", "package-lock.json", "design-qa.md"))
    files += sorted(path for path in (ROOT / "vendor").rglob("*") if path.is_file() and not path.name.endswith(".tmp"))
    files += sorted(path for path in (ROOT / "fonts").rglob("*") if path.is_file())
    digest = hashlib.sha256()
    for path in files:
        digest.update(path.relative_to(ROOT).as_posix().encode())
        digest.update(path.read_bytes())
    assets = ["./"] + ["./" + path.relative_to(ROOT).as_posix() for path in files]
    source = f"self.SYMVACAS_CACHE = 'symvacas-static-{digest.hexdigest()[:16]}';\nself.SYMVACAS_ASSETS = {json.dumps(assets, indent=2)};\n"
    (ROOT / "assets.js").write_text(source, encoding="utf-8")


def publish_directory(output):
    """Copy only the static files into a Pages artifact; no server or npm files."""
    target = pathlib.Path(output).resolve()
    if target == ROOT or ROOT in target.parents:
        raise ValueError("Choose an output directory outside web/, e.g. build/web")
    target.mkdir(parents=True, exist_ok=True)
    source = (ROOT / "assets.js").read_text(encoding="utf-8")
    assets = json.loads(re.search(r"self.SYMVACAS_ASSETS = (\[[\s\S]+\]);", source).group(1))
    for name in ["./assets.js", *assets]:
        if name == "./":
            continue
        relative = pathlib.Path(name.removeprefix("./"))
        destination = target / relative
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(ROOT / relative, destination)
    (target / ".nojekyll").write_text("", encoding="utf-8")
    print(f"Static Pages artifact: {target}", flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--skip-download", action="store_true", help="Refresh the engine and catalog only")
    parser.add_argument("--output", help="Copy the deployable site to this directory, e.g. build/web")
    options = parser.parse_args()
    build(options.skip_download, options.output)
