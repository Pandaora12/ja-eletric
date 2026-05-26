import { useRef } from 'react';
import type { Block, HeadingContent } from '../../types/blocks';

interface Props {
  block: Block & { content: HeadingContent };
  onChange: (content: Partial<HeadingContent>) => void;
}

const levelClasses: Record<1 | 2 | 3, string> = {
  1: 'text-2xl font-bold text-zinc-900 dark:text-zinc-100',
  2: 'text-xl font-semibold text-zinc-800 dark:text-zinc-200',
  3: 'text-base font-semibold text-zinc-700 dark:text-zinc-300',
};

export function HeadingBlock({ block, onChange }: Props) {
  const ref = useRef<HTMLDivElement>(null);
  const { text, level } = block.content;

  return (
    <div className="group/heading flex items-start gap-2">
      <select
        value={level}
        onChange={(e) => onChange({ level: Number(e.target.value) as 1 | 2 | 3 })}
        className="mt-1 shrink-0 text-xs text-zinc-400 dark:text-zinc-500 bg-transparent border-none cursor-pointer opacity-0 group-hover/heading:opacity-100 focus:opacity-100 transition-opacity"
      >
        <option value={1}>H1</option>
        <option value={2}>H2</option>
        <option value={3}>H3</option>
      </select>

      <div
        ref={ref}
        contentEditable
        suppressContentEditableWarning
        onBlur={(e) => onChange({ text: e.currentTarget.textContent ?? '' })}
        className={`flex-1 outline-none border-none bg-transparent w-full min-h-[1.5rem] ${levelClasses[level]} empty:before:content-['Título...'] empty:before:text-zinc-300 dark:empty:before:text-zinc-600`}
        dangerouslySetInnerHTML={{ __html: text }}
      />
    </div>
  );
}
