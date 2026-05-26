import { useEffect } from 'react';
import { createPortal } from 'react-dom';
import { useDocumentStore } from '../../stores/documentStore';
import { DeleteDocModal } from './modals/DeleteDocModal';
import { CreateDocModal } from './modals/CreateDocModal';
import { RenameDocModal } from './modals/RenameDocModal';

export function GlobalModal() {
  const modal = useDocumentStore((s) => s.modal);
  const closeModal = useDocumentStore((s) => s.closeModal);

  useEffect(() => {
    if (!modal.type) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') closeModal();
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [modal.type, closeModal]);

  useEffect(() => {
    if (modal.type) {
      document.body.style.overflow = 'hidden';
    } else {
      document.body.style.overflow = '';
    }
    return () => { document.body.style.overflow = ''; };
  }, [modal.type]);

  if (!modal.type) return null;

  return createPortal(
    <div
      role="dialog"
      aria-modal="true"
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/70"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) closeModal();
      }}
    >
      {modal.type === 'delete-doc' && (
        <DeleteDocModal data={modal.data} onClose={closeModal} />
      )}
      {modal.type === 'create-doc' && (
        <CreateDocModal onClose={closeModal} />
      )}
      {modal.type === 'rename-doc' && (
        <RenameDocModal data={modal.data} onClose={closeModal} />
      )}
    </div>,
    document.body,
  );
}
