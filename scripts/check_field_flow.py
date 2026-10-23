#!/usr/bin/env python3
"""Every field an app saves has to reach the database - and only that field.

"when i click that a certain users phone canot sync ... it saves i click the
account again it is stll saved ... check this and any other area that might
have this issue" (24 October 2026). The desktop sent canSyncSms and the
backend saves it, but the admin server's request model didn't list it, so
FastAPI dropped it without a word: the window closed as if saved.

This follows every save from the desktop app, the phone app and the website
through its own server into the backend's save method, and reports:
  LOST    - the app sends a field its server doesn't pass on (dropped
            silently - the bug above)
  WIPED   - an edit from this app empties a field: the backend overwrites
            it, and the app never sends it
  RESET   - the same, but the server fills in a default (e.g. active = true)
  IGNORED - the app's field reaches the backend under a name it doesn't read
  CHECK   - something this script can't follow; look at it by hand
Fields listed in KNOWN were checked by hand and are meant to be so.

Run from the project folder:  python3 scripts/check_field_flow.py
The phone app's code is read from mobile-app/ (the copy kept here), or from
PHONE_APP_DIR. Exit code 1 when anything is LOST, WIPED, RESET or IGNORED.
"""
import ast
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BACKEND = os.path.join(ROOT, "backend", "src", "main", "java")
SERVERS = {
    "admin": os.path.join(ROOT, "admin_server_control", "server", "app"),
    "mobile": os.path.join(ROOT, "mobileServer", "app"),
    "website": os.path.join(ROOT, "frontend", "app"),
}
DESKTOP = os.path.join(ROOT, "admin_server_control", "desktop", "app")
PHONE = os.environ.get("PHONE_APP_DIR") or os.path.join(ROOT, "mobile-app")
WEBSITE = os.path.join(ROOT, "frontend", "website", "src")

# (app, method, path, field): why it is fine. Checked by hand.
KNOWN = {
    ("desktop", "PATCH", "/workers/{}/{}", "-"): "the address is /workers/fields/<key> or /workers/doc-types/<key>",
    ("desktop", "PATCH", "/users/{}", "userName"):
        "UserService.changedUserName() leaves the username alone when none is sent (Make SuperAdmin sends only the role)",
    ("desktop", "PATCH", "/deliveries/light/{}/assign", "shareDriver"):
        "on purpose: a new assignment starts with the driver hidden (privacy); under a parent delivery it follows the parent; "
        "staff switch it on afterwards with Share Driver",
    ("desktop", "PATCH", "/deliveries/bulk/{}/finalize", "productDescription"):
        "the desktop's payment window sends no productType, so no production row is written",
    ("desktop", "PATCH", "/deliveries/bulk/{}/finalize", "productMeasurement"): "as productDescription",
    ("desktop", "PATCH", "/deliveries/bulk/{}/finalize", "productTotal"): "as productDescription",
    ("desktop", "PATCH", "/deliveries/bulk/{}/finalize", "totalBodyFeet"): "as productDescription",
    ("website", "POST", "/auth/register", "-"):
        "checked by hand 24 October 2026: AuthModal sends user_name, name, password, email, phone_no, phone_proof; "
        "frontend/app/routers/auth.py passes all six on",
}


def norm(path: str) -> str:
    path = re.sub(r"\{[^}]*\}", "{}", path.split("?")[0])
    return re.sub(r"/+", "/", "/" + path.strip("/"))


# ---------------------------------------------------------------- java
def strip_java_comments(src: str) -> str:
    out, i, n = [], 0, len(src)
    while i < n:
        c = src[i]
        if c == '"':
            if src.startswith('"""', i):
                j = src.index('"""', i + 3) + 3
            else:
                j = i + 1
                while j < n and src[j] != '"':
                    j += 2 if src[j] == "\\" else 1
                j += 1
            out.append(src[i:j])
            i = j
        elif c == "'" and i + 2 < n and (src[i + 2] == "'" or src[i + 1] == "\\"):
            j = src.index("'", i + 2) + 1
            out.append(src[i:j])
            i = j
        elif src.startswith("//", i):
            i = src.find("\n", i)
            i = n if i < 0 else i
        elif src.startswith("/*", i):
            i = src.index("*/", i) + 2
        else:
            out.append(c)
            i += 1
    return "".join(out)


def matching(src: str, start: int, open_c: str, close_c: str) -> int:
    depth = 0
    for i in range(start, len(src)):
        if src[i] == open_c:
            depth += 1
        elif src[i] == close_c:
            depth -= 1
            if depth == 0:
                return i
    return len(src)


def split_top(text: str) -> list:
    parts, depth, cur, quote = [], 0, [], None
    for i, c in enumerate(text):
        if quote:
            cur.append(c)
            if c == quote and text[i - 1] != "\\":
                quote = None
            continue
        if c in "\"'":
            quote = c
            cur.append(c)
            continue
        if c in "<([{":
            depth += 1
        elif c in ">)]}":
            depth -= 1
        if c == "," and depth == 0:
            parts.append("".join(cur))
            cur = []
        else:
            cur.append(c)
    if "".join(cur).strip():
        parts.append("".join(cur))
    return parts


def java_method_body(src: str, name: str):
    for m in re.finditer(r"\b" + name + r"\s*\(", src):
        before = src[max(0, m.start() - 160):m.start()]
        if not re.search(r"(public|private|protected)[^;{}()=]*$", before):
            continue
        close = matching(src, m.end() - 1, "(", ")")
        brace = src.find("{", close)
        if brace < 0 or src[close:brace].strip(" \n\t").startswith(("->", ";")) or ";" in src[close:brace]:
            continue
        return src[close:matching(src, brace, "{", "}")], src[m.end():close]
    return None, None


def sql_guarded(scope: str, fields: set) -> set:
    """Fields an UPDATE keeps when they arrive empty: Col = COALESCE(?, Col)
    or Col = IF(? IS NULL, Col, ...), matched to the argument in that place."""
    guarded = set()
    for call in re.finditer(r"jdbcTemplate\.update\(", scope):
        args = split_top(scope[call.end():matching(scope, call.end() - 1, "(", ")")])
        if len(args) < 2:
            continue
        sql = "".join(re.findall(r'"((?:[^"\\]|\\.)*)"', args[0]))
        marks = [m.start() for m in re.finditer(r"\?", sql)]
        for g in re.finditer(r"(\w+)\s*=\s*(?:COALESCE\(\s*\?\s*,\s*\1\s*\)|IF\(\s*\?\s*IS\s+NULL\s*,\s*\1\b)", sql, re.I):
            place = marks.index(sql.index("?", g.start()))
            if place + 1 >= len(args):
                continue
            arg = args[place + 1].strip()
            source = arg
            if re.fullmatch(r"\w+", arg):
                assigned = re.search(r"\b" + arg + r"\s*=\s*([^;]*);", scope)
                source = assigned.group(1) if assigned else arg
            guarded |= {f for f in fields if re.search(r"\." + f + r"\(\)", source)}
    return guarded


def backend_index():
    sources = {}
    for folder, _dirs, files in os.walk(BACKEND):
        for name in files:
            if name.endswith(".java"):
                path = os.path.join(folder, name)
                sources[name[:-5]] = strip_java_comments(open(path, encoding="utf-8").read())
    records = {}
    for src in sources.values():
        for m in re.finditer(r"\brecord\s+(\w+)\s*\(", src):
            end = matching(src, m.end() - 1, "(", ")")
            names = [re.findall(r"\w+", p) for p in split_top(src[m.end():end])]
            records[m.group(1)] = [n[-1] for n in names if n]
    routes = {}
    for cls_name, src in sources.items():
        if "@RestController" not in src:
            continue
        cls = re.search(r'@RequestMapping\(\s*(?:value\s*=\s*)?"([^"]*)"\s*\)\s*(?:@\w+\s*)*public\s+class', src)
        prefix = cls.group(1) if cls else ""
        for m in re.finditer(r"@(?:[\w.]+\.)?(Get|Post|Put|Patch|Delete)Mapping\b(\(([^)]*)\))?", src):
            paths = re.findall(r'"([^"]*)"', m.group(3) or "") or [""]
            sig_open = src.index("(", src.index(" ", src.index("public", m.end())))
            sig_close = matching(src, sig_open, "(", ")")
            params = src[sig_open + 1:sig_close]
            body_open = src.index("{", sig_close)
            body = src[body_open:matching(src, body_open, "{", "}")]
            info = None
            rb = re.search(r"@RequestBody(?:\([^)]*\))?\s+(?:final\s+)?([\w.]+(?:<[^>]*>)?)\s+(\w+)", params)
            if rb and rb.group(1) in records:
                type_name, var = rb.group(1), rb.group(2)
                fields = set(records[type_name])
                # Where the record goes: the service method it is handed to.
                used, guarded, where = set(), set(), cls_name
                call = re.search(r"(\w+)\.(\w+)\([^;]*\b" + var + r"\b", body)
                service_body = None
                if call:
                    receiver = call.group(1)
                    service_cls = receiver[:1].upper() + receiver[1:]
                    if service_cls in sources:
                        service_body, service_params = java_method_body(sources[service_cls], call.group(2))
                        where = f"{service_cls}.{call.group(2)}"
                scope = (service_body or "") + body
                used = {f for f in fields if re.search(r"\." + f + r"\(\)", scope)}
                # A field read only behind "!= null" is kept when it isn't sent.
                guarded = {f for f in used if re.search(r"\." + f + r"\(\)\s*!=\s*null", scope)} | sql_guarded(scope, used)
                info = {"kind": "record " + type_name, "fields": fields, "used": used, "guarded": guarded,
                        "where": where, "found": service_body is not None}
            elif rb and rb.group(1).startswith("Map"):
                keys = set(re.findall(r'\.get(?:OrDefault)?\(\s*"(\w+)"', body))
                info = {"kind": "map", "fields": keys, "used": keys, "guarded": keys, "where": cls_name, "found": True}
            elif rb:
                info = {"kind": "other", "fields": None, "used": set(), "guarded": set(), "where": cls_name, "found": True}
            for p in paths:
                routes[(m.group(1).upper(), norm(prefix + "/" + p))] = info
    return routes


# ---------------------------------------------------------------- servers
def py_files(root):
    for folder, _dirs, files in os.walk(root):
        if "venv" in folder or "__pycache__" in folder:
            continue
        for name in files:
            if name.endswith(".py"):
                yield os.path.join(folder, name)


def path_text(node):
    if isinstance(node, ast.Constant) and isinstance(node.value, str):
        return node.value
    if isinstance(node, ast.JoinedStr):
        return "".join(v.value if isinstance(v, ast.Constant) else "{x}" for v in node.values)
    return None


def server_index(root):
    classes, bases = {}, {}
    for path in py_files(root):
        tree = ast.parse(open(path, encoding="utf-8").read())
        for node in ast.walk(tree):
            if isinstance(node, ast.ClassDef):
                fields = {}
                for s in node.body:
                    if isinstance(s, ast.AnnAssign) and isinstance(s.target, ast.Name) and s.target.id != "model_config":
                        default = "required"
                        if s.value is not None:
                            v = s.value
                            if isinstance(v, ast.Call) and getattr(v.func, "id", "") == "Field":
                                v = next((kw.value for kw in v.keywords if kw.arg == "default"), v.args[0] if v.args else None)
                            default = None if (v is None or (isinstance(v, ast.Constant) and v.value is None)) else ast.unparse(v)
                        fields[s.target.id] = default
                classes[node.name] = fields
                bases[node.name] = [b.id for b in node.bases if isinstance(b, ast.Name)]

    def is_model(name, seen=()):
        return name == "BaseModel" or any(is_model(b, seen + (name,)) for b in bases.get(name, []) if b not in seen)

    def all_fields(name):
        out = {}
        for b in bases.get(name, []):
            if b in classes:
                out.update(all_fields(b))
        out.update(classes.get(name, {}))
        return out

    models = {n: all_fields(n) for n in classes if is_model(n)}
    routes = {}
    for path in py_files(os.path.join(root, "routers")):
        tree = ast.parse(open(path, encoding="utf-8").read())
        prefix = ""
        for node in ast.walk(tree):
            if isinstance(node, ast.Call) and getattr(node.func, "id", "") == "APIRouter":
                prefix = next((kw.value.value for kw in node.keywords if kw.arg == "prefix"), "")
        for fn in tree.body:
            if not isinstance(fn, (ast.FunctionDef, ast.AsyncFunctionDef)):
                continue
            for dec in fn.decorator_list:
                if not (isinstance(dec, ast.Call) and isinstance(dec.func, ast.Attribute)
                        and dec.func.attr in ("get", "post", "put", "patch", "delete")):
                    continue
                model_args = {}
                for a in fn.args.args:
                    ann = a.annotation
                    name = ann.id if isinstance(ann, ast.Name) else None
                    if isinstance(ann, ast.Subscript) and isinstance(ann.slice, ast.Name):
                        name = ann.slice.id
                    if name in models:
                        model_args[a.arg] = name
                    elif name in ("dict", "Dict"):
                        model_args[a.arg] = "dict"
                calls = []
                for node in ast.walk(fn):
                    if (isinstance(node, ast.Call) and isinstance(node.func, ast.Attribute)
                            and getattr(node.func.value, "id", "") == "backend_client" and node.args):
                        target = path_text(node.args[0])
                        body = next((kw.value for kw in node.keywords if kw.arg == "json"), None)
                        if target and body is not None:
                            calls.append((node.func.attr.upper(), norm(target), body))
                route_path = path_text(dec.args[0]) if dec.args else ""
                routes[(dec.func.attr.upper(), norm(prefix + "/" + (route_path or "")))] = {
                    "fn": fn, "models": model_args, "calls": calls, "file": os.path.basename(path)}
    return models, routes


def forwarded(expr, fn, models, model_args, depth=0):
    """The keys a server passes on: (set, "any", or None if unreadable)."""
    if depth > 4:
        return None
    if isinstance(expr, ast.Call) and isinstance(expr.func, ast.Attribute) and expr.func.attr == "model_dump":
        model = model_args.get(getattr(expr.func.value, "id", None))
        if model and model != "dict":
            keys = set(models[model])
            for kw in expr.keywords:
                if kw.arg == "exclude" and isinstance(kw.value, (ast.Set, ast.List)):
                    keys -= {e.value for e in kw.value.elts if isinstance(e, ast.Constant)}
            return keys
        return None
    if isinstance(expr, ast.Name) and model_args.get(expr.id) == "dict":
        return "any"
    if isinstance(expr, ast.Dict):
        keys = set()
        for k, v in zip(expr.keys, expr.values):
            if k is None:
                inner = forwarded(v, fn, models, model_args, depth + 1)
                if not isinstance(inner, set):
                    return inner
                keys |= inner
            elif isinstance(k, ast.Constant):
                keys.add(k.value)
        return keys
    if isinstance(expr, ast.DictComp):
        it = expr.generators[0].iter
        if isinstance(it, ast.Call) and isinstance(it.func, ast.Attribute) and it.func.attr == "items":
            return forwarded(it.func.value, fn, models, model_args, depth + 1)
        return None
    if isinstance(expr, ast.Name):
        keys, found = None, False
        for node in ast.walk(fn):
            if isinstance(node, ast.Assign):
                for t in node.targets:
                    if isinstance(t, ast.Name) and t.id == expr.id:
                        keys, found = forwarded(node.value, fn, models, model_args, depth + 1), True
                    elif (isinstance(t, ast.Subscript) and getattr(t.value, "id", "") == expr.id
                          and isinstance(t.slice, ast.Constant) and isinstance(keys, set)):
                        keys.add(t.slice.value)
        return keys if found else None
    return None


# ---------------------------------------------------------------- apps
def spec_keys(node) -> set:
    keys = set()
    for n in ast.walk(node):
        if isinstance(n, ast.Call) and getattr(n.func, "id", "") == "FieldSpec" and n.args \
                and isinstance(n.args[0], ast.Constant):
            keys.add(n.args[0].value)
            for kw in n.keywords:
                if kw.arg == "type_key" and isinstance(kw.value, ast.Constant):
                    keys.add(kw.value.value)
    return keys


def form_keys(chain, module_specs, defs) -> set:
    """The FieldSpec keys of the nearest function that builds a form: its
    own FieldSpecs, those of helpers it calls (self._fields()) and
    module-level lists it names."""
    for fn in chain:
        keys = spec_keys(fn)
        for n in ast.walk(fn):
            if isinstance(n, ast.Name) and n.id in module_specs:
                keys |= module_specs[n.id]
            if isinstance(n, ast.Call):
                name = n.func.attr if isinstance(n.func, ast.Attribute) else getattr(n.func, "id", None)
                if name in defs and defs[name] is not fn:
                    keys |= spec_keys(defs[name])
                    for m in ast.walk(defs[name]):
                        if isinstance(m, ast.Name) and m.id in module_specs:
                            keys |= module_specs[m.id]
        if keys:
            return keys
    return set()


def desktop_keys(body, chain, module_specs, defs):
    fn = chain[0]
    if isinstance(body, ast.Dict):
        keys = set()
        for k, v in zip(body.keys, body.values):
            if k is None:
                inner = desktop_keys(v, chain, module_specs, defs)
                if inner is None:
                    return None
                keys |= inner
            elif isinstance(k, ast.Constant):
                keys.add(k.value)
        return keys
    if isinstance(body, ast.Name):
        # save_fn=lambda values: api.post(..., json=values) in a form: the
        # form sends its FieldSpec keys.
        for scope in chain:
            for node in ast.walk(scope):
                if isinstance(node, ast.Lambda) and body.id in [a.arg for a in node.args.args]:
                    return form_keys(chain, module_specs, defs) or None
        # def save(values): values["tripId"] = ...; api.post(..., json=values)
        # handed to a form as save_fn: the form's keys, plus what save (and
        # helpers it hands values to) adds, minus what they pop.
        if body.id in [a.arg for a in fn.args.args]:
            keys = form_keys(chain[1:] or chain, module_specs, defs)
            if not keys:
                return None
            scopes = [fn]
            for n in ast.walk(fn):
                if isinstance(n, ast.Call) and any(isinstance(a, ast.Name) and a.id == body.id for a in n.args):
                    name = n.func.attr if isinstance(n.func, ast.Attribute) else getattr(n.func, "id", None)
                    if name in defs:
                        scopes.append(defs[name])
            for scope in scopes:
                for n in ast.walk(scope):
                    if (isinstance(n, ast.Assign) and isinstance(n.targets[0], ast.Subscript)
                            and getattr(n.targets[0].value, "id", "") == body.id
                            and isinstance(n.targets[0].slice, ast.Constant)):
                        keys.add(n.targets[0].slice.value)
                    if (isinstance(n, ast.Call) and isinstance(n.func, ast.Attribute) and n.func.attr == "pop"
                            and getattr(n.func.value, "id", "") == body.id and n.args
                            and isinstance(n.args[0], ast.Constant)):
                        keys.discard(n.args[0].value)
            return keys
        for scope in chain:
            for node in ast.walk(scope):
                if isinstance(node, ast.Assign) and any(isinstance(t, ast.Name) and t.id == body.id for t in node.targets):
                    keys = desktop_keys(node.value, chain, module_specs, defs)
                    if keys is not None:
                        for n in ast.walk(scope):
                            if (isinstance(n, ast.Assign) and isinstance(n.targets[0], ast.Subscript)
                                    and getattr(n.targets[0].value, "id", "") == body.id
                                    and isinstance(n.targets[0].slice, ast.Constant)):
                                keys.add(n.targets[0].slice.value)
                    return keys
    return None


def desktop_bodies():
    out = []
    for path in py_files(DESKTOP):
        tree = ast.parse(open(path, encoding="utf-8").read())
        module_specs = {}
        for node in tree.body:
            if isinstance(node, ast.Assign) and isinstance(node.value, ast.List):
                keys = spec_keys(node.value)
                for t in node.targets:
                    if keys and isinstance(t, ast.Name):
                        module_specs[t.id] = keys
        defs = {n.name: n for n in ast.walk(tree) if isinstance(n, (ast.FunctionDef, ast.AsyncFunctionDef))}
        parents = {}
        for parent in ast.walk(tree):
            for child in ast.iter_child_nodes(parent):
                parents[child] = parent
        for node in ast.walk(tree):
            if not (isinstance(node, ast.Call) and isinstance(node.func, ast.Attribute)
                    and node.func.attr in ("post", "put", "patch") and node.args
                    and "api" in ast.unparse(node.func.value)):
                continue
            target = path_text(node.args[0])
            body = next((kw.value for kw in node.keywords if kw.arg == "json"), None)
            if target is None or body is None:
                continue
            chain, up = [], parents.get(node)
            while up is not None:
                if isinstance(up, (ast.FunctionDef, ast.AsyncFunctionDef)):
                    chain.append(up)
                up = parents.get(up)
            if chain:
                keys = desktop_keys(body, chain, module_specs, defs)
                out.append((node.func.attr.upper(), norm(target), keys, f"{os.path.relpath(path, DESKTOP)}:{node.lineno}"))
    return out


def phone_bodies():
    root = os.path.join(PHONE, "app", "src", "main", "java")
    sources = {}
    for folder, _dirs, files in os.walk(root):
        for name in files:
            if name.endswith(".kt"):
                sources[name] = open(os.path.join(folder, name), encoding="utf-8").read()
    classes = {}
    for src in sources.values():
        for m in re.finditer(r"\bdata\s+class\s+(\w+)\s*\(", src):
            end = matching(src, m.end() - 1, "(", ")")
            classes[m.group(1)] = set(re.findall(r"\b(?:val|var)\s+(\w+)\s*:", src[m.end():end]))
    api = sources.get("ApiService.kt", "")
    out = []
    for m in re.finditer(r'@(POST|PUT|PATCH)\("([^"]*)"\)\s*(?:@\w+(?:\([^)]*\))?\s*)*suspend\s+fun\s+(\w+)\s*\(', api):
        params = api[m.end():matching(api, m.end() - 1, "(", ")")]
        b = re.search(r"@Body\s+\w+\s*:\s*([\w.]+)", params)
        if not b:
            continue
        if b.group(1) in classes:
            keys = classes[b.group(1)]
        elif b.group(1) in ("Map", "JsonMap", "HashMap"):
            keys = set()
            for src in sources.values():
                for call in re.finditer(r"\." + m.group(3) + r"\(", src):
                    close = matching(src, call.end() - 1, "(", ")")
                    keys |= set(re.findall(r'"(\w+)"\s+to\b', src[call.end():close]))
            keys = keys or None
        else:
            keys = None
        out.append((m.group(1), norm(m.group(2)), keys, "ApiService." + m.group(3)))
    return out


def js_keys(literal: str) -> set:
    keys = set()
    for part in split_top(literal.strip()[1:-1]):
        key = re.match(r"\s*['\"]?(\w+)['\"]?\s*(?::|$)", part)
        if key and not part.strip().startswith("..."):
            keys.add(key.group(1))
    return keys


def website_bodies():
    client = open(os.path.join(WEBSITE, "api", "client.js"), encoding="utf-8").read()
    sources = []
    for folder, _dirs, files in os.walk(WEBSITE):
        for name in files:
            if name.endswith((".js", ".jsx")):
                sources.append(open(os.path.join(folder, name), encoding="utf-8").read())
    out = []
    for m in re.finditer(r"(\w+):\s*\(([^)]*)\)\s*=>\s*(?:\n\s*)?request\(\s*[`']([^`']+)[`']\s*,\s*\{", client):
        opts = client[m.end():matching(client, m.end() - 1, "{", "}")]
        method = re.search(r"method:\s*'(\w+)'", opts)
        body = re.search(r"body:\s*JSON\.stringify\(", opts)
        if not (method and body):
            continue
        expr = opts[body.end():matching(opts, body.end() - 1, "(", ")")].strip()
        if expr.startswith("{"):
            keys = js_keys(expr)
        elif "?" in expr:
            keys = set().union(*[js_keys(p) for p in re.findall(r"\{[^{}]*\}", expr)])
        else:
            # A parameter: the top-level keys of the object the pages pass in.
            keys = set()
            for src in sources:
                for call in re.finditer(r"api\." + m.group(1) + r"\(", src):
                    args = src[call.end():matching(src, call.end() - 1, "(", ")")]
                    lit = args.find("{")
                    if lit >= 0:
                        keys |= js_keys(args[lit:matching(args, lit, "{", "}") + 1])
            keys = keys or None
        out.append((method.group(1), norm(re.sub(r"\$\{[^}]*\}", "{x}", m.group(3))), keys, "client.js " + m.group(1)))
    return out


# ---------------------------------------------------------------- report
def main():
    backend = backend_index()
    servers = {name: server_index(root) for name, root in SERVERS.items()}
    problems, known = [], []

    def report(kind, app, method, path, field, detail):
        why = KNOWN.get((app, method, path, field))
        line = f"{kind:7} {app:8} {method:6} {path:44} {field:20} {detail}"
        (known if why else problems).append(line + (f"\n        known: {why}" if why else ""))

    checked = 0
    for app, server, bodies in (("desktop", "admin", desktop_bodies()), ("phone", "mobile", phone_bodies()),
                                ("website", "website", website_bodies())):
        models, routes = servers[server]
        for method, path, keys, where in bodies:
            route = routes.get((method, path))
            if route is None:
                report("CHECK", app, method, path, "-", f"no {server} server route ({where})")
                continue
            if keys is None:
                report("CHECK", app, method, path, "-", f"can't read what is sent ({where})")
                continue
            checked += 1
            model_names = [m for m in route["models"].values() if m != "dict"]
            accepted = set().union(*(set(models[m]) for m in model_names)) if model_names else None
            if accepted is not None and "dict" not in route["models"].values():
                for f in sorted(keys - accepted):
                    report("LOST", app, method, path, f, f"{route['file']} ({model_names[0]}) has no such field ({where})")
            for call_method, call_path, body in route["calls"]:
                passed = forwarded(body, route["fn"], models, route["models"])
                target = backend.get((call_method, call_path), "missing")
                if target == "missing":
                    report("CHECK", app, method, path, "-", f"backend has no {call_method} {call_path}")
                    continue
                if target is None or target["fields"] is None:
                    continue
                if passed is None:
                    report("CHECK", app, method, path, "-", f"can't read what {route['file']} passes on")
                    continue
                arriving = keys if passed == "any" else (keys & passed)
                # A server field the app doesn't send still arrives - empty, or as its default.
                defaults = {}
                if passed != "any" and model_names:
                    for m in model_names:
                        for f, d in models[m].items():
                            if f in passed and f not in keys:
                                defaults[f] = d
                for f in sorted((keys & (passed if passed != "any" else keys)) - target["fields"]):
                    report("IGNORED", app, method, path, f, f"{target['where']} has no field {f}")
                if call_method in ("PATCH", "PUT") and target["found"]:
                    ids = {f for f in target["used"] if f.endswith("Id") and "{}" in call_path}
                    for f in sorted(target["used"] - target["guarded"] - arriving - ids):
                        d = defaults.get(f)
                        if d not in (None, "required"):
                            report("RESET", app, method, path, f, f"{target['where']} sets it to {d} on every edit ({where})")
                        elif d != "required":
                            report("WIPED", app, method, path, f, f"{target['where']} empties it on every edit ({where})")
                elif call_method in ("PATCH", "PUT"):
                    report("CHECK", app, method, path, "-", f"couldn't find the backend save method ({target['where']})")

    for line in problems:
        print(line)
    if known:
        print("\nChecked by hand:")
        print("\n".join(known))
    print(f"\n{checked} saves followed; {len(problems)} to look at")
    return 1 if any(p.split()[0] in ("LOST", "WIPED", "RESET", "IGNORED") for p in problems) else 0


if __name__ == "__main__":
    sys.exit(main())
