import fs from 'node:fs'
import path from 'node:path'
import { execFile } from 'node:child_process'
import { promisify } from 'node:util'

const execFileAsync = promisify(execFile)

export const BUILTIN_TOOLS = [
  {
    type: 'function',
    function: {
      name: 'store_memory',
      description: 'Store an important fact into long-term project memory.',
      parameters: {
        type: 'object',
        properties: {
          content: { type: 'string' },
          importance: { type: 'number' },
        },
        required: ['content'],
      },
    },
  },
  {
    type: 'function',
    function: {
      name: 'read_file',
      description: 'Read a UTF-8 text file relative to the project root.',
      parameters: {
        type: 'object',
        properties: {
          path: { type: 'string' },
        },
        required: ['path'],
      },
    },
  },
  {
    type: 'function',
    function: {
      name: 'list_dir',
      description: 'List files in a directory relative to the project root.',
      parameters: {
        type: 'object',
        properties: {
          path: { type: 'string' },
        },
        required: [],
      },
    },
  },
  {
    type: 'function',
    function: {
      name: 'run_shell',
      description: 'Run a shell command in the project directory (Windows cmd / powershell).',
      parameters: {
        type: 'object',
        properties: {
          command: { type: 'string' },
        },
        required: ['command'],
      },
    },
  },
]

function safeJoin(projectPath: string, rel: string): string {
  const resolved = path.resolve(projectPath, rel || '.')
  const root = path.resolve(projectPath)
  if (!resolved.startsWith(root)) {
    throw new Error('Path escapes project root')
  }
  return resolved
}

export async function executeTool(
  name: string,
  argsJson: string,
  ctx: {
    projectPath: string
    storeMemory: (content: string, importance?: number) => Promise<string>
  },
): Promise<string> {
  let args: Record<string, unknown> = {}
  try {
    args = JSON.parse(argsJson || '{}') as Record<string, unknown>
  } catch {
    return 'Invalid JSON arguments'
  }

  try {
    switch (name) {
      case 'store_memory': {
        const content = String(args.content ?? '')
        const importance = typeof args.importance === 'number' ? args.importance : 0.5
        return await ctx.storeMemory(content, importance)
      }
      case 'read_file': {
        const file = safeJoin(ctx.projectPath, String(args.path ?? ''))
        if (!fs.existsSync(file)) return `File not found: ${args.path}`
        const text = fs.readFileSync(file, 'utf8')
        return text.length > 20000 ? text.slice(0, 20000) + '\n...[truncated]' : text
      }
      case 'list_dir': {
        const dir = safeJoin(ctx.projectPath, String(args.path ?? '.'))
        if (!fs.existsSync(dir)) return `Directory not found: ${args.path}`
        return fs
          .readdirSync(dir, { withFileTypes: true })
          .map((e) => `${e.isDirectory() ? 'd' : 'f'} ${e.name}`)
          .join('\n')
      }
      case 'run_shell': {
        const command = String(args.command ?? '')
        const { stdout, stderr } = await execFileAsync(
          process.platform === 'win32' ? 'cmd.exe' : 'bash',
          process.platform === 'win32' ? ['/c', command] : ['-lc', command],
          { cwd: ctx.projectPath, timeout: 60_000, maxBuffer: 2_000_000 },
        )
        const out = `${stdout || ''}${stderr ? `\nSTDERR:\n${stderr}` : ''}`.trim()
        return out.slice(0, 15000) || '(empty output)'
      }
      default:
        return `Unknown tool: ${name}`
    }
  } catch (e) {
    return `Tool error: ${e instanceof Error ? e.message : String(e)}`
  }
}
