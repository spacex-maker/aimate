#!/usr/bin/env python3
"""Deploy OpenForgeX AIMate (chat) to 139.155.130.167 (Chengdu).

Domains:
  chat.aimatex.com        → SPA  (/home/ubuntu/web/chat)
  chat-admin.aimatex.com  → Admin SPA (same dist, /home/ubuntu/web/chat-admin)
  chat-api.aimatex.com    → API  (127.0.0.1:9299)

Fresh local MySQL DB `aimate` (schema + seed). Docker per-user VM disabled.
"""
from __future__ import annotations

import base64
import os
import sys
import time
from pathlib import Path

import paramiko

HOST = "139.155.130.167"
WEB_DOMAIN = "chat.aimatex.com"
ADMIN_DOMAIN = "chat-admin.aimatex.com"
API_DOMAIN = "chat-api.aimatex.com"
APP_PORT = 9299
DNS_DOMAIN = "aimatex.com"
DNS_RECORDS = ["chat", "chat-api", "chat-admin"]
DNS_VALUE = HOST

ROOT = Path(__file__).resolve().parents[1]
JAR = ROOT / "target" / "aimate-0.1.0-SNAPSHOT.jar"
WEB_DIST = ROOT / "frontend" / "dist"
DB_DIR = ROOT / "src" / "main" / "resources" / "db"
NAS_SECRETS = Path(r"F:\dev\proj2\aimatex-nas\secrets")
DEPLOY_ENV_CANDIDATES = (
    Path.home() / ".aimatex-chat-deploy.env",
    Path.home() / ".aimatex-music-deploy.env",
    ROOT / "secrets" / "deploy.env",
)

REMOTE_JAVA = "/home/ubuntu/java/chat"
REMOTE_WEB = "/home/ubuntu/web/chat"
REMOTE_ADMIN_WEB = "/home/ubuntu/web/chat-admin"
REMOTE_JAR = f"{REMOTE_JAVA}/aimate.jar"
REMOTE_ENV = f"{REMOTE_JAVA}/aimate.env"
REMOTE_JDK = "/home/ubuntu/jdk25"
REMOTE_JAVA_BIN = f"{REMOTE_JDK}/bin/java"

ENV_CONTENT = """SPRING_PROFILES_ACTIVE=prod
SERVER_PORT={port}
DB_HOST=127.0.0.1
DB_PORT=3306
DB_NAME=aimate
DB_USERNAME={db_user}
DB_PASSWORD={db_password}
JWT_SECRET={jwt_secret}
SPRING_JPA_HIBERNATE_DDL_AUTO=update
AGENT_SCRIPT_DOCKER_ENABLED=false
PRIMARY_LLM_BASE_URL={primary_llm_base}
PRIMARY_LLM_API_KEY={primary_llm_key}
PRIMARY_LLM_MODEL={primary_llm_model}
FALLBACK_LLM_BASE_URL={fallback_llm_base}
FALLBACK_LLM_API_KEY={fallback_llm_key}
FALLBACK_LLM_MODEL={fallback_llm_model}
EMBEDDING_BASE_URL={embedding_base}
EMBEDDING_API_KEY={embedding_key}
EMBEDDING_MODEL={embedding_model}
EMBEDDING_DIMENSIONS=1024
MILVUS_HOST={milvus_host}
MILVUS_PORT={milvus_port}
MILVUS_VECTOR_DIMENSIONS=1024
AGENT_MILVUS_ENABLED={milvus_enabled}
AIMATEX_OIDC_ENABLED={oidc_enabled}
AIMATEX_OIDC_CLIENT_ID={oidc_client_id}
AIMATEX_OIDC_ISSUER={oidc_issuer}
AIMATEX_OIDC_AUTHORIZE_URL={oidc_authorize_url}
AIMATEX_OIDC_TOKEN_URL={oidc_token_url}
AIMATEX_OIDC_USERINFO_URL={oidc_userinfo_url}
AIMATEX_OIDC_JWKS_URL={oidc_jwks_url}
AIMATEX_OIDC_REDIRECT_URI={oidc_redirect_uri}
AIMATEX_OIDC_REDIRECT_URIS={oidc_redirect_uris}
AIMATEX_OIDC_SCOPES={oidc_scopes}
AIMATEX_CHAT_COS_ENABLED={cos_enabled}
AIMATEX_CHAT_COS_BUCKET={cos_bucket}
AIMATEX_CHAT_COS_REGION={cos_region}
AIMATEX_CHAT_COS_PREFIX={cos_prefix}
TENCENTCLOUD_SECRET_ID={tc_sid}
TENCENTCLOUD_SECRET_KEY={tc_skey}
AIMATE_ADMIN_BOOTSTRAP_EMAIL={admin_email}
AIMATE_ADMIN_BOOTSTRAP_PASSWORD={admin_password}
AIMATE_ADMIN_BOOTSTRAP_DISPLAY_NAME={admin_display_name}
AIMATE_SOUL_MEMORY_ENABLED={soul_memory_enabled}
AIMATE_SOUL_API_BASE={soul_api_base}
AIMATE_SOUL_PLATFORM_ENSURE_URL={soul_platform_ensure_url}
AIMATE_SOUL_PLATFORM_BASE_URL={soul_platform_base_url}
AIMATE_SOUL_PLATFORM_KEY={soul_platform_key}
AIMATE_SOUL_PLATFORM_CODE={soul_platform_code}
"""

SYSTEMD_UNIT = f"""[Unit]
Description=AIMATEX Chat (OpenForgeX AIMate) API
After=network.target mysql.service

[Service]
Type=simple
User=ubuntu
WorkingDirectory={REMOTE_JAVA}
EnvironmentFile={REMOTE_ENV}
ExecStart={REMOTE_JAVA_BIN} -Xms64m -Xmx256m -jar {REMOTE_JAR}
SuccessExitStatus=143
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
"""

NGINX_WEB = f"""server {{
    listen 80;
    server_name {WEB_DOMAIN};

    root {REMOTE_WEB};
    index index.html;
    client_max_body_size 20m;

    location ^~ /.well-known/acme-challenge/ {{
        default_type "text/plain";
        root /var/www/certbot;
    }}

    location / {{
        try_files $uri $uri/ /index.html;
    }}
}}
"""

NGINX_ADMIN = f"""server {{
    listen 80;
    server_name {ADMIN_DOMAIN};

    root {REMOTE_ADMIN_WEB};
    index index.html;
    client_max_body_size 20m;

    location ^~ /.well-known/acme-challenge/ {{
        default_type "text/plain";
        root /var/www/certbot;
    }}

    location = / {{
        return 302 /login;
    }}

    location / {{
        try_files $uri $uri/ /index.html;
    }}
}}
"""

NGINX_API = f"""server {{
    listen 80;
    server_name {API_DOMAIN};
    client_max_body_size 20m;

    location ^~ /.well-known/acme-challenge/ {{
        default_type "text/plain";
        root /var/www/certbot;
    }}

    location / {{
        proxy_pass http://127.0.0.1:{APP_PORT};
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_read_timeout 600s;
        proxy_send_timeout 600s;
    }}
}}
"""


def load_env_file(path: Path) -> dict[str, str]:
    if not path.exists():
        return {}
    out: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, v = line.split("=", 1)
        out[k.strip()] = v.strip().strip("'").strip('"')
    return out


# NAS secrets are reused for SSH/Tencent only — never inherit NAS OIDC/client ids.
_NAS_OIDC_KEYS = (
    "AIMATEX_OIDC_CLIENT_ID",
    "AIMATEX_CLIENT_ID",
    "AIMATEX_OIDC_REDIRECT_URI",
    "AIMATEX_OIDC_REDIRECT_URIS",
    "AIMATEX_OIDC_NAS_BENEFIT_KEY",
)


def load_merged() -> dict[str, str]:
    data: dict[str, str] = {}
    data.update(load_env_file(NAS_SECRETS / "aimatex-nas.env"))
    data.update(load_env_file(NAS_SECRETS / "ops.env"))
    for k in _NAS_OIDC_KEYS:
        data.pop(k, None)
    for path in DEPLOY_ENV_CANDIDATES:
        data.update(load_env_file(path))
    # local.yml-adjacent secrets file if present
    data.update(load_env_file(ROOT / "secrets" / "aimate.env"))
    for k, v in os.environ.items():
        if v:
            data[k] = v
    return data


def env_get(data: dict[str, str], *keys: str, default: str = "") -> str:
    for k in keys:
        v = (data.get(k) or "").strip()
        if v:
            return v
    return default


def run(ssh: paramiko.SSHClient, cmd: str, check: bool = True, timeout: int = 300) -> tuple[int, str]:
    print(f"\n>>> {cmd[:200]}")
    _, stdout, _ = ssh.exec_command(cmd, get_pty=True, timeout=timeout)
    out = stdout.read().decode("utf-8", errors="replace")
    code = stdout.channel.recv_exit_status()
    print(out[-4000:] if len(out) > 4000 else out)
    if check and code != 0:
        raise RuntimeError(f"cmd failed ({code}): {cmd}")
    return code, out


def sftp_put_dir(sftp: paramiko.SFTPClient, local: Path, remote: str) -> None:
    try:
        sftp.stat(remote)
    except FileNotFoundError:
        sftp.mkdir(remote)
    for path in local.rglob("*"):
        rel = path.relative_to(local).as_posix()
        rpath = f"{remote}/{rel}"
        if path.is_dir():
            try:
                sftp.stat(rpath)
            except FileNotFoundError:
                sftp.mkdir(rpath)
        else:
            print(f"put {rel}")
            sftp.put(str(path), rpath)


def ensure_dns(tc_sid: str, tc_skey: str) -> None:
    from tencentcloud.common import credential
    from tencentcloud.common.profile.client_profile import ClientProfile
    from tencentcloud.common.profile.http_profile import HttpProfile
    from tencentcloud.dnspod.v20210323 import dnspod_client, models

    cred = credential.Credential(tc_sid, tc_skey)
    hp = HttpProfile(endpoint="dnspod.tencentcloudapi.com")
    client = dnspod_client.DnspodClient(cred, "", ClientProfile(httpProfile=hp))

    req = models.DescribeRecordListRequest()
    req.Domain = DNS_DOMAIN
    req.Limit = 3000
    try:
        resp = client.DescribeRecordList(req)
        existing = {r.Name: r for r in (resp.RecordList or [])}
    except Exception as e:
        print("DescribeRecordList failed, try Create anyway:", e)
        existing = {}

    for name in DNS_RECORDS:
        if name in existing:
            rec = existing[name]
            if getattr(rec, "Value", None) == DNS_VALUE and getattr(rec, "Type", None) == "A":
                print(f"DNS OK {name}.{DNS_DOMAIN} -> {DNS_VALUE}")
                continue
            ureq = models.ModifyRecordRequest()
            ureq.Domain = DNS_DOMAIN
            ureq.RecordId = rec.RecordId
            ureq.RecordType = "A"
            ureq.RecordLine = "默认"
            ureq.Value = DNS_VALUE
            ureq.SubDomain = name
            ureq.TTL = 600
            client.ModifyRecord(ureq)
            print(f"DNS UPDATED {name}.{DNS_DOMAIN} -> {DNS_VALUE}")
        else:
            creq = models.CreateRecordRequest()
            creq.Domain = DNS_DOMAIN
            creq.SubDomain = name
            creq.RecordType = "A"
            creq.RecordLine = "默认"
            creq.Value = DNS_VALUE
            creq.TTL = 600
            client.CreateRecord(creq)
            print(f"DNS CREATED {name}.{DNS_DOMAIN} -> {DNS_VALUE}")


def ensure_jdk25(ssh: paramiko.SSHClient, sftp: paramiko.SFTPClient) -> None:
    code, out = run(
        ssh, f"test -x {REMOTE_JAVA_BIN} && {REMOTE_JAVA_BIN} -version 2>&1 | head -1", check=False
    )
    if code == 0 and "25" in out:
        print("JDK 25 already present")
        return
    print("Installing OpenJDK 25 (Huawei mirror) ...")
    local_sh = ROOT / "scripts" / "_install_jdk25_remote.sh"
    remote_sh = f"{REMOTE_JAVA}/_install_jdk25_remote.sh"
    sftp.put(str(local_sh), remote_sh)
    run(ssh, f"chmod +x {remote_sh} && bash {remote_sh}", timeout=600)
    run(ssh, f"{REMOTE_JAVA_BIN} -version")
    run(ssh, f"rm -f {remote_sh}", check=False)


def apply_schema(ssh: paramiko.SSHClient, sftp: paramiko.SFTPClient, db_user: str, db_password: str) -> None:
    init_sql = f"""CREATE DATABASE IF NOT EXISTS aimate CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '{db_user}'@'localhost' IDENTIFIED BY '{db_password}';
CREATE USER IF NOT EXISTS '{db_user}'@'127.0.0.1' IDENTIFIED BY '{db_password}';
ALTER USER '{db_user}'@'localhost' IDENTIFIED BY '{db_password}';
ALTER USER '{db_user}'@'127.0.0.1' IDENTIFIED BY '{db_password}';
GRANT ALL PRIVILEGES ON aimate.* TO '{db_user}'@'localhost';
GRANT ALL PRIVILEGES ON aimate.* TO '{db_user}'@'127.0.0.1';
FLUSH PRIVILEGES;
"""
    remote_init = f"{REMOTE_JAVA}/init-db.sql"
    with sftp.file(remote_init, "w") as f:
        f.write(init_sql)
    run(ssh, f"sudo mysql < {remote_init}")
    run(ssh, f"rm -f {remote_init}", check=False)

    # 仅在显式 DEPLOY_FRESH_DB=1 时清空库。默认保留已有数据。
    fresh = os.environ.get("DEPLOY_FRESH_DB", "0").strip().lower() in ("1", "true", "yes")
    if fresh:
        run(ssh, "sudo mysql -e \"DROP DATABASE IF EXISTS aimate; CREATE DATABASE aimate CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;\"")
        run(
            ssh,
            f"sudo mysql -e \"GRANT ALL PRIVILEGES ON aimate.* TO '{db_user}'@'localhost'; "
            f"GRANT ALL PRIVILEGES ON aimate.* TO '{db_user}'@'127.0.0.1'; FLUSH PRIVILEGES;\"",
        )

    remote_db = f"{REMOTE_JAVA}/db"
    run(ssh, f"rm -rf {remote_db} && mkdir -p {remote_db}")
    sftp_put_dir(sftp, DB_DIR, remote_db)

    # schema.sql contains CREATE DATABASE + USE — safe to run as root
    run(ssh, f"sudo mysql < {remote_db}/schema.sql")

    migrations = sorted(DB_DIR.glob("migration_*.sql"))
    for m in migrations:
        run(ssh, f"sudo mysql aimate < {remote_db}/{m.name}", check=False)

    for seed in ("seed_builtin_tools.sql", "seed_system_models.sql"):
        if (DB_DIR / seed).exists():
            run(ssh, f"sudo mysql aimate < {remote_db}/{seed}", check=False)


def main() -> int:
    data = load_merged()
    user = env_get(data, "DEPLOY_SSH_USER", default="ubuntu")
    password = env_get(data, "DEPLOY_SSH_PASSWORD")
    if not password:
        print("缺少 DEPLOY_SSH_PASSWORD（可用 ~/.aimatex-music-deploy.env）", file=sys.stderr)
        return 1

    db_user = env_get(data, "DB_USERNAME", "DEPLOY_DB_USER", default="aimate")
    db_password = env_get(data, "DB_PASSWORD", "DEPLOY_DB_PASSWORD")
    jwt_secret = env_get(data, "JWT_SECRET", "DEPLOY_JWT_SECRET")
    if not db_password or not jwt_secret:
        print("缺少 DB_PASSWORD/DEPLOY_DB_PASSWORD 或 JWT_SECRET/DEPLOY_JWT_SECRET", file=sys.stderr)
        return 1

    primary_llm_key = env_get(data, "PRIMARY_LLM_API_KEY")
    if not primary_llm_key:
        # fallback to known local deepseek key source for bootstrap (prefer secrets)
        print("警告: PRIMARY_LLM_API_KEY 未设置，将尝试 FALLBACK / 占位", file=sys.stderr)
    fallback_llm_key = env_get(data, "FALLBACK_LLM_API_KEY", "PRIMARY_LLM_API_KEY")

    tc_sid = env_get(data, "TENCENTCLOUD_SECRET_ID")
    tc_skey = env_get(data, "TENCENTCLOUD_SECRET_KEY")
    if not tc_sid or not tc_skey:
        print("缺少 TENCENTCLOUD_SECRET_ID/KEY（用于 DNSPod）", file=sys.stderr)
        return 1

    frontend_only = os.environ.get("DEPLOY_FRONTEND_ONLY", "").strip().lower() in ("1", "true", "yes")
    skip_dns = os.environ.get("DEPLOY_SKIP_DNS", "").strip().lower() in ("1", "true", "yes")

    if not frontend_only and not JAR.exists():
        print(f"Missing fat JAR: {JAR}", file=sys.stderr)
        return 1
    if not WEB_DIST.exists():
        print(f"Missing web dist: {WEB_DIST}", file=sys.stderr)
        return 1
    if not (DB_DIR / "schema.sql").exists():
        print(f"Missing schema: {DB_DIR / 'schema.sql'}", file=sys.stderr)
        return 1

    if not skip_dns:
        print("Ensuring DNS ...")
        ensure_dns(tc_sid, tc_skey)

    env_body = ENV_CONTENT.format(
        port=APP_PORT,
        db_user=db_user,
        db_password=db_password,
        jwt_secret=jwt_secret,
        primary_llm_base=env_get(data, "PRIMARY_LLM_BASE_URL", default="https://api.deepseek.com/v1"),
        primary_llm_key=primary_llm_key or fallback_llm_key or "sk-placeholder",
        primary_llm_model=env_get(data, "PRIMARY_LLM_MODEL", default="deepseek-reasoner"),
        fallback_llm_base=env_get(data, "FALLBACK_LLM_BASE_URL", default="https://api.deepseek.com/v1"),
        fallback_llm_key=fallback_llm_key or primary_llm_key or "sk-placeholder",
        fallback_llm_model=env_get(data, "FALLBACK_LLM_MODEL", default="deepseek-chat"),
        embedding_base=env_get(data, "EMBEDDING_BASE_URL", default="http://175.27.255.29:11434/v1"),
        embedding_key=env_get(data, "EMBEDDING_API_KEY", default="ollama"),
        embedding_model=env_get(data, "EMBEDDING_MODEL", default="bge-m3"),
        milvus_host=env_get(data, "MILVUS_HOST", default="175.27.255.29"),
        milvus_port=env_get(data, "MILVUS_PORT", default="19530"),
        milvus_enabled=env_get(data, "AGENT_MILVUS_ENABLED", default="false"),
        soul_memory_enabled=env_get(data, "AIMATE_SOUL_MEMORY_ENABLED", default="true"),
        soul_api_base=env_get(
            data, "AIMATE_SOUL_API_BASE", default="https://soul-api.aimatex.com/api/v1/soul"
        ),
        soul_platform_ensure_url=env_get(
            data,
            "AIMATE_SOUL_PLATFORM_ENSURE_URL",
            default="https://soul-api.aimatex.com/api/v1/platform/souls/ensure",
        ),
        soul_platform_base_url=env_get(
            data,
            "AIMATE_SOUL_PLATFORM_BASE_URL",
            default="https://soul-api.aimatex.com/api/v1/platform/souls",
        ),
        soul_platform_key=env_get(
            data, "AIMATE_SOUL_PLATFORM_KEY", "SOUL_PLATFORM_API_KEY", default=""
        ),
        soul_platform_code=env_get(data, "AIMATE_SOUL_PLATFORM_CODE", default="aimatex-chat"),
        oidc_enabled=env_get(data, "AIMATEX_OIDC_ENABLED", default="true"),
        oidc_client_id=env_get(data, "AIMATEX_OIDC_CLIENT_ID", "AIMATEX_CLIENT_ID", default="aimatex-chat"),
        oidc_issuer=env_get(data, "AIMATEX_OIDC_ISSUER", default="https://account-api.aimatex.com"),
        oidc_authorize_url=env_get(
            data, "AIMATEX_OIDC_AUTHORIZE_URL", default="https://account.aimatex.com/oauth/authorize"
        ),
        oidc_token_url=env_get(
            data, "AIMATEX_OIDC_TOKEN_URL", default="https://account-api.aimatex.com/oauth/token"
        ),
        oidc_userinfo_url=env_get(
            data, "AIMATEX_OIDC_USERINFO_URL", default="https://account-api.aimatex.com/oauth/userinfo"
        ),
        oidc_jwks_url=env_get(
            data, "AIMATEX_OIDC_JWKS_URL", default="https://account-api.aimatex.com/oauth/jwks"
        ),
        oidc_redirect_uri=env_get(
            data, "AIMATEX_OIDC_REDIRECT_URI", default="https://chat.aimatex.com/oauth/callback"
        ),
        oidc_redirect_uris=env_get(
            data,
            "AIMATEX_OIDC_REDIRECT_URIS",
            default=(
                "https://chat.aimatex.com/oauth/callback,"
                "http://127.0.0.1:5173/oauth/callback,"
                "http://localhost:5173/oauth/callback"
            ),
        ),
        oidc_scopes=env_get(data, "AIMATEX_OIDC_SCOPES", default="openid profile email membership"),
        cos_enabled=env_get(data, "AIMATEX_CHAT_COS_ENABLED", default="true"),
        cos_bucket=env_get(data, "AIMATEX_CHAT_COS_BUCKET", default="aimatex-chat-1258150206"),
        cos_region=env_get(data, "AIMATEX_CHAT_COS_REGION", default="ap-nanjing"),
        cos_prefix=env_get(data, "AIMATEX_CHAT_COS_PREFIX", default="chat/"),
        tc_sid=tc_sid,
        tc_skey=tc_skey,
        admin_email=env_get(
            data,
            "AIMATE_ADMIN_BOOTSTRAP_EMAIL",
            "NAS_ADMIN_BOOTSTRAP_EMAIL",
            default="admin@aimatex.com",
        ),
        admin_password=env_get(
            data,
            "AIMATE_ADMIN_BOOTSTRAP_PASSWORD",
            "NAS_ADMIN_BOOTSTRAP_PASSWORD",
            default="",
        ),
        admin_display_name=env_get(
            data,
            "AIMATE_ADMIN_BOOTSTRAP_DISPLAY_NAME",
            default="超级管理员",
        ),
    )

    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    print(f"Connecting {user}@{HOST} ...")
    ssh.connect(
        HOST,
        username=user,
        password=password,
        timeout=30,
        allow_agent=False,
        look_for_keys=False,
    )

    if frontend_only:
        sftp = ssh.open_sftp()
        run(ssh, f"mkdir -p {REMOTE_WEB} {REMOTE_ADMIN_WEB}")
        run(ssh, f"rm -rf {REMOTE_WEB}/* {REMOTE_ADMIN_WEB}/*")
        sftp_put_dir(sftp, WEB_DIST, REMOTE_WEB)
        sftp_put_dir(sftp, WEB_DIST, REMOTE_ADMIN_WEB)
        sftp.close()
        run(ssh, f"chmod 755 /home/ubuntu /home/ubuntu/web {REMOTE_WEB} {REMOTE_ADMIN_WEB}")
        run(ssh, "sudo nginx -t && sudo systemctl reload nginx")
        ssh.close()
        print("\nFrontend-only deploy finished.")
        return 0

    run(
        ssh,
        "sudo apt-get update -qq && sudo DEBIAN_FRONTEND=noninteractive apt-get install -y -qq nginx certbot python3-certbot-nginx curl tar",
        timeout=600,
    )
    run(ssh, f"mkdir -p {REMOTE_JAVA} {REMOTE_WEB} {REMOTE_ADMIN_WEB}")
    run(ssh, "sudo mkdir -p /var/www/certbot/.well-known/acme-challenge && sudo chmod -R 755 /var/www/certbot")
    run(ssh, "sudo chmod 755 /home/ubuntu")

    sftp = ssh.open_sftp()
    ensure_jdk25(ssh, sftp)
    apply_schema(ssh, sftp, db_user, db_password)

    print(f"Uploading JAR -> {REMOTE_JAR}")
    sftp.put(str(JAR), REMOTE_JAR)

    force_env = os.environ.get("DEPLOY_FORCE_ENV", "1").strip().lower() in ("1", "true", "yes")
    env_exists = False
    try:
        sftp.stat(REMOTE_ENV)
        env_exists = True
    except FileNotFoundError:
        pass
    if env_exists and not force_env:
        print(f"Keep existing env: {REMOTE_ENV}")
        # 补齐 COS 相关键，避免 ForceEnv=0 时对话图片无法落桶
        cos_keys = {
            "AIMATEX_CHAT_COS_ENABLED": env_get(data, "AIMATEX_CHAT_COS_ENABLED", default="true"),
            "AIMATEX_CHAT_COS_BUCKET": env_get(data, "AIMATEX_CHAT_COS_BUCKET", default="aimatex-chat-1258150206"),
            "AIMATEX_CHAT_COS_REGION": env_get(data, "AIMATEX_CHAT_COS_REGION", default="ap-nanjing"),
            "AIMATEX_CHAT_COS_PREFIX": env_get(data, "AIMATEX_CHAT_COS_PREFIX", default="chat/"),
            "TENCENTCLOUD_SECRET_ID": tc_sid,
            "TENCENTCLOUD_SECRET_KEY": tc_skey,
        }
        with sftp.file(REMOTE_ENV, "r") as f:
            existing = f.read().decode("utf-8", "replace")
        lines = existing.splitlines()
        have = {ln.split("=", 1)[0].strip() for ln in lines if "=" in ln and not ln.strip().startswith("#")}
        appended = []
        for k, v in cos_keys.items():
            if k not in have and v:
                appended.append(f"{k}={v}")
        if appended:
            with sftp.file(REMOTE_ENV, "a") as f:
                f.write("\n" + "\n".join(appended) + "\n")
            print(f"Appended COS keys to {REMOTE_ENV}: {', '.join(k.split('=')[0] for k in appended)}")
    else:
        with sftp.file(REMOTE_ENV, "w") as f:
            f.write(env_body)
        print(f"Wrote {REMOTE_ENV}")

    print("Uploading frontend dist (user + admin) ...")
    run(ssh, f"rm -rf {REMOTE_WEB}/* {REMOTE_ADMIN_WEB}/*")
    sftp_put_dir(sftp, WEB_DIST, REMOTE_WEB)
    sftp_put_dir(sftp, WEB_DIST, REMOTE_ADMIN_WEB)
    sftp.close()
    run(ssh, f"chmod 600 {REMOTE_ENV}")
    run(ssh, f"chmod 755 /home/ubuntu /home/ubuntu/web {REMOTE_WEB} {REMOTE_ADMIN_WEB}")

    unit_b64 = base64.b64encode(SYSTEMD_UNIT.encode()).decode()
    run(
        ssh,
        f"echo {unit_b64} | base64 -d | sudo tee /etc/systemd/system/aimate-chat.service >/dev/null",
    )
    run(
        ssh,
        "sudo systemctl daemon-reload && sudo systemctl enable aimate-chat && sudo systemctl restart aimate-chat",
    )

    web_b64 = base64.b64encode(NGINX_WEB.encode()).decode()
    admin_b64 = base64.b64encode(NGINX_ADMIN.encode()).decode()
    api_b64 = base64.b64encode(NGINX_API.encode()).decode()
    run(
        ssh,
        f"echo {web_b64} | base64 -d | sudo tee /etc/nginx/sites-available/{WEB_DOMAIN} >/dev/null",
    )
    run(
        ssh,
        f"echo {admin_b64} | base64 -d | sudo tee /etc/nginx/sites-available/{ADMIN_DOMAIN} >/dev/null",
    )
    run(
        ssh,
        f"echo {api_b64} | base64 -d | sudo tee /etc/nginx/sites-available/{API_DOMAIN} >/dev/null",
    )
    run(
        ssh,
        f"sudo ln -sfn /etc/nginx/sites-available/{WEB_DOMAIN} /etc/nginx/sites-enabled/{WEB_DOMAIN} && "
        f"sudo ln -sfn /etc/nginx/sites-available/{ADMIN_DOMAIN} /etc/nginx/sites-enabled/{ADMIN_DOMAIN} && "
        f"sudo ln -sfn /etc/nginx/sites-available/{API_DOMAIN} /etc/nginx/sites-enabled/{API_DOMAIN}",
    )
    run(ssh, "sudo nginx -t && sudo systemctl reload nginx")
    run(
        ssh,
        "sudo ufw allow OpenSSH || true; sudo ufw allow 80/tcp || true; sudo ufw allow 443/tcp || true",
        check=False,
    )

    print("Waiting for backend ...")
    time.sleep(18)
    run(ssh, "sudo systemctl is-active aimate-chat", check=False)
    run(ssh, "sudo journalctl -u aimate-chat -n 40 --no-pager", check=False)
    run(ssh, f"curl -sf http://127.0.0.1:{APP_PORT}/actuator/health || true", check=False)
    run(ssh, f"curl -sfI http://127.0.0.1/ -H 'Host: {WEB_DOMAIN}' | head -8", check=False)
    run(ssh, f"curl -sfI http://127.0.0.1/ -H 'Host: {ADMIN_DOMAIN}' | head -8", check=False)

    run(
        ssh,
        f"sudo certbot --nginx -d {WEB_DOMAIN} -d {API_DOMAIN} -d {ADMIN_DOMAIN} "
        f"--expand --non-interactive --agree-tos --register-unsafely-without-email --redirect",
        check=False,
        timeout=180,
    )
    run(ssh, "sudo systemctl reload nginx", check=False)
    run(ssh, f"curl -sfI https://{WEB_DOMAIN}/ | head -8", check=False)
    run(ssh, f"curl -sfI https://{ADMIN_DOMAIN}/ | head -8", check=False)
    run(ssh, f"curl -sf https://{API_DOMAIN}/actuator/health || true", check=False)
    run(
        ssh,
        "sudo systemctl enable --now certbot.timer 2>/dev/null || "
        "sudo systemctl enable --now snap.certbot.renew.timer 2>/dev/null || true",
        check=False,
    )

    ssh.close()
    print("\nDeploy finished.")
    print(f"  Web:   https://{WEB_DOMAIN}")
    print(f"  Admin: https://{ADMIN_DOMAIN}")
    print(f"  API:   https://{API_DOMAIN}/actuator/health")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
