-- J.A Documentos · clientes e normas (cada empresa vê só os seus)

create table public.clientes (
  id uuid primary key default gen_random_uuid(),
  empresa_id uuid not null references public.empresas (id) on delete cascade,
  nome text not null default '',
  documento text not null default '',
  contato text not null default '',
  endereco text not null default '',
  cidade text not null default '',
  cep text not null default '',
  telefone text not null default '',
  email text not null default '',
  situacao text not null default 'atendido' check (situacao in ('orcamento', 'aprovado', 'atendido', 'nao_atendido')),
  pagamento text not null default 'nao_pago' check (pagamento in ('pago', 'nao_pago', 'parcial')),
  observacoes text not null default '',
  criado_em timestamptz not null default now(),
  atualizado_em timestamptz not null default now()
);
create index clientes_empresa_nome on public.clientes (empresa_id, nome);

create table public.normas (
  id uuid primary key default gen_random_uuid(),
  empresa_id uuid not null references public.empresas (id) on delete cascade,
  codigo text not null,
  descricao text not null default '',
  criado_em timestamptz not null default now()
);

alter table public.clientes enable row level security;
alter table public.normas enable row level security;

create policy "clientes da empresa" on public.clientes
  for all using (empresa_id = public.minha_empresa())
  with check (empresa_id = public.minha_empresa());

create policy "normas da empresa" on public.normas
  for all using (empresa_id = public.minha_empresa())
  with check (empresa_id = public.minha_empresa());

create trigger clientes_atualizado before update on public.clientes
  for each row execute function public.tocar_atualizado();

-- normas mais usadas já cadastradas para cada empresa nova
create or replace function public.normas_iniciais() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  insert into public.normas (empresa_id, codigo, descricao) values
    (new.id, 'NBR 5410', 'Instalações elétricas de baixa tensão'),
    (new.id, 'NBR 5419', 'Proteção contra descargas atmosféricas (SPDA)'),
    (new.id, 'NBR 14039', 'Instalações elétricas de média tensão (1,0 kV a 36,2 kV)'),
    (new.id, 'NBR IEC 61439', 'Conjuntos de manobra e comando de baixa tensão (quadros)'),
    (new.id, 'NBR IEC 60947-2', 'Disjuntores de baixa tensão'),
    (new.id, 'NBR NM 60898', 'Disjuntores para instalações domésticas e similares'),
    (new.id, 'NR-10', 'Segurança em instalações e serviços em eletricidade'),
    (new.id, 'NR-35', 'Trabalho em altura');
  return new;
end $$;

create trigger empresa_normas_iniciais after insert on public.empresas
  for each row execute function public.normas_iniciais();
