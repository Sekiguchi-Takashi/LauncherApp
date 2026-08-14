import base64
import json
import os
import subprocess
import sys
import urllib.error
import urllib.request

REPO = "Sekiguchi-Takashi/LauncherApp"
KEYSTORE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "appathy.keystore")

try:
    from nacl import encoding, public
except ImportError:
    sys.stderr.write("PyNaCl not installed. Run: pkg install -y python libsodium\n")
    sys.stderr.write("then: SODIUM_INSTALL=system pip install pynacl\n")
    sys.exit(1)


def token():
    out = subprocess.run(
        ["git", "config", "--global", "github.token"],
        capture_output=True, text=True
    )
    value = out.stdout.strip()
    if not value:
        sys.stderr.write("github.token is not set in git config\n")
        sys.exit(1)
    return value


def api(path, tok, method="GET", body=None):
    url = "https://api.github.com/repos/" + REPO + path
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Authorization", "token " + tok)
    req.add_header("Accept", "application/vnd.github+json")
    if data is not None:
        req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req) as res:
            raw = res.read()
            return json.loads(raw) if raw else {}
    except urllib.error.HTTPError as e:
        sys.stderr.write("HTTP " + str(e.code) + " on " + path + "\n")
        sys.stderr.write(e.read().decode() + "\n")
        sys.exit(1)


def seal(pubkey_b64, value):
    pk = public.PublicKey(pubkey_b64.encode(), encoding.Base64Encoder())
    sealed = public.SealedBox(pk).encrypt(value.encode())
    return base64.b64encode(sealed).decode()


def put_secret(tok, key_info, name, value):
    api(
        "/actions/secrets/" + name,
        tok,
        method="PUT",
        body={
            "encrypted_value": seal(key_info["key"], value),
            "key_id": key_info["key_id"],
        },
    )
    sys.stdout.write("registered: " + name + "\n")


def main():
    if len(sys.argv) < 2:
        sys.stderr.write("usage: python ci/set_secrets.py <keystore-password>\n")
        sys.exit(1)
    password = sys.argv[1]

    if not os.path.exists(KEYSTORE):
        sys.stderr.write("keystore not found: " + KEYSTORE + "\n")
        sys.exit(1)

    with open(KEYSTORE, "rb") as f:
        keystore_b64 = base64.b64encode(f.read()).decode()

    tok = token()
    key_info = api("/actions/secrets/public-key", tok)

    put_secret(tok, key_info, "KEYSTORE_B64", keystore_b64)
    put_secret(tok, key_info, "KEYSTORE_PASSWORD", password)

    names = [s["name"] for s in api("/actions/secrets", tok).get("secrets", [])]
    sys.stdout.write("secrets now: " + ", ".join(sorted(names)) + "\n")


if __name__ == "__main__":
    main()
