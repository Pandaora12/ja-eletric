// Discriminated union — cada modal carrega exatamente os dados que precisa.
// Para adicionar um novo modal: novo membro na union e novo case no GlobalModal.

export type ModalPayload =
  | { type: 'delete-doc';       data: { docId: string; docTitle: string } }
  | { type: 'create-doc';       data: null }
  | { type: 'rename-doc';       data: { docId: string; currentTitle: string } }
  | { type: 'material-picker';  data: { docId: string; blockId: string } }
  | { type: 'dictionary';       data: null }
  | { type: null;               data: null };

// Helper — payload válido que pode ser passado para openModal
export type OpenModalPayload = Exclude<ModalPayload, { type: null }>;
