import type { Block, SignatureContent } from '../../types/blocks';

interface Props {
  block: Block & { content: SignatureContent };
  onChange: (content: Partial<SignatureContent>) => void;
}

function EditableField({ value, placeholder, onCommit, className = '' }: {
  value: string;
  placeholder: string;
  onCommit: (val: string) => void;
  className?: string;
}) {
  return (
    <div
      contentEditable
      suppressContentEditableWarning
      onBlur={(e) => onCommit(e.currentTarget.textContent ?? '')}
      data-placeholder={placeholder}
      className={`outline-none min-w-[120px] empty:before:content-[attr(data-placeholder)] empty:before:text-zinc-300 dark:empty:before:text-zinc-600 ${className}`}
      dangerouslySetInnerHTML={{ __html: value }}
    />
  );
}

export function SignatureBlock({ block, onChange }: Props) {
  const { label, name, role } = block.content;
  return (
    <div className="flex flex-col items-start gap-1 pt-8 w-64">
      <div className="w-full border-t border-zinc-900 dark:border-zinc-200 pt-2">
        <EditableField
          value={name} placeholder="Nome completo"
          onCommit={(val) => onChange({ name: val })}
          className="text-sm font-medium text-zinc-800 dark:text-zinc-200"
        />
        <EditableField
          value={role} placeholder="Cargo / CREA"
          onCommit={(val) => onChange({ role: val })}
          className="text-xs text-zinc-500 dark:text-zinc-400 mt-0.5"
        />
        <EditableField
          value={label} placeholder="Rótulo"
          onCommit={(val) => onChange({ label: val })}
          className="text-xs text-zinc-400 dark:text-zinc-500 mt-1 italic"
        />
      </div>
    </div>
  );
}
