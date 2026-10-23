#!/usr/bin/env python3
"""Apply agent_industries migration + seed on chat prod MySQL (via SSH)."""
from __future__ import annotations

import sys
from pathlib import Path

import paramiko

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from deploy_chat_prod import HOST, load_merged, env_get  # noqa: E402

DDL = r"""
CREATE TABLE IF NOT EXISTS agent_industries (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    code         VARCHAR(64)  NOT NULL,
    name         VARCHAR(128) NOT NULL,
    description  VARCHAR(512) NULL,
    sort_order   INT          NOT NULL DEFAULT 100,
    enabled      TINYINT(1)   NOT NULL DEFAULT 1,
    create_time  DATETIME(6)  NOT NULL,
    update_time  DATETIME(6)  NOT NULL,
    version      INT          NOT NULL DEFAULT 0,
    UNIQUE KEY uk_agent_industries_code (code)
);
"""

SEED = r"""
INSERT INTO agent_industries (code, name, description, sort_order, enabled, create_time, update_time, version)
SELECT 'TECH', '技术研发', '软件工程、后端、前端与基础设施', 10, 1, NOW(6), NOW(6), 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM agent_industries WHERE code = 'TECH');

INSERT INTO agent_industries (code, name, description, sort_order, enabled, create_time, update_time, version)
SELECT 'PRODUCT', '产品运营', '产品规划、运营增长与项目管理', 20, 1, NOW(6), NOW(6), 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM agent_industries WHERE code = 'PRODUCT');

INSERT INTO agent_industries (code, name, description, sort_order, enabled, create_time, update_time, version)
SELECT 'DESIGN', '设计创意', 'UI/UX、视觉与品牌体验', 30, 1, NOW(6), NOW(6), 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM agent_industries WHERE code = 'DESIGN');

INSERT INTO agent_industries (code, name, description, sort_order, enabled, create_time, update_time, version)
SELECT 'DATA', '数据智能', '数据分析、商业智能与机器学习', 40, 1, NOW(6), NOW(6), 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM agent_industries WHERE code = 'DATA');

UPDATE agent_careers c
JOIN agent_industries i ON i.code = 'TECH'
SET c.industry_id = i.id
WHERE c.code = 'JAVA_ENGINEER' AND (c.industry_id IS NULL OR c.industry_id <> i.id);

UPDATE agent_careers c
JOIN agent_industries i ON i.code = 'PRODUCT'
SET c.industry_id = i.id
WHERE c.code = 'PRODUCT_MANAGER' AND (c.industry_id IS NULL OR c.industry_id <> i.id);

UPDATE agent_careers c
JOIN agent_industries i ON i.code = 'DESIGN'
SET c.industry_id = i.id
WHERE c.code = 'UI_DESIGNER' AND (c.industry_id IS NULL OR c.industry_id <> i.id);

UPDATE agent_careers c
JOIN agent_industries i ON i.code = 'DATA'
SET c.industry_id = i.id
WHERE c.code = 'DATA_ANALYST' AND (c.industry_id IS NULL OR c.industry_id <> i.id);
"""

VERIFY = r"""
SELECT 'industries' AS kind, id, code, name, sort_order FROM agent_industries ORDER BY sort_order, id;
SELECT 'careers' AS kind, c.id, c.code, c.name, c.industry_id, i.code AS industry_code, i.name AS industry_name
FROM agent_careers c
LEFT JOIN agent_industries i ON i.id = c.industry_id
ORDER BY c.sort_order, c.id;
"""


def run(ssh: paramiko.SSHClient, cmd: str, timeout: int = 120) -> tuple[int, str, str]:
    stdin, stdout, stderr = ssh.exec_command(cmd, timeout=timeout)
    out = stdout.read().decode("utf-8", "replace")
    err = stderr.read().decode("utf-8", "replace")
    code = stdout.channel.recv_exit_status()
    return code, out, err


def mysql_batch(ssh: paramiko.SSHClient, sql: str) -> tuple[int, str, str]:
    remote = "/tmp/aimate_migrate_agent_industries.sql"
    sftp = ssh.open_sftp()
    with sftp.file(remote, "w") as f:
        f.write(sql)
    sftp.close()
    code, out, err = run(ssh, f"sudo mysql aimate < {remote}")
    run(ssh, f"rm -f {remote}", timeout=30)
    return code, out, err


def ensure_industry_id_column(ssh: paramiko.SSHClient) -> None:
    code, out, err = run(
        ssh,
        "sudo mysql aimate -N -e \"SHOW COLUMNS FROM agent_careers LIKE 'industry_id';\"",
    )
    if code != 0:
        raise SystemExit(f"SHOW COLUMNS failed: {err or out}")
    if out.strip():
        print("column industry_id already exists")
        return
    code, out, err = run(
        ssh,
        "sudo mysql aimate -e \"ALTER TABLE agent_careers ADD COLUMN industry_id BIGINT NULL;\"",
    )
    if code != 0:
        raise SystemExit(f"ADD COLUMN industry_id failed: {err or out}")
    print("added column agent_careers.industry_id")
    run(
        ssh,
        "sudo mysql aimate -e \"CREATE INDEX idx_agent_careers_industry_id ON agent_careers (industry_id);\"",
        timeout=60,
    )


def main() -> int:
    data = load_merged()
    user = env_get(data, "DEPLOY_SSH_USER", default="ubuntu")
    password = env_get(data, "DEPLOY_SSH_PASSWORD")
    if not password:
        print("缺少 DEPLOY_SSH_PASSWORD", file=sys.stderr)
        return 1

    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    print(f"Connecting {user}@{HOST} ...")
    ssh.connect(HOST, username=user, password=password, timeout=30)

    print("1) CREATE TABLE agent_industries")
    code, out, err = mysql_batch(ssh, DDL)
    if code != 0:
        print(err or out, file=sys.stderr)
        return code

    print("2) ensure industry_id column")
    ensure_industry_id_column(ssh)

    print("3) seed industries + link careers")
    code, out, err = mysql_batch(ssh, SEED)
    if code != 0:
        print(err or out, file=sys.stderr)
        return code

    print("4) verify")
    code, out, err = mysql_batch(ssh, VERIFY)
    sys.stdout.write(out)
    if err.strip():
        sys.stderr.write(err)
    if code != 0:
        return code

    ssh.close()
    print("DONE")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
