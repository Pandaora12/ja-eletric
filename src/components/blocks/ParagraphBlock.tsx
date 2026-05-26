import type { Block, ParagraphContent } from '../../types/blocks';

interface Props {
  block: Block & { content: ParagraphContent };
  onChange: (content: Partial<ParagraphContent>) => void;
}

export function ParagraphBlock({ block, onChange }: Props) {
  return (
    <div
      contentEditable
      suppressContentEditableWarning
      onBlur={(e) => onChange({ text: e.currentTarget.textContent ?? '' })}
      className="outline-none w-full min-h-[1.5rem] text-sm text-zinc-700 dark:text-zinc-300 leading-relaxed empty:before:content-['Escreva_aqui...'] empty:before:text-zinc-300 dark:empty:before:text-zinc-600"
      dangerouslySetInnerHTML={{ __html: block.content.text }}
    />
  );
}
