import { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { useDocumentStore } from '../../stores/documentStore';
import { DeleteDocModal }      from './modals/DeleteDocModal';
import { CreateDocModal }      from './modals/CreateDocModal';
import { RenameDocModal }      from './modals/RenameDocModal';
import { MaterialPickerModal } from './modals/MaterialPickerModal';
import { DictionaryModal }     from './modals/DictionaryModal';

export function GlobalModal() {
  const modal      = useDocumentStore((s) => s.modal);
  const closeModal = useDocumentStore((s) => s.closeModal);

  const [overlayVisible, setOverlayVisible] = useState(false);

  // Fade-in do overlay
  useEffect(() => {
    if (modal.type) {
      requestAnimationFrame(() => setOverlayVisible(true));
    } else {
      setOverlayVisible(false);
    }
  }, [modal.type]);

  useEffect(() => {
    if (!modal.type) return;
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') closeModal(); };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [modal.type, closeModal]);

  useEffect(() => {
    document.body.style.overflow = modal.type ? 'hidden' : '';
    return () => { document.body.style.overflow = ''; };
  }, [modal.type]);

  if (!modal.type) return null;

  return createPortal(
    <div
      role="dialog"
      aria-modal="true"
      className={`fixed inset-0 z-50 flex items-center justify-center
        backdrop-blur-sm transition-all duration-200 ease-out
        ${overlayVisible ? 'bg-black/70' : 'bg-black/0'}`}
      onMouseDown={(e) => { if (e.target === e.currentTarget) closeModal(); }}
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
      {modal.type === 'material-picker' && (
        <MaterialPickerModal data={modal.data} onClose={closeModal} />
      )}
      {modal.type === 'dictionary' && (
        <DictionaryModal onClose={closeModal} />
      )}
    </div>,
    document.body,
  );
}
