# Security smoke test

`security_smoke.py` checks that some security controls of a running Fasti BPM server hold:
requests without a login are refused, the initial setup cannot be used without its token, deployments need the
permission, query expressions stay on the allow-list, values like `${1+1}` are stored as text,
deserialized types are validated, Java serialization is refused, multipart requests are limited
and the four-eyes rule cannot be skipped by starting a process at the approval step.

The checks send ordinary, harmless requests; most of them expect a refusal. Run it against a **test**
installation: it creates users, deployments and process instances named `smoke*` and removes
them afterwards. Only the Python standard library is needed.

```bash
export ADMIN_PW=...   # password of an administrator of the test installation
python qa/security-smoke/security_smoke.py --url http://localhost:8080 \
  --admin admin --admin-password-env ADMIN_PW
# with FASTI_REST_MULTIPART_MAX_SIZE=1MB on the server:
python qa/security-smoke/security_smoke.py ... --multipart-limit-bytes 1048576
```

Exit code 0 when every check passed. The background of each check is in
[docs/security/pentest-1.3.md](../../docs/security/pentest-1.3.md).
