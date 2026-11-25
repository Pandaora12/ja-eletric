/** Perfil local (substitui login e cadastro): nome e foto, salvos só neste computador. */
export interface Perfil { id: string; nome: string; foto: string | null; criado_em: string }

const K = 'ja.perfil';

export function lerPerfil(): Perfil | null {
  try { const v = localStorage.getItem(K); return v ? (JSON.parse(v) as Perfil) : null; } catch { return null; }
}

export function salvarPerfil(p: Perfil) {
  localStorage.setItem(K, JSON.stringify(p));
}

/** Reduz a foto para 320 px (fica leve para guardar no computador). */
export async function prepararFoto(f: File): Promise<string> {
  if (!f.type.startsWith('image/')) throw new Error('Escolha uma imagem (JPG ou PNG).');
  const img = await createImageBitmap(f);
  const lado = Math.min(img.width, img.height);
  const c = document.createElement('canvas');
  c.width = c.height = 320;
  c.getContext('2d')!.drawImage(img, (img.width - lado) / 2, (img.height - lado) / 2, lado, lado, 0, 0, 320, 320);
  return c.toDataURL('image/jpeg', 0.86);
}
