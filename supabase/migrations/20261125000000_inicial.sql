-- J.A Documentos · estrutura inicial
-- Empresas, quem pertence a cada empresa, e os documentos (orçamentos, relatórios, recibos, comprovantes).
-- Cada pessoa só enxerga os dados da própria empresa (Row Level Security).

create extension if not exists pgcrypto;

create table public.empresas (
  id uuid primary key default gen_random_uuid(),
  nome text not null,
  -- dados de marca e cadastro: CNPJ, endereço, telefones, Pix, responsável, cores, logo...
  dados jsonb not null default '{}'::jsonb,
  -- código para convidar outras pessoas da mesma empresa no cadastro
  codigo_convite text not null unique default upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 8)),
  criado_em timestamptz not null default now()
);

create table public.membros (
  user_id uuid primary key references auth.users (id) on delete cascade,
  empresa_id uuid not null references public.empresas (id) on delete cascade,
  nome text not null default '',
  papel text not null default 'membro' check (papel in ('admin', 'membro')),
  criado_em timestamptz not null default now()
);

create table public.documentos (
  id uuid primary key default gen_random_uuid(),
  empresa_id uuid not null references public.empresas (id) on delete cascade,
  tipo text not null check (tipo in ('orcamento', 'relatorio', 'recibo', 'comprovante')),
  numero text,
  titulo text,
  cliente text,
  valor numeric(14, 2),
  status text not null default 'rascunho',
  dados jsonb not null default '{}'::jsonb,
  criado_por uuid references auth.users (id) default auth.uid(),
  criado_em timestamptz not null default now(),
  atualizado_em timestamptz not null default now()
);
create index documentos_empresa_tipo on public.documentos (empresa_id, tipo, criado_em desc);

-- empresa da pessoa logada
create or replace function public.minha_empresa() returns uuid
language sql stable security definer set search_path = public as $$
  select empresa_id from public.membros where user_id = auth.uid()
$$;

alter table public.empresas enable row level security;
alter table public.membros enable row level security;
alter table public.documentos enable row level security;

create policy "ver a própria empresa" on public.empresas
  for select using (id = public.minha_empresa());
create policy "admin altera a empresa" on public.empresas
  for update using (id = public.minha_empresa()
    and exists (select 1 from public.membros m where m.user_id = auth.uid() and m.papel = 'admin'));

create policy "ver colegas" on public.membros
  for select using (empresa_id = public.minha_empresa());

create policy "documentos da empresa" on public.documentos
  for all using (empresa_id = public.minha_empresa())
  with check (empresa_id = public.minha_empresa());

-- atualiza a data de alteração
create or replace function public.tocar_atualizado() returns trigger language plpgsql as $$
begin new.atualizado_em = now(); return new; end $$;
create trigger documentos_atualizado before update on public.documentos
  for each row execute function public.tocar_atualizado();

-- No cadastro: quem informa um código de convite entra na empresa dele;
-- quem não informa cria uma empresa nova e vira admin.
create or replace function public.novo_usuario() returns trigger
language plpgsql security definer set search_path = public as $$
declare
  v_empresa uuid;
  v_codigo text := upper(coalesce(new.raw_user_meta_data ->> 'codigo_convite', ''));
  v_nome text := coalesce(new.raw_user_meta_data ->> 'nome', '');
begin
  if v_codigo <> '' then
    select id into v_empresa from public.empresas where codigo_convite = v_codigo;
    if v_empresa is null then
      raise exception 'Código de convite inválido';
    end if;
    insert into public.membros (user_id, empresa_id, nome, papel) values (new.id, v_empresa, v_nome, 'membro');
  else
    insert into public.empresas (nome) values (coalesce(nullif(new.raw_user_meta_data ->> 'empresa', ''), 'Minha empresa'))
      returning id into v_empresa;
    insert into public.membros (user_id, empresa_id, nome, papel) values (new.id, v_empresa, v_nome, 'admin');
  end if;
  return new;
end $$;

create trigger ao_criar_usuario after insert on auth.users
  for each row execute function public.novo_usuario();
