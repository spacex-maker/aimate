#!/usr/bin/env python3
"""Seed CROSS_BORDER industry + careers + catalog tiers on chat prod."""
from __future__ import annotations

import sys
from pathlib import Path

import paramiko

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from deploy_chat_prod import HOST, load_merged, env_get  # noqa: E402

SQL = r"""
SET NAMES utf8mb4;

INSERT INTO agent_industries (code, name, description, sort_order, enabled, create_time, update_time, version)
SELECT 'CROSS_BORDER', '跨境电商', '跨境平台运营、独立站、选品供应链与投放增长', 50, 1, NOW(6), NOW(6), 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM agent_industries WHERE code = 'CROSS_BORDER');

INSERT INTO agent_careers (industry_id, code, name, description, sort_order, enabled, create_time, update_time, version)
SELECT i.id, 'CB_OPS', '跨境运营专员', '多平台店铺日常运营、Listing 与转化优化', 10, 1, NOW(6), NOW(6), 0
FROM agent_industries i WHERE i.code = 'CROSS_BORDER'
  AND NOT EXISTS (SELECT 1 FROM agent_careers WHERE code = 'CB_OPS');

INSERT INTO agent_careers (industry_id, code, name, description, sort_order, enabled, create_time, update_time, version)
SELECT i.id, 'AMAZON_OPS', '亚马逊运营', 'Amazon 店铺、广告、库存与品牌旗舰', 20, 1, NOW(6), NOW(6), 0
FROM agent_industries i WHERE i.code = 'CROSS_BORDER'
  AND NOT EXISTS (SELECT 1 FROM agent_careers WHERE code = 'AMAZON_OPS');

INSERT INTO agent_careers (industry_id, code, name, description, sort_order, enabled, create_time, update_time, version)
SELECT i.id, 'INDEPENDENT_SITE_OPS', '独立站运营', 'Shopify/自建站获客、转化与复购', 30, 1, NOW(6), NOW(6), 0
FROM agent_industries i WHERE i.code = 'CROSS_BORDER'
  AND NOT EXISTS (SELECT 1 FROM agent_careers WHERE code = 'INDEPENDENT_SITE_OPS');

INSERT INTO agent_careers (industry_id, code, name, description, sort_order, enabled, create_time, update_time, version)
SELECT i.id, 'TIKTOK_SHOP_OPS', 'TikTok Shop 运营', '短视频带货、直播与店铺增长', 40, 1, NOW(6), NOW(6), 0
FROM agent_industries i WHERE i.code = 'CROSS_BORDER'
  AND NOT EXISTS (SELECT 1 FROM agent_careers WHERE code = 'TIKTOK_SHOP_OPS');

INSERT INTO agent_careers (industry_id, code, name, description, sort_order, enabled, create_time, update_time, version)
SELECT i.id, 'CB_SOURCING', '跨境选品专员', '市场调研、竞品分析与爆品选品', 50, 1, NOW(6), NOW(6), 0
FROM agent_industries i WHERE i.code = 'CROSS_BORDER'
  AND NOT EXISTS (SELECT 1 FROM agent_careers WHERE code = 'CB_SOURCING');

INSERT INTO agent_careers (industry_id, code, name, description, sort_order, enabled, create_time, update_time, version)
SELECT i.id, 'CB_SUPPLY_CHAIN', '跨境供应链专员', '采购、质检、头程物流与海外仓协同', 60, 1, NOW(6), NOW(6), 0
FROM agent_industries i WHERE i.code = 'CROSS_BORDER'
  AND NOT EXISTS (SELECT 1 FROM agent_careers WHERE code = 'CB_SUPPLY_CHAIN');

INSERT INTO agent_careers (industry_id, code, name, description, sort_order, enabled, create_time, update_time, version)
SELECT i.id, 'CB_CUSTOMER_SERVICE', '跨境客服', '多语言售前售后、纠纷与评价维护', 70, 1, NOW(6), NOW(6), 0
FROM agent_industries i WHERE i.code = 'CROSS_BORDER'
  AND NOT EXISTS (SELECT 1 FROM agent_careers WHERE code = 'CB_CUSTOMER_SERVICE');

INSERT INTO agent_careers (industry_id, code, name, description, sort_order, enabled, create_time, update_time, version)
SELECT i.id, 'CB_ADS', '跨境广告投放', 'Google/Meta/平台广告投放与 ROI 优化', 80, 1, NOW(6), NOW(6), 0
FROM agent_industries i WHERE i.code = 'CROSS_BORDER'
  AND NOT EXISTS (SELECT 1 FROM agent_careers WHERE code = 'CB_ADS');

-- Ensure industry link for any of the above that already existed without industry
UPDATE agent_careers c
JOIN agent_industries i ON i.code = 'CROSS_BORDER'
SET c.industry_id = i.id
WHERE c.code IN (
  'CB_OPS','AMAZON_OPS','INDEPENDENT_SITE_OPS','TIKTOK_SHOP_OPS',
  'CB_SOURCING','CB_SUPPLY_CHAIN','CB_CUSTOMER_SERVICE','CB_ADS'
) AND (c.industry_id IS NULL OR c.industry_id <> i.id);

-- Catalog tiers for each new career (JUNIOR/MID/SENIOR/EXPERT)
INSERT INTO agent_catalog (
  career_id, tier, name, description, persona_template, default_gender,
  price_day_points, price_month_points, price_year_points,
  soul_price_day_points, soul_price_month_points, soul_price_year_points,
  enabled, sort_order, create_time, update_time, version
)
SELECT
  c.id,
  t.tier,
  CONCAT(c.name, ' · ', t.label),
  CONCAT(IFNULL(c.description, ''), '（', t.label, '）'),
  CONCAT('你是一名', t.label, c.name, '，专业、可靠、沟通清晰。'),
  'UNSPECIFIED',
  100 * t.mult, 2000 * t.mult, 18000 * t.mult,
  50 * t.mult, 800 * t.mult, 7000 * t.mult,
  1,
  c.sort_order * 10 + t.ord,
  NOW(6), NOW(6), 0
FROM agent_careers c
JOIN (
  SELECT 'JUNIOR' AS tier, '初级' AS label, 1 AS mult, 0 AS ord
  UNION ALL SELECT 'MID', '中级', 2, 1
  UNION ALL SELECT 'SENIOR', '高级', 4, 2
  UNION ALL SELECT 'EXPERT', '专家', 8, 3
) t
WHERE c.code IN (
  'CB_OPS','AMAZON_OPS','INDEPENDENT_SITE_OPS','TIKTOK_SHOP_OPS',
  'CB_SOURCING','CB_SUPPLY_CHAIN','CB_CUSTOMER_SERVICE','CB_ADS'
)
AND NOT EXISTS (
  SELECT 1 FROM agent_catalog x WHERE x.career_id = c.id AND x.tier = t.tier
);

SELECT 'industries' AS kind, code, name FROM agent_industries WHERE code = 'CROSS_BORDER';
SELECT 'careers' AS kind, c.code, c.name, i.name AS industry
FROM agent_careers c
JOIN agent_industries i ON i.id = c.industry_id
WHERE i.code = 'CROSS_BORDER'
ORDER BY c.sort_order, c.id;
SELECT 'catalog' AS kind, COUNT(*) AS cnt
FROM agent_catalog cat
JOIN agent_careers c ON c.id = cat.career_id
JOIN agent_industries i ON i.id = c.industry_id
WHERE i.code = 'CROSS_BORDER';
"""


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

    remote = "/tmp/seed_cross_border.sql"
    sftp = ssh.open_sftp()
    with sftp.file(remote, "w") as f:
        f.write(SQL)
    sftp.close()

    stdin, stdout, stderr = ssh.exec_command(f"sudo mysql aimate < {remote}", timeout=120)
    out = stdout.read()
    err = stderr.read()
    code = stdout.channel.recv_exit_status()
    sys.stdout.buffer.write(out)
    if err:
        sys.stderr.buffer.write(err)
    ssh.exec_command(f"rm -f {remote}")
    ssh.close()
    if code == 0:
        print("DONE")
    return code


if __name__ == "__main__":
    raise SystemExit(main())
