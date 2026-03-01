import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import type { Components } from 'react-markdown'

const markdownComponents: Components = {
  p: ({ children }) => <p className="mb-3 last:mb-0">{children}</p>,
  h1: ({ children }) => <h1 className="text-lg font-semibold text-white/95 mt-4 mb-2 first:mt-0">{children}</h1>,
  h2: ({ children }) => <h2 className="text-base font-semibold text-white/90 mt-4 mb-2 first:mt-0">{children}</h2>,
  h3: ({ children }) => <h3 className="text-sm font-semibold text-white/85 mt-3 mb-1.5 first:mt-0">{children}</h3>,
  h4: ({ children }) => <h4 className="text-sm font-medium text-white/80 mt-3 mb-1 first:mt-0">{children}</h4>,
  ul: ({ children }) => <ul className="list-disc list-inside mb-3 space-y-1 text-white/80">{children}</ul>,
  ol: ({ children }) => <ol className="list-decimal list-inside mb-3 space-y-1 text-white/80">{children}</ol>,
  li: ({ children }) => <li className="leading-relaxed">{children}</li>,
  strong: ({ children }) => <strong className="font-semibold text-white/95">{children}</strong>,
  table: ({ children }) => (
    <div className="my-3 w-full overflow-x-auto rounded-lg border border-white/10">
      <table className="w-full min-w-[280px] border-collapse text-sm text-white/85">{children}</table>
    </div>
  ),
  thead: ({ children }) => <thead className="bg-white/10">{children}</thead>,
  tbody: ({ children }) => <tbody>{children}</tbody>,
  tr: ({ children }) => <tr className="border-b border-white/10 last:border-b-0">{children}</tr>,
  th: ({ children }) => (
    <th className="px-3 py-2 text-left font-semibold text-white/90 align-baseline">
      {children}
    </th>
  ),
  td: ({ children }) => (
    <td className="px-3 py-2 text-left align-baseline">{children}</td>
  ),
  code: ({ className, children, ...props }) => {
    const isBlock = className?.includes('language-')
    if (isBlock) {
      return (
        <pre className="rounded-lg bg-black/30 border border-white/10 p-3 my-2 overflow-x-auto text-xs font-mono text-white/80">
          <code {...props}>{children}</code>
        </pre>
      )
    }
    return (
      <code className="rounded bg-white/10 px-1.5 py-0.5 text-xs font-mono text-white/85" {...props}>
        {children}
      </code>
    )
  },
  blockquote: ({ children }) => (
    <blockquote className="border-l-2 border-white/20 pl-3 my-2 text-white/70 italic">
      {children}
    </blockquote>
  ),
  hr: () => <hr className="border-white/10 my-3" />,
  a: ({ href, children }) => (
    <a href={href} target="_blank" rel="noopener noreferrer" className="text-blue-400 hover:underline">
      {children}
    </a>
  ),
}

interface Props {
  content: string
  className?: string
}

export function MarkdownContent({ content, className = '' }: Props) {
  if (!content?.trim()) return null
  return (
    <div className={`markdown-body ${className}`}>
      <ReactMarkdown remarkPlugins={[remarkGfm]} components={markdownComponents}>{content}</ReactMarkdown>
    </div>
  )
}
