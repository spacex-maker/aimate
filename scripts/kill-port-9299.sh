#!/usr/bin/env bash
# 杀掉占用 9299 端口的进程（本机启动 Spring 前若提示端口占用可运行此脚本）
set -e
PORT=9299
if command -v lsof &>/dev/null; then
  PID=$(lsof -ti :$PORT 2>/dev/null || true)
elif command -v ss &>/dev/null; then
  PID=$(ss -tlnp 2>/dev/null | awk -v p=":$PORT" '$4 ~ p {gsub(/.*pid=/, ""); gsub(/,.*/, ""); print; exit}' || true)
else
  PID=$(netstat -tlnp 2>/dev/null | awk -v p=":$PORT" '$4 ~ p {gsub(/.*\//, ""); print $NF; exit}' || true)
fi
if [ -n "$PID" ]; then
  echo "Killing PID $PID listening on $PORT..."
  kill -9 $PID
  echo "Done."
else
  echo "No process found listening on port $PORT."
fi
