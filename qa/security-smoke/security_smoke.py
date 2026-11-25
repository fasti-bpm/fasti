#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
# Copyright Fasti BPM contributors
"""Checks that the security controls of a running Fasti BPM server hold.

Every check sends ordinary, harmless requests and expects the server to refuse what it must
refuse. Run it against a test installation, never against production data: it creates users,
deployments and process instances (all named ``smoke-*``) and removes them again.

Usage:
  python security_smoke.py --url http://localhost:8080 --admin admin --admin-password-env ADMIN_PW \
      [--expect-production] [--setup-open] [--multipart-limit-bytes 1048576]

Exit code 0 when every check passed, 1 otherwise. Only the Python standard library is used.
"""
import argparse
import base64
import json
import os
import sys
import urllib.error
import urllib.request
import uuid

RESULTS = []


def record(name, ok, detail=""):
    RESULTS.append((name, ok, detail))
    print(f"[{'PASS' if ok else 'FAIL'}] {name}" + (f" - {detail}" if detail else ""))


class Client:
    def __init__(self, base, user=None, password=None):
        self.base = base.rstrip("/")
        self.auth = None
        if user is not None:
            self.auth = "Basic " + base64.b64encode(f"{user}:{password}".encode()).decode()

    def call(self, method, path, body=None, headers=None, raw=None, content_type=None):
        data = None
        hdrs = dict(headers or {})
        if self.auth:
            hdrs["Authorization"] = self.auth
        if raw is not None:
            data = raw
            hdrs["Content-Type"] = content_type
        elif body is not None:
            data = json.dumps(body).encode()
            hdrs["Content-Type"] = "application/json"
        req = urllib.request.Request(self.base + path, data=data, method=method, headers=hdrs)
        try:
            with urllib.request.urlopen(req, timeout=60) as res:
                text = res.read().decode("utf-8", "replace")
                return res.status, text, dict(res.headers)
        except urllib.error.HTTPError as e:
            return e.code, e.read().decode("utf-8", "replace"), dict(e.headers)
        except urllib.error.URLError as e:
            return 0, str(e), {}


def multipart(fields):
    boundary = "smoke-" + uuid.uuid4().hex
    out = b""
    for name, (filename, content, ctype) in fields.items():
        out += f"--{boundary}\r\n".encode()
        if filename:
            out += f'Content-Disposition: form-data; name="{name}"; filename="{filename}"\r\n'.encode()
        else:
            out += f'Content-Disposition: form-data; name="{name}"\r\n'.encode()
        out += f"Content-Type: {ctype}\r\n\r\n".encode() + content + b"\r\n"
    out += f"--{boundary}--\r\n".encode()
    return out, f"multipart/form-data; boundary={boundary}"


FOUR_EYES_BPMN = b"""<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:camunda="http://camunda.org/schema/1.0/bpmn"
  targetNamespace="http://fasti.dev/smoke">
  <process id="smokeFourEyes" isExecutable="true" camunda:historyTimeToLive="1">
    <startEvent id="start"/>
    <sequenceFlow id="f1" sourceRef="start" targetRef="draft"/>
    <userTask id="draft" name="Draft" camunda:candidateUsers="smokealice,smokebob"/>
    <sequenceFlow id="f2" sourceRef="draft" targetRef="approve"/>
    <userTask id="approve" name="Approve" camunda:candidateUsers="smokealice,smokebob">
      <extensionElements><camunda:properties><camunda:property name="fasti:fourEyes" value="draft"/></camunda:properties></extensionElements>
    </userTask>
    <sequenceFlow id="f3" sourceRef="approve" targetRef="end"/>
    <endEvent id="end"/>
  </process>
</definitions>
"""

PLAIN_BPMN = FOUR_EYES_BPMN.replace(b"smokeFourEyes", b"smokePlain")


def check_unauthenticated(base):
    anon = Client(base)
    for path in ["/engine-rest/user", "/engine-rest/process-definition", "/engine-rest/deployment",
                 "/engine-rest/task", "/engine-rest/history/process-instance", "/engine-rest/authorization",
                 "/engine-rest/analytics/process-definition/key/smokePlain/instances",
                 "/engine-rest/task/workload", "/fasti/api/engine/engine/default/user", "/actuator/env"]:
        status, _, _ = anon.call("GET", path)
        record(f"no login: GET {path} refused", status in (401, 403, 404), f"status {status}")
    status, _, _ = anon.call("GET", "/actuator/health")
    record("no login: health check answers", status == 200, f"status {status}")


def check_setup(base, setup_open):
    anon = Client(base)
    body = {"profile": {"id": "smokeintruder"}, "credentials": {"password": "Smoke-Passw0rd-1!"}}
    status, _, _ = anon.call("POST", "/fasti/api/admin/setup/default/user/create", body)
    record("initial setup without token refused", status in (401, 403, 404),
           f"status {status}" + (" (setup page open on this server)" if setup_open else ""))
    status, _, _ = anon.call("POST", "/fasti/api/admin/setup/default/user/create", body,
                             headers={"X-Fasti-Setup-Token": "guessed-token-0000000000"})
    record("initial setup with a wrong token refused", status in (401, 403, 404), f"status {status}")


def check_deploy_permission(admin, limited):
    raw, ctype = multipart({"deployment-name": (None, b"smoke-plain", "text/plain"),
                            "data": ("smoke.bpmn", PLAIN_BPMN, "application/xml")})
    status, _, _ = limited.call("POST", "/engine-rest/deployment/create", raw=raw, content_type=ctype)
    record("deployment without CREATE on DEPLOYMENT refused", status == 403, f"status {status}")
    status, text, _ = admin.call("POST", "/engine-rest/deployment/create", raw=raw, content_type=ctype)
    record("deployment by an administrator works", status == 200, f"status {status}")
    return json.loads(text)["id"] if status == 200 else None


def check_expressions(admin, limited):
    status, _, _ = limited.call("GET", "/engine-rest/task?assigneeExpression=" + urllib.request.quote("${execution}"))
    record("expression in an ad-hoc query refused", status == 400, f"status {status}")
    for expr in ["${execution}", "${identityService}", "${currentUser().getClass()}", "${dateTime()[0]}"]:
        status, text, _ = admin.call("POST", "/engine-rest/filter/create", {
            "resourceType": "Task", "name": "smoke-filter", "query": {"assigneeExpression": expr}})
        ok = status == 400
        if status == 200:
            admin.call("DELETE", f"/engine-rest/filter/{json.loads(text)['id']}")
        record(f"filter expression {expr} refused", ok, f"status {status}")


def check_values_not_evaluated(admin):
    status, text, _ = admin.call("POST", "/engine-rest/process-definition/key/smokePlain/start", {
        "businessKey": "${1+1}", "variables": {"note": {"value": "#{1+1}", "type": "String"}}})
    if status != 200:
        record("expression text in a business key is stored as text", False, f"start status {status}")
        return
    pi = json.loads(text)["id"]
    _, text, _ = admin.call("GET", f"/engine-rest/process-instance/{pi}")
    _, var, _ = admin.call("GET", f"/engine-rest/process-instance/{pi}/variables/note")
    record("expression text in a business key is stored as text", json.loads(text).get("businessKey") == "${1+1}")
    record("expression text in a variable is stored as text", json.loads(var).get("value") == "#{1+1}")
    admin.call("DELETE", f"/engine-rest/process-instance/{pi}")


def check_type_validation(admin):
    status, text, _ = admin.call("POST", "/engine-rest/process-definition/key/smokePlain/start", {})
    pi = json.loads(text)["id"]
    status, _, _ = admin.call("PUT", f"/engine-rest/process-instance/{pi}/variables/smokeUri", {
        "type": "Object", "value": "\"https://fasti.dev\"",
        "valueInfo": {"objectTypeName": "java.net.URI", "serializationDataFormat": "application/json"}})
    read_status, body, _ = admin.call("GET", f"/engine-rest/process-instance/{pi}/variables/smokeUri")
    refused = status >= 400 or read_status >= 400 or "not allowed" in body.lower() or "validat" in body.lower()
    record("JSON variable of a type outside the allow-list is not deserialized", refused,
           f"write {status}, read {read_status}")
    status, _, _ = admin.call("PUT", f"/engine-rest/process-instance/{pi}/variables/smokeJava", {
        "type": "Object", "value": "rO0ABXQABHRlc3Q=",
        "valueInfo": {"objectTypeName": "java.lang.String", "serializationDataFormat": "application/x-java-serialized-object"}})
    record("Java-serialized variable refused", status == 400, f"status {status}")
    admin.call("DELETE", f"/engine-rest/process-instance/{pi}")


def check_multipart_limit(admin, limit):
    if not limit:
        record("multipart size limit (skipped: pass --multipart-limit-bytes)", True)
        return
    raw, ctype = multipart({"deployment-name": (None, b"smoke-big", "text/plain"),
                            "data": ("big.txt", b"x" * (limit + 1024), "text/plain")})
    status, _, _ = admin.call("POST", "/engine-rest/deployment/create", raw=raw, content_type=ctype)
    record("multipart request over the limit answered 413", status == 413, f"status {status}")


def check_four_eyes(admin, alice_pw, bob_pw, base):
    raw, ctype = multipart({"deployment-name": (None, b"smoke-four-eyes", "text/plain"),
                            "data": ("four-eyes.bpmn", FOUR_EYES_BPMN, "application/xml")})
    status, text, _ = admin.call("POST", "/engine-rest/deployment/create", raw=raw, content_type=ctype)
    if status != 200:
        record("four-eyes model deployed", False, f"status {status}")
        return None
    deployment = json.loads(text)["id"]
    alice = Client(base, "smokealice", alice_pw)
    status, text, _ = alice.call("POST", "/engine-rest/process-definition/key/smokeFourEyes/start", {
        "startInstructions": [{"type": "startBeforeActivity", "activityId": "approve"}]})
    if status != 200:
        record("four-eyes: start at the guarded task", False, f"status {status}")
        return deployment
    pi = json.loads(text)["id"]
    _, tasks, _ = admin.call("GET", f"/engine-rest/task?processInstanceId={pi}")
    task = json.loads(tasks)[0]["id"]
    alice.call("POST", f"/engine-rest/task/{task}/claim", {"userId": "smokealice"})
    status, _, _ = alice.call("POST", f"/engine-rest/task/{task}/complete", {})
    record("four-eyes: who starts at the guarded task cannot complete it", status == 403, f"status {status}")
    admin.call("POST", f"/engine-rest/task/{task}/unclaim")
    bob = Client(base, "smokebob", bob_pw)
    bob.call("POST", f"/engine-rest/task/{task}/claim", {"userId": "smokebob"})
    status, _, _ = bob.call("POST", f"/engine-rest/task/{task}/complete", {})
    record("four-eyes: another person can complete it", status == 204, f"status {status}")
    return deployment


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--url", required=True)
    parser.add_argument("--admin", required=True)
    parser.add_argument("--admin-password-env", required=True, help="environment variable that holds the password")
    parser.add_argument("--setup-open", action="store_true", help="the initial setup page is switched on")
    parser.add_argument("--multipart-limit-bytes", type=int, default=0)
    parser.add_argument("--expect-production", action="store_true")
    args = parser.parse_args()

    base = args.url
    admin = Client(base, args.admin, os.environ[args.admin_password_env])
    status, _, _ = admin.call("GET", "/engine-rest/user/" + args.admin + "/profile")
    if status != 200:
        print(f"admin login failed ({status}); stopping")
        return 1

    pw = "Smoke-" + uuid.uuid4().hex[:12] + "-A1!"
    users = ["smokelimited", "smokealice", "smokebob"]
    for user in users:
        admin.call("POST", "/engine-rest/user/create", {"profile": {"id": user, "firstName": "Smoke", "lastName": "Test",
                                                                    "email": f"{user}@example.invalid"},
                                                        "credentials": {"password": pw}})
    grants = []
    for user, resource, perms in [("smokelimited", 6, ["READ"]), ("smokealice", 6, ["ALL"]), ("smokealice", 8, ["CREATE"]),
                                  ("smokebob", 6, ["ALL"]), ("smokealice", 7, ["ALL"]), ("smokebob", 7, ["ALL"])]:
        status, text, _ = admin.call("POST", "/engine-rest/authorization/create", {
            "type": 1, "permissions": perms, "userId": user, "resourceType": resource, "resourceId": "*"})
        if status == 200:
            grants.append(json.loads(text)["id"])
    limited = Client(base, "smokelimited", pw)
    deployments = []
    try:
        check_unauthenticated(base)
        check_setup(base, args.setup_open)
        deployments.append(check_deploy_permission(admin, limited))
        check_expressions(admin, limited)
        check_values_not_evaluated(admin)
        check_type_validation(admin)
        check_multipart_limit(admin, args.multipart_limit_bytes)
        deployments.append(check_four_eyes(admin, pw, pw, base))
    finally:
        for deployment in deployments:
            if deployment:
                admin.call("DELETE", f"/engine-rest/deployment/{deployment}?cascade=true")
        for grant in grants:
            admin.call("DELETE", f"/engine-rest/authorization/{grant}")
        for user in users:
            admin.call("DELETE", f"/engine-rest/user/{user}")

    failed = [r for r in RESULTS if not r[1]]
    print(f"\n{len(RESULTS) - len(failed)} passed, {len(failed)} failed")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
