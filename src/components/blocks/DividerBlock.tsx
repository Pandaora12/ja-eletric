import type { Block, DividerContent } from '../../types/blocks';

interface Props {
  block: Block & { content: DividerContent };
}

export function DividerBlock({ block }: Props) {
  return (
    <hr className={`my-1 border-zinc-200 dark:border-zinc-700 ${block.content.style === 'dashed' ? 'border-dashed' : ''}`} />
  );
}
