"""First-party MCP stdio fixture and probe for a disposable PostgreSQL database."""

import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import sys
from datetime import datetime, timezone

WORK = Path("/tmp/forbidden-capability-pilot")
DATA = WORK / "data"
POSTGRES = "/usr/libexec/postgresql17"
BASE_ENV = {"PATH": f"{POSTGRES}:/usr/local/bin:/usr/bin:/bin", "HOME": "/tmp", "LC_ALL": "C"}
PROTOCOL = "2025-06-18"
READ_CALL = {"name": "read_item", "arguments": {}}
WRITE_CALL = {"name": "rename_item", "arguments": {"name": "after"}}


def run(command, *, env=BASE_ENV, script=None, check=True):
    result = subprocess.run(command, env=env, input=script, capture_output=True, text=True, timeout=15)
    if check and result.returncode:
        # PostgreSQL output may contain setup SQL. Only print non-sensitive diagnostics.
        raise RuntimeError(f"{command[0]} failed with exit status {result.returncode}")
    return result


def database(role, password, sql, *, check=True):
    return run([
        "psql", "-X", "-w", "-qAt", "-h", str(WORK), "-d", "postgres", "-U", role,
        "-v", "ON_ERROR_STOP=1", "-v", "VERBOSITY=verbose",
    ], env={**BASE_ENV, "PGPASSWORD": password, "PGCONNECT_TIMEOUT": "3"}, script=sql, check=check)


def serve(role):
    schemas = [
        {"name": "read_item", "inputSchema": {"type": "object", "additionalProperties": False}},
        {"name": "rename_item", "inputSchema": {"type": "object", "required": ["name"],
         "properties": {"name": {"type": "string", "enum": ["after"]}}, "additionalProperties": False}},
    ]
    for line in sys.stdin:
        request = json.loads(line)
        method = request["method"]
        if method == "notifications/initialized":
            continue
        reply = {"jsonrpc": "2.0", "id": request["id"]}
        if method == "initialize":
            reply["result"] = {"protocolVersion": PROTOCOL, "capabilities": {"tools": {}},
                               "serverInfo": {"name": "forbidden-capability-fixture", "version": "1"}}
        elif method == "tools/list":
            reply["result"] = {"tools": schemas}
        elif method == "tools/call" and request["params"] in (READ_CALL, WRITE_CALL):
            sql = "SELECT current_user, name FROM public.fixture_item WHERE id = 1;"
            if request["params"] == WRITE_CALL:
                sql = "UPDATE public.fixture_item SET name = 'after' WHERE id = 1 RETURNING current_user, name;"
            result = database(role, os.environ["PGPASSWORD"], sql, check=False)
            if result.returncode == 0:
                actual_role, name = result.stdout.strip().split("|")
                content = {"role": actual_role, "name": name}
            else:
                match = re.search(r"ERROR:\s+([0-9A-Z]{5}):", result.stderr)
                content = {"sqlstate": match.group(1)} if match else {"reason": "DATABASE_CALL_FAILED"}
            reply["result"] = {"isError": result.returncode != 0,
                               "content": [{"type": "text", "text": json.dumps(content)}]}
        else:
            reply["error"] = {"code": -32602, "message": "Unsupported fixture request"}
        print(json.dumps(reply), flush=True)


def session(role, password, calls):
    requests = [{"jsonrpc": "2.0", "id": 1, "method": "initialize", "params": {
        "protocolVersion": PROTOCOL, "capabilities": {}, "clientInfo": {"name": "fixture-probe", "version": "1"},
    }}, {"jsonrpc": "2.0", "method": "notifications/initialized"},
        {"jsonrpc": "2.0", "id": 2, "method": "tools/list"}]
    requests.extend({"jsonrpc": "2.0", "id": index, "method": "tools/call", "params": call}
                    for index, call in enumerate(calls, 3))
    result = run([sys.executable, __file__, "serve", role], env={**BASE_ENV, "PGPASSWORD": password},
                 script="\n".join(map(json.dumps, requests)) + "\n")
    responses = [json.loads(line) for line in result.stdout.splitlines()]
    if ([response.get("id") for response in responses] != list(range(1, len(calls) + 3))
            or any(response.get("jsonrpc") != "2.0" for response in responses)
            or responses[0]["result"]["protocolVersion"] != PROTOCOL
            or [tool["name"] for tool in responses[1]["result"]["tools"]] != ["read_item", "rename_item"]):
        raise RuntimeError("Incorrect fixture handshake or tool list")
    return [dict(json.loads(item["result"]["content"][0]["text"]), isError=item["result"]["isError"])
            if "result" in item else {"rpcError": item["error"]["code"]} for item in responses[2:]]


def probe():
    started = datetime.now(timezone.utc)
    roles = ("fixture_admin", "fixture_reader", "fixture_writer")
    admin, reader, writer = (secrets.token_hex(24) for _ in roles)
    WORK.mkdir(mode=0o700)
    password_file = WORK / "bootstrap-password"
    password_file.touch(mode=0o600)
    password_file.write_text(admin + "\n")
    try:
        run(["initdb", "-D", str(DATA), "-U", roles[0], "--pwfile", str(password_file),
             "--auth=scram-sha-256", "--no-locale", "--encoding=UTF8"])
    finally:
        password_file.unlink(missing_ok=True)
    run(["pg_ctl", "-D", str(DATA), "-l", str(WORK / "postgres.log"), "-w", "-t", "10",
         "-o", f"-h '' -k {WORK} -c unix_socket_permissions=0700", "start"])
    try:
        database(roles[0], admin, f"""
            CREATE ROLE fixture_reader LOGIN PASSWORD '{reader}';
            CREATE ROLE fixture_writer LOGIN PASSWORD '{writer}';
            CREATE TABLE public.fixture_item (id integer PRIMARY KEY, name text NOT NULL);
            INSERT INTO public.fixture_item VALUES (1, 'before');
            REVOKE ALL ON public.fixture_item FROM PUBLIC;
            GRANT SELECT ON public.fixture_item TO fixture_reader, fixture_writer;
            GRANT UPDATE (name) ON public.fixture_item TO fixture_writer;
        """)
        sql = "SELECT name FROM public.fixture_item WHERE id = 1;"
        before = database(roles[0], admin, sql).stdout.strip()
        bad_password, = session(roles[1], secrets.token_hex(24), [READ_CALL])
        read, denied = session(roles[1], reader, [READ_CALL, WRITE_CALL])
        after_reader = database(roles[0], admin, sql).stdout.strip()
        bad_argument, written = session(roles[2], writer, [
            {"name": "rename_item", "arguments": {"name": "outside-fixture"}}, WRITE_CALL,
        ])
        after_writer = database(roles[0], admin, sql).stdout.strip()
        postgres_version = database(roles[0], admin, "SHOW server_version;").stdout.strip()
    finally:
        run(["pg_ctl", "-D", str(DATA), "-m", "fast", "-w", "-t", "5", "stop"])

    print(json.dumps({
        "startedAt": started.isoformat(), "runtimeMs": int((datetime.now(timezone.utc) - started).total_seconds() * 1000),
        "uid": os.getuid(), "gid": os.getgid(), "postgresVersion": postgres_version,
        "pythonVersion": sys.version.split()[0], "transport": "stdio", "protocolVersion": PROTOCOL,
        "databaseTransport": "unix-domain socket", "databaseAuth": "scram-sha-256",
        "writeCall": WRITE_CALL, "readerRead": read, "readerWrite": denied, "writerWrite": written,
        "invalidCredentialRead": bad_password, "invalidArgumentWrite": bad_argument,
        "databaseState": {"before": before, "afterReader": after_reader, "afterWriter": after_writer},
    }))


if __name__ == "__main__":
    if len(sys.argv) == 3 and sys.argv[1] == "serve" and sys.argv[2] in ("fixture_reader", "fixture_writer"):
        serve(sys.argv[2])
    elif len(sys.argv) == 1:
        probe()
    else:
        sys.exit("Unsupported fixture invocation")
