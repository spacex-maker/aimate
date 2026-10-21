#!/usr/bin/env python3
"""Create Chat 专用 COS 桶 aimatex-chat-1258150206（若不存在）。"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import deploy_chat_prod as d

try:
    from qcloud_cos import CosConfig, CosS3Client
    from qcloud_cos.cos_exception import CosServiceError
except ImportError:
    print("需要: pip install cos-python-sdk-v5", file=sys.stderr)
    sys.exit(1)

BUCKET = "aimatex-chat-1258150206"
REGION = "ap-nanjing"


def main() -> int:
    data = d.load_merged()
    sid = d.env_get(data, "TENCENTCLOUD_SECRET_ID", "AIMATEX_CHAT_COS_SECRET_ID")
    skey = d.env_get(data, "TENCENTCLOUD_SECRET_KEY", "AIMATEX_CHAT_COS_SECRET_KEY")
    bucket = d.env_get(data, "AIMATEX_CHAT_COS_BUCKET", default=BUCKET)
    region = d.env_get(data, "AIMATEX_CHAT_COS_REGION", default=REGION)
    if not sid or not skey:
        print("缺少 TENCENTCLOUD_SECRET_ID/KEY", file=sys.stderr)
        return 1

    client = CosS3Client(CosConfig(Region=region, SecretId=sid, SecretKey=skey, Scheme="https"))
    try:
        client.head_bucket(Bucket=bucket)
        print(f"Bucket already exists: {bucket} ({region})")
    except CosServiceError as e:
        code = e.get_error_code() if hasattr(e, "get_error_code") else ""
        status = e.get_status_code() if hasattr(e, "get_status_code") else None
        if status not in (404,) and code not in ("NoSuchBucket", "NoSuchResource", ""):
            print(f"head_bucket unexpected: status={status} code={code} {e}")
        client.create_bucket(Bucket=bucket)
        print(f"Created bucket: {bucket} ({region})")
    print(f"Public object URL base: https://{bucket}.cos.{region}.myqcloud.com/")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
