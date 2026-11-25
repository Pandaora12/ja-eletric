import { createClient, type SupabaseClient } from '@supabase/supabase-js';

const url = import.meta.env.VITE_SUPABASE_URL as string | undefined;
const chave = import.meta.env.VITE_SUPABASE_ANON_KEY as string | undefined;

/** null = modo local (sem Supabase): login e documentos ficam só neste computador. */
export const supabase: SupabaseClient | null = url && chave ? createClient(url, chave) : null;
export const modoLocal = !supabase;
