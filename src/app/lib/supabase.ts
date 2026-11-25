import type { SupabaseClient } from '@supabase/supabase-js';

/**
 * O app funciona 100% neste computador, sem internet e sem login (perfil local).
 * A integração com o Supabase (nuvem) fica preparada em supabase/ para uma versão futura,
 * mas está desligada aqui de propósito.
 */
export const supabase: SupabaseClient | null = null;
export const modoLocal = true;
