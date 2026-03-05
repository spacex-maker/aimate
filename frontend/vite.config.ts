import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// 为了通过 TypeScript 检查，在这里声明一个简化版的 process 类型。
declare const process: { env?: Record<string, string | undefined> }

export default defineConfig({
  plugins: [react()],
  optimizeDeps: {
    // 只从入口扫描依赖，避免全项目爬取拖慢启动
    entries: ['index.html'],
    // 预构建大/常用依赖，首启后走缓存；lucide-react 体积大，不预构建时 dev 会慢
    include: [
      'sockjs-client',
      '@stomp/stompjs',
      'lucide-react',
      'react-markdown',
      'remark-gfm',
      '@tanstack/react-query',
    ],
  },
  server: {
    port: 3000,
    // 热更新：显式开启 HMR，避免改代码后进程退出
    hmr: true,
    watch: {
      // 忽略依赖和缓存，避免 Vite 写 .vite 缓存时触发监视→重启→退出的循环
      ignored: ['**/node_modules/**', '**/.git/**', '**/node_modules/.vite/**'],
      // 若改代码后 dev 进程直接退出：先试 false（原生监视）；若出现 0xC0000006 再改 true
      usePolling: false,
      ...(process.env?.VITE_USE_POLLING === '1' ? { usePolling: true, interval: 2000 } : {}),
    },
    proxy: {
      '/api': {
        target: 'http://localhost:9299',
        changeOrigin: true,
      },
      '/ws': {
        target: 'http://localhost:9299',
        changeOrigin: true,
        ws: true,
      },
    },
  },
})

