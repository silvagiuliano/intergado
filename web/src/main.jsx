import React, { useEffect, useMemo, useState } from 'react'
import { createRoot } from 'react-dom/client'
import './style.css'

const statusLabels = {
  RECEIVED: 'Recebido', QUOTING: 'Em cotação', AWAITING_APPROVAL: 'Aguardando aprovação',
  APPROVED: 'Aprovado', ORDERED: 'Pedido feito', DELIVERED: 'Entregue', PAID: 'Pago',
}
const statusOrder = Object.keys(statusLabels)
const money = value => new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(Number(value || 0))
const integer = value => new Intl.NumberFormat('pt-BR').format(value)
const date = value => new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: 'short' }).format(new Date(value))
const Icon = ({ name, size = 20 }) => {
  const paths = {
    grid: <><rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/></>,
    box: <><path d="M3 7 12 3l9 4v10l-9 4-9-4z"/><path d="m3 7 9 4 9-4M12 11v10"/></>,
    chart: <><path d="M4 20V10m6 10V4m6 16v-7m4 7V8"/></>,
    plus: <path d="M12 5v14M5 12h14"/>,
    search: <><circle cx="11" cy="11" r="7"/><path d="m20 20-4-4"/></>,
    arrow: <path d="M5 12h14m-6-6 6 6-6 6"/>,
    refresh: <><path d="M20 7v5h-5M4 17v-5h5"/><path d="M5.5 9A7 7 0 0 1 18 7l2 5M4 12l2 5a7 7 0 0 0 12.5-2"/></>,
    check: <path d="m4 12 5 5L20 6"/>,
    clock: <><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></>,
    logout: <><path d="M15 4h3a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-3"/><path d="M10 17l5-5-5-5M15 12H4"/></>,
    plug: <><path d="M9 2v6M15 2v6M6 8h12v4a6 6 0 0 1-12 0z"/><path d="M12 18v4"/></>,
    leaf: <><path d="M20 4c-9 0-15 3-15 11a5 5 0 0 0 5 5c8 0 11-6 10-16Z"/><path d="M4 21c3-7 7-10 13-13"/></>,
    edit: <><path d="M4 20h4L19 9l-4-4L4 16z"/><path d="m13.5 6.5 4 4"/></>,
    users: <><circle cx="9" cy="8" r="3.5"/><path d="M2.5 20a6.5 6.5 0 0 1 13 0"/><path d="M16 4.5a3.5 3.5 0 0 1 0 7M18 14a6 6 0 0 1 3.5 6"/></>,
  }
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths[name]}</svg>
}

class Unauthorized extends Error {}

async function readJson(url, options) {
  const response = await fetch(url, options)
  if (response.status === 401) throw new Unauthorized('Sessão expirada. Entre novamente.')
  if (!response.ok) {
    // Only our own handlers answer { error } without Spring's default "status" field.
    const body = await response.json().catch(() => null)
    if (body?.error && body.status === undefined) throw new Error(body.error)
    throw new Error(response.status === 400 ? 'Confira os dados informados.' : 'Não foi possível concluir a operação.')
  }
  return response.json()
}

const roleLabels = { LEITOR: 'Leitor', OPERADOR: 'Operador', ADMIN: 'Administrador' }
// Admin-only screens; other roles land on the dashboard even with the hash in the URL.
const adminViews = { '#integracoes': 'integrations', '#usuarios': 'users' }
const viewFor = (hash, role) => role === 'ADMIN' && adminViews[hash] || 'dashboard'

const sendJson = method => (url, body) => readJson(url, { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) })
const postJson = sendJson('POST')
const putJson = sendJson('PUT')
const categories = ['Insumos', 'Manutenção', 'Pecuária', 'Combustível', 'Outros']
const today = () => new Date().toLocaleDateString('sv-SE')

function Root() {
  const [user, setUser] = useState(undefined)
  useEffect(() => {
    readJson('/api/auth/me').then(setUser).catch(() => setUser(null))
  }, [])
  async function logout() {
    await fetch('/api/auth/logout', { method: 'POST' }).catch(() => {})
    setUser(null)
  }
  if (user === undefined) return <div className="login-page"><p className="login-loading">Carregando...</p></div>
  if (!user) return <Login onLogin={setUser}/>
  return <App user={user} onLogout={logout} onExpired={() => setUser(null)}/>
}

function Login({ onLogin }) {
  const [email, setEmail] = useState('')
  const [code, setCode] = useState('')
  const [step, setStep] = useState('email')
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function requestCode(event) {
    event?.preventDefault()
    setBusy(true); setError('')
    try {
      const answer = await postJson('/api/auth/request-code', { email })
      setMessage(answer.message)
      setStep('code')
    } catch (e) { setError(e.message) }
    finally { setBusy(false) }
  }

  async function verify(event) {
    event.preventDefault()
    setBusy(true); setError('')
    try { onLogin(await postJson('/api/auth/verify', { email, code })) }
    catch (e) { setError(e instanceof Unauthorized ? 'Código inválido ou expirado.' : e.message) }
    finally { setBusy(false) }
  }

  return <div className="login-page">
    <div className="modal login-card">
      <div className="brand login-brand"><span className="brand-mark"><Icon name="leaf" size={23}/></span><span>intergado<small>GESTÃO RURAL</small></span></div>
      <div className="eyebrow">ACESSO</div>
      {step === 'email' ? <>
        <h2>Entrar</h2>
        <p>Informe seu e-mail. Enviaremos um código de 6 dígitos para confirmar o acesso.</p>
        <form onSubmit={requestCode}>
          <label>E-mail<input type="email" required maxLength="254" autoComplete="email" autoFocus value={email} onChange={e => setEmail(e.target.value)} placeholder="nome@empresa.com.br"/></label>
          {error && <div className="error" role="alert">{error}</div>}
          <button type="submit" className="primary" disabled={busy}>{busy ? 'Enviando...' : 'Enviar código'}</button>
        </form>
      </> : <>
        <h2>Digite o código</h2>
        <p>{message} Verifique a caixa de entrada de <strong>{email}</strong>.</p>
        <form onSubmit={verify}>
          <label>Código de acesso<input className="login-code" inputMode="numeric" autoComplete="one-time-code" required pattern="\d{6}" maxLength="6" autoFocus value={code} onChange={e => setCode(e.target.value.replace(/\D/g, ''))} placeholder="000000"/></label>
          {error && <div className="error" role="alert">{error}</div>}
          <button type="submit" className="primary" disabled={busy}>{busy ? 'Verificando...' : 'Entrar'}</button>
          <div className="login-links">
            <button type="button" onClick={() => { setStep('email'); setCode(''); setError('') }}>Trocar e-mail</button>
            <button type="button" onClick={() => requestCode()} disabled={busy}>Reenviar código</button>
          </div>
        </form>
      </>}
    </div>
  </div>
}

function App({ user, onLogout, onExpired }) {
  const [purchases, setPurchases] = useState([])
  const [farms, setFarms] = useState([])
  const [overview, setOverview] = useState(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [filter, setFilter] = useState('ALL')
  const [farmFilter, setFarmFilter] = useState('ALL')
  const [search, setSearch] = useState('')
  // null, or { kind: 'purchase' | 'farm', item } where item is undefined for a new record.
  const [editing, setEditing] = useState(null)
  const isAdmin = user.role === 'ADMIN'
  const canEdit = user.role === 'ADMIN' || user.role === 'OPERADOR'
  const [view, setView] = useState(() => viewFor(window.location.hash, user.role))
  useEffect(() => {
    const sync = () => setView(viewFor(window.location.hash, user.role))
    window.addEventListener('hashchange', sync)
    return () => window.removeEventListener('hashchange', sync)
  }, [])

  async function load() {
    try {
      setError('')
      const [items, metrics, farmItems] = await Promise.all([readJson('/api/purchases'), readJson('/api/analytics/overview'), readJson('/api/farms')])
      setPurchases(items)
      setOverview(metrics)
      setFarms(farmItems)
    } catch (e) {
      if (e instanceof Unauthorized) return onExpired()
      setError('Serviços indisponíveis. Verifique o ambiente e tente novamente.')
    }
    finally { setLoading(false) }
  }
  useEffect(() => { load() }, [])
  const visible = useMemo(() => purchases.filter(item =>
    (filter === 'ALL' || item.status === filter) &&
    (farmFilter === 'ALL' || item.farm === farmFilter) &&
    `${item.title} ${item.farm} ${item.supplier || ''}`.toLocaleLowerCase('pt-BR').includes(search.toLocaleLowerCase('pt-BR'))
  ), [purchases, filter, farmFilter, search])
  const purchaseFarms = useMemo(() => [...new Set(purchases.map(item => item.farm))].sort((a, b) => a.localeCompare(b, 'pt-BR')), [purchases])

  async function saved() {
    setEditing(null)
    await load()
  }

  async function changeStatus(id, status) {
    try {
      await readJson(`/api/purchases/${id}/status`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ status }) })
      await load()
    } catch (e) { e instanceof Unauthorized ? onExpired() : setError(e.message) }
  }

  const maxDay = Math.max(1, ...(overview?.recentDays || []).map(item => item.count))
  const maxCategory = Math.max(1, ...(overview?.byCategory || []).map(item => Number(item.amount)))
  return <div className="shell">
    <aside className="sidebar">
      <div className="brand"><span className="brand-mark"><Icon name="leaf" size={23}/></span><span>intergado<small>GESTÃO RURAL</small></span></div>
      <div className="sidebar-caption">PAINEL DE CONTROLE</div>
      <nav aria-label="Navegação principal">
        <a className={view === 'dashboard' ? 'nav-active' : ''} href="#visao"><Icon name="grid"/> Visão geral</a>
        <a href="#fazendas"><Icon name="leaf"/> Fazendas</a>
        <a href="#solicitacoes"><Icon name="box"/> Solicitações</a>
        <a href="#indicadores"><Icon name="chart"/> Indicadores</a>
        {isAdmin && <a className={view === 'integrations' ? 'nav-active' : ''} href="#integracoes"><Icon name="plug"/> Integrações</a>}
        {isAdmin && <a className={view === 'users' ? 'nav-active' : ''} href="#usuarios"><Icon name="users"/> Usuários</a>}
        <a href="/swagger-ui/index.html" target="_blank" rel="noreferrer"><Icon name="box"/> API de cadastros</a>
        <a href="/q/swagger-ui" target="_blank" rel="noreferrer"><Icon name="chart"/> API de indicadores</a>
      </nav>
      <div className="sidebar-bottom"><div className="sidebar-dot"/> Ambiente de demonstração <small>Compras ilustrativas · saldos históricos</small></div>
    </aside>

    {view !== 'dashboard' ? <main className="content"><header className="topbar"><div className="breadcrumbs">Configuração <span>/</span> <strong>{view === 'users' ? 'Usuários' : 'Integrações'}</strong></div><div className="topbar-right"><span className="user-email" title={roleLabels[user.role]}>{user.email} · {roleLabels[user.role]}</span><button className="refresh logout" onClick={onLogout} title="Sair" aria-label="Sair"><Icon name="logout" size={17}/></button></div></header><div className="page">{view === 'users' ? <Users user={user} onExpired={onExpired}/> : <Integrations onExpired={onExpired}/>}</div></main> :
    <main id="visao" className="content">
      <header className="topbar"><div className="breadcrumbs">Operação <span>/</span> Fazendas <span>/</span> <strong>Visão geral</strong></div><div className="topbar-right"><span className="live-dot"/> Compras atualizadas ao abrir <button className="refresh" onClick={load} title="Atualizar dados" aria-label="Atualizar dados"><Icon name="refresh" size={17}/></button><span className="user-email" title={roleLabels[user.role]}>{user.email} · {roleLabels[user.role]}</span><button className="refresh logout" onClick={onLogout} title="Sair" aria-label="Sair"><Icon name="logout" size={17}/></button></div></header>
      <div className="page">
        <div className="page-heading"><div><div className="eyebrow">INTELIGÊNCIA OPERACIONAL</div><h1>Visão das fazendas</h1><p>Consulte o histórico do rebanho por fazenda e acompanhe as compras da aplicação.</p></div>{canEdit && <button className="primary" onClick={() => setEditing({ kind: 'purchase' })}><Icon name="plus" size={19}/> Nova solicitação</button>}</div>
        {error && <div className="error" role="alert">{error} <button onClick={load}>Tentar novamente</button></div>}
        {overview?.demoData && <div className="demo-note">As solicitações de compra são exemplos, não dados importados das planilhas. Bases criadas antes desta atualização podem conter a fazenda ilustrativa “Boa Vista”; esses registros foram preservados.</div>}
        <section id="fazendas" className="farms-section" aria-labelledby="farms-title">
          <div className="farms-heading"><div><div className="eyebrow">CADASTRO DE FAZENDAS</div><h2 id="farms-title">{farms.length} fazendas cadastradas</h2><p>Os quatro registros iniciais mostram saldos históricos das planilhas de 2021.{canEdit ? ' Cadastre novas fazendas e atualize os saldos por aqui.' : ''}</p></div>{canEdit ? <button className="secondary farm-new" onClick={() => setEditing({ kind: 'farm' })}><Icon name="plus" size={16}/> Nova fazenda</button> : <span className="small-tag">Somente leitura</span>}</div>
          <div className="farm-grid">{farms.map(farm => <article className="farm-card" key={farm.id}>
            <div className="farm-card-head"><h3>{farm.name}</h3><span>Data do saldo: {farm.balanceDate.split('-').reverse().join('/')}</span></div>
            <div className="farm-balance"><strong>{integer(farm.herdBalance)}</strong><span>cabeças · saldo registrado</span></div>
            <div className="farm-adepara">Saldo Adepará registrado: <strong>{integer(farm.adeparaBalance)}</strong></div>
            <div className="farm-sources" aria-label={`Fontes de ${farm.name}`}>{farm.sources.split(',').map(source => source.trim()).filter(Boolean).map(source => <span key={source}>{source}</span>)}</div>
            {canEdit && <div className="farm-actions"><button type="button" className="link-button" onClick={() => setEditing({ kind: 'farm', item: farm })} aria-label={`Editar ${farm.name}`}><Icon name="edit" size={14}/> Editar</button></div>}
          </article>)}</div>
          {farms.length === 0 && !loading && <p className="empty">Nenhuma fazenda cadastrada.</p>}
          <p className="farm-source-note">Os quatro saldos iniciais vieram de <code>01_Controle_gado.xlsx</code>, com datas diferentes. A comparação com Adepará precisa de conciliação. Novos valores são inseridos pela tela ou pela API; a importação automática das planilhas ainda não está disponível.</p>
        </section>
        <div className="eyebrow purchase-eyebrow">COMPRAS · DADOS DA APLICAÇÃO</div>
        <section className="metrics" aria-label="Indicadores principais">
          <Metric label="Solicitações" value={overview?.total} detail="Total registrado" icon="box" tone="green" loading={loading}/>
          <Metric label="Em andamento" value={overview?.active} detail="Aguardando conclusão" icon="refresh" tone="blue" loading={loading}/>
          <Metric label="Aguardando aprovação" value={overview?.awaitingApproval} detail="Precisam de decisão" icon="clock" tone="amber" loading={loading}/>
          <Metric label="Valor estimado" value={overview ? money(overview.estimatedAmount) : '—'} detail="Soma dos valores informados" icon="chart" tone="purple" loading={loading}/>
        </section>

        <section id="indicadores" className="insights">
          <div className="panel activity"><div className="panel-head"><div><h2>Atividade recente</h2><p>Solicitações recebidas nos últimos 7 dias</p></div><span className="small-tag">7 dias</span></div><div className="chart-bars">{(overview?.recentDays || []).map(day => <div className="chart-col" key={day.date}><span className="chart-value">{day.count || ''}</span><div className="chart-track"><div style={{height: `${Math.max(day.count ? 13 : 3, day.count / maxDay * 100)}%`}} /></div><span className="chart-label">{new Intl.DateTimeFormat('pt-BR', { weekday: 'short' }).format(new Date(day.date + 'T12:00:00')).replace('.', '')}</span></div>)}</div></div>
          <div className="panel category"><div className="panel-head"><div><h2>Por categoria</h2><p>Valor estimado das solicitações</p></div></div><div className="category-list">{(overview?.byCategory || []).map((group, i) => <div className="category-row" key={group.name}><div className="category-line"><span><i className={`category-dot dot-${i % 4}`}/>{group.name}</span><strong>{money(group.amount)}</strong></div><div className="progress"><span style={{width: `${Number(group.amount) / maxCategory * 100}%`}}/></div></div>)}{overview && !overview.byCategory.length && <p className="empty">Sem dados no período.</p>}</div></div>
        </section>

        <section id="solicitacoes" className="panel requests"><div className="panel-head request-head"><div><h2>Solicitações recentes</h2><p>Compras da aplicação; os exemplos não foram extraídos das planilhas.</p></div><div className="request-controls"><label className="search"><Icon name="search" size={17}/><input placeholder="Buscar solicitação" value={search} onChange={e => setSearch(e.target.value)} aria-label="Buscar solicitação"/></label><select value={farmFilter} onChange={e => setFarmFilter(e.target.value)} aria-label="Filtrar por fazenda"><option value="ALL">Todas as fazendas</option>{purchaseFarms.map(farm => <option key={farm} value={farm}>{farm}</option>)}</select><select value={filter} onChange={e => setFilter(e.target.value)} aria-label="Filtrar por status"><option value="ALL">Todos os status</option>{statusOrder.map(s => <option key={s} value={s}>{statusLabels[s]}</option>)}</select></div></div><div className="table-wrap"><table><thead><tr><th>SOLICITAÇÃO</th><th>FAZENDA</th><th>DATA</th><th>VALOR</th><th>STATUS</th><th>ATUALIZAR</th></tr></thead><tbody>{visible.map(item => <tr key={item.id}><td><span className="item-title">{item.title}</span><span className="item-sub">#{String(item.id).padStart(4, '0')} · {item.category}{item.demo ? ' · Exemplo' : ''}</span></td><td>{item.farm}</td><td>{date(item.requestedAt)}</td><td className="amount">{money(item.amount)}</td><td><span className={`badge badge-${item.status.toLowerCase()}`}>{statusLabels[item.status]}</span></td><td>{canEdit ? <div className="row-actions"><select className="status-select" value={item.status} onChange={e => changeStatus(item.id, e.target.value)} aria-label={`Atualizar status de ${item.title}`}>{statusOrder.map(s => <option key={s} value={s}>{statusLabels[s]}</option>)}</select><button type="button" className="icon-button" onClick={() => setEditing({ kind: 'purchase', item })} title="Editar solicitação" aria-label={`Editar ${item.title}`}><Icon name="edit" size={15}/></button></div> : <span className="muted" title="Seu perfil é somente leitura">—</span>}</td></tr>)}</tbody></table>{!loading && !visible.length && <div className="empty-table">Nenhuma solicitação encontrada.</div>}</div><div className="table-foot">Exibindo {visible.length} de {purchases.length} solicitações <span>Limite de 100 solicitações recentes</span></div></section>
        <footer>Intergado · Gestão rural</footer>
      </div>
    </main>}

    {editing?.kind === 'purchase' && <PurchaseForm purchase={editing.item} farms={farms} onClose={() => setEditing(null)} onSaved={saved} onExpired={onExpired}/>}
    {editing?.kind === 'farm' && <FarmForm farm={editing.item} onClose={() => setEditing(null)} onSaved={saved} onExpired={onExpired}/>}
  </div>
}

function Modal({ eyebrow, title, intro, onClose, children }) {
  useEffect(() => {
    const onKey = e => { if (e.key === 'Escape') onClose() }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])
  return <div className="modal-backdrop" onMouseDown={e => { if (e.target === e.currentTarget) onClose() }}><div className="modal" role="dialog" aria-modal="true" aria-labelledby="modal-title">
    <button className="close" onClick={onClose} aria-label="Fechar">×</button>
    <div className="eyebrow">{eyebrow}</div><h2 id="modal-title">{title}</h2><p>{intro}</p>
    {children}
  </div></div>
}

// Shared submit logic: the form fields become the JSON body, and errors stay inside the modal.
function useSave(onSaved, onExpired) {
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  async function save(request) {
    setSaving(true); setError('')
    try { await request(); await onSaved() }
    catch (e) { e instanceof Unauthorized ? onExpired() : setError(e.message) }
    finally { setSaving(false) }
  }
  return { saving, error, save }
}

function PurchaseForm({ purchase, farms, onClose, onSaved, onExpired }) {
  const { saving, error, save } = useSave(onSaved, onExpired)
  const farmNames = farms.map(f => f.name)
  // Old records may point to a farm that is not registered (text link); keep it selectable.
  const farmOptions = purchase && !farmNames.includes(purchase.farm) ? [...farmNames, purchase.farm] : farmNames
  const categoryOptions = purchase && !categories.includes(purchase.category) ? [...categories, purchase.category] : categories
  function submit(event) {
    event.preventDefault()
    const fields = Object.fromEntries(new FormData(event.currentTarget))
    fields.amount = Number(fields.amount)
    fields.supplier = fields.supplier.trim() || null
    save(() => purchase ? putJson(`/api/purchases/${purchase.id}`, fields) : postJson('/api/purchases', fields))
  }
  return <Modal onClose={onClose} eyebrow={purchase ? `SOLICITAÇÃO #${String(purchase.id).padStart(4, '0')}` : 'NOVA ENTRADA'}
    title={purchase ? 'Editar solicitação' : 'Nova solicitação'}
    intro={purchase ? `Status atual: ${statusLabels[purchase.status]}. O status muda pela tabela e não é alterado aqui.` : 'Registre uma necessidade de compra. Ela entrará como “Recebido”.'}>
    <form onSubmit={submit}>
      <label>O que precisa ser comprado?<input name="title" required maxLength="160" autoFocus defaultValue={purchase?.title} placeholder="Ex.: Peças para irrigação"/></label>
      <div className="form-grid">
        <label>Fazenda{farmOptions.length
          ? <select name="farm" required defaultValue={purchase?.farm ?? ''}>{!purchase && <option value="" disabled>Selecione</option>}{farmOptions.map(name => <option key={name} value={name}>{name}{farmNames.includes(name) ? '' : ' (não cadastrada)'}</option>)}</select>
          : <input name="farm" required maxLength="100" defaultValue={purchase?.farm} placeholder="Ex.: Santa Cruz"/>}</label>
        <label>Categoria<select name="category" required defaultValue={purchase?.category ?? categories[0]}>{categoryOptions.map(c => <option key={c}>{c}</option>)}</select></label>
      </div>
      <div className="form-grid">
        <label>Solicitante<input name="requester" required maxLength="100" defaultValue={purchase?.requester} placeholder="Nome ou equipe"/></label>
        <label>Valor estimado (R$)<input name="amount" type="number" min="0" step="0.01" defaultValue={purchase?.amount ?? 0} required/></label>
      </div>
      <label>Fornecedor (opcional)<input name="supplier" maxLength="120" defaultValue={purchase?.supplier ?? ''} placeholder="Ainda não definido"/></label>
      {error && <div className="error" role="alert">{error}</div>}
      <div className="modal-actions"><button type="button" className="secondary" onClick={onClose}>Cancelar</button><button type="submit" className="primary" disabled={saving}>{saving ? 'Salvando...' : purchase ? 'Salvar alterações' : 'Criar solicitação'}</button></div>
    </form>
  </Modal>
}

function FarmForm({ farm, onClose, onSaved, onExpired }) {
  const { saving, error, save } = useSave(onSaved, onExpired)
  const [name, setName] = useState(farm?.name ?? '')
  const renamed = farm && name.trim() && name.trim() !== farm.name
  function submit(event) {
    event.preventDefault()
    const fields = Object.fromEntries(new FormData(event.currentTarget))
    fields.herdBalance = Number(fields.herdBalance)
    fields.adeparaBalance = Number(fields.adeparaBalance)
    fields.sources = fields.sources.split(',').map(s => s.trim()).filter(Boolean).join(', ')
    save(() => farm ? putJson(`/api/farms/${farm.id}`, fields) : postJson('/api/farms', fields))
  }
  return <Modal onClose={onClose} eyebrow={farm ? 'CADASTRO DE FAZENDAS' : 'NOVA FAZENDA'} title={farm ? `Editar ${farm.name}` : 'Nova fazenda'}
    intro="Informe o saldo do rebanho na data indicada. Os valores ficam gravados como foram digitados; não há cálculo a partir de movimentos.">
    <form onSubmit={submit}>
      <label>Nome<input name="name" required maxLength="100" autoFocus value={name} onChange={e => setName(e.target.value)} placeholder="Ex.: Santa Cruz"/>
        {renamed && <small className="field-help">As solicitações de compra ligadas a “{farm.name}” passam para o novo nome.</small>}</label>
      <div className="form-grid">
        <label>Data do saldo<input name="balanceDate" type="date" required defaultValue={farm?.balanceDate ?? today()}/></label>
        <label>Saldo do rebanho (cabeças)<input name="herdBalance" type="number" min="0" step="1" required defaultValue={farm?.herdBalance ?? 0}/></label>
      </div>
      <div className="form-grid">
        <label>Saldo Adepará (cabeças)<input name="adeparaBalance" type="number" min="0" step="1" required defaultValue={farm?.adeparaBalance ?? 0}/></label>
        <label>Fontes (opcional)<input name="sources" maxLength="500" defaultValue={farm?.sources ?? ''} placeholder="Ex.: Controle de gado, Adepará"/>
          <small className="field-help">Separe por vírgulas.</small></label>
      </div>
      {error && <div className="error" role="alert">{error}</div>}
      <div className="modal-actions"><button type="button" className="secondary" onClick={onClose}>Cancelar</button><button type="submit" className="primary" disabled={saving}>{saving ? 'Salvando...' : farm ? 'Salvar alterações' : 'Cadastrar fazenda'}</button></div>
    </form>
  </Modal>
}

const dateTime = value => value ? new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value)) : '—'

function Users({ user, onExpired }) {
  const [items, setItems] = useState(null)
  const [roles, setRoles] = useState([])
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [busy, setBusy] = useState('')
  const [form, setForm] = useState({ email: '', name: '', role: 'LEITOR' })
  const fail = e => e instanceof Unauthorized ? onExpired() : setError(e.message)

  async function load() {
    try {
      setError('')
      const [list, roleList] = await Promise.all([readJson('/api/users'), readJson('/api/users/roles')])
      setItems(list); setRoles(roleList)
    } catch (e) { fail(e) }
  }
  useEffect(() => { load() }, [])

  async function create(event) {
    event.preventDefault()
    setBusy('new'); setError(''); setNotice('')
    try {
      const created = await postJson('/api/users', form)
      setItems(list => [...list, created])
      setForm({ email: '', name: '', role: form.role })
      setNotice(`${created.email} cadastrado. O acesso é pelo código enviado ao e-mail.`)
    } catch (e) { fail(e) }
    finally { setBusy('') }
  }

  async function change(target, changes, message) {
    setBusy(target.email); setError(''); setNotice('')
    try {
      const updated = await readJson(`/api/users/${encodeURIComponent(target.email)}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(changes) })
      setItems(list => list.map(u => u.email === updated.email ? updated : u))
      setNotice(message(updated))
    } catch (e) { fail(e) }
    finally { setBusy('') }
  }

  const active = items?.filter(u => u.active).length ?? 0
  return <>
    <div className="page-heading"><div><div className="eyebrow">CONFIGURAÇÃO</div><h1>Usuários</h1><p>Quem pode entrar no sistema e o que cada um pode fazer. O acesso é sempre pelo código enviado ao e-mail; não há senha.</p></div></div>
    <section className="panel users-roles" aria-label="Perfis de acesso">
      {roles.map(r => <div key={r.key}><strong>{r.label}</strong><span>{r.description}</span></div>)}
    </section>
    <section className="panel">
      <div className="panel-head"><div><h2>Cadastrar usuário</h2><p>O usuário entra com o código enviado ao e-mail cadastrado.</p></div></div>
      <form className="users-form" onSubmit={create}>
        <label>E-mail<input type="email" required maxLength="254" value={form.email} onChange={e => setForm({ ...form, email: e.target.value })} placeholder="nome@empresa.com.br"/></label>
        <label>Nome (opcional)<input maxLength="100" value={form.name} onChange={e => setForm({ ...form, name: e.target.value })}/></label>
        <label>Perfil<select value={form.role} onChange={e => setForm({ ...form, role: e.target.value })}>{roles.map(r => <option key={r.key} value={r.key}>{r.label}</option>)}</select></label>
        <button type="submit" className="primary" disabled={busy === 'new'}><Icon name="plus" size={17}/> {busy === 'new' ? 'Salvando...' : 'Cadastrar'}</button>
      </form>
    </section>
    {error && <div className="error" role="alert">{error} <button onClick={load}>Recarregar</button></div>}
    {notice && <div className="saved users-notice" role="status">{notice}</div>}
    <section className="panel requests">
      <div className="panel-head"><div><h2>{items ? `${items.length} usuário(s), ${active} ativo(s)` : 'Usuários'}</h2><p>Administradores iniciais vêm do <code>AUTH_ADMIN_EMAILS</code> e podem ser desativados aqui por outro administrador. Ninguém altera o próprio perfil, e sempre resta ao menos um administrador ativo.</p></div></div>
      {!items && !error && <p className="empty">Carregando...</p>}
      {items && <div className="table-wrap"><table><thead><tr><th>USUÁRIO</th><th>PERFIL</th><th>SITUAÇÃO</th><th>ÚLTIMO ACESSO</th><th>ALTERADO</th></tr></thead><tbody>
        {items.map(u => { const self = u.email === user.email; return <tr key={u.email} className={u.active ? '' : 'user-inactive'}>
          <td><span className="item-title">{u.name || u.email}{self && ' (você)'}</span><span className="item-sub">{u.name ? `${u.email} · ` : ''}{u.fromEnv ? <span className="source source-env" title="Criado a partir de AUTH_ADMIN_EMAILS">.env</span> : `Cadastrado por ${u.createdBy || '—'}`}</span></td>
          <td><select className="status-select" value={u.role} disabled={self || busy === u.email} title={self ? 'Peça a outro administrador para alterar o seu perfil' : undefined}
            onChange={e => change(u, { role: e.target.value }, x => `${x.email} agora é ${roleLabels[x.role]}.`)} aria-label={`Perfil de ${u.email}`}>
            {roles.map(r => <option key={r.key} value={r.key}>{r.label}</option>)}</select></td>
          <td><span className={`badge ${u.active ? 'badge-paid' : 'badge-inactive'}`}>{u.active ? 'Ativo' : 'Desativado'}</span>
            {!self && <button type="button" className="link-button" disabled={busy === u.email}
              onClick={() => change(u, { active: !u.active }, x => x.active ? `${x.email} pode entrar novamente.` : `${x.email} foi desativado e as sessões dele foram encerradas.`)}>{u.active ? 'Desativar' : 'Reativar'}</button>}</td>
          <td>{dateTime(u.lastLoginAt)}</td>
          <td><span className="item-sub">{dateTime(u.updatedAt || u.createdAt)}{u.updatedBy ? ` · ${u.updatedBy}` : ''}</span></td>
        </tr> })}
      </tbody></table></div>}
    </section>
  </>
}

const sourceLabels = { tela: 'Salvo na tela', '.env': 'Do .env', 'padrão': 'Padrão' }
const connectionLabels = { open: 'Conectado', connecting: 'Aguardando pareamento', close: 'Desconectado', not_created: 'Instância não criada', unknown: 'Estado desconhecido' }

function Integrations({ onExpired }) {
  const [items, setItems] = useState(null)
  const [error, setError] = useState('')
  async function load() {
    try { setError(''); setItems(await readJson('/api/integrations')) }
    catch (e) { e instanceof Unauthorized ? onExpired() : setError(e.message) }
  }
  useEffect(() => { load() }, [])
  return <>
    <div className="page-heading"><div><div className="eyebrow">CONFIGURAÇÃO</div><h1>Integrações</h1><p>Dados dos serviços externos. O valor salvo aqui tem prioridade sobre o <code>.env</code>; deixe um campo vazio para voltar ao valor do <code>.env</code>. Chaves e senhas ficam só no <code>.env</code>.</p></div></div>
    {error && <div className="error" role="alert">{error} <button onClick={load}>Tentar novamente</button></div>}
    {!items && !error && <p className="empty">Carregando...</p>}
    {items?.map(item => <IntegrationCard key={item.key} integration={item} onSaved={updated => setItems(list => list.map(i => i.key === updated.key ? updated : i))} onExpired={onExpired}/>)}
  </>
}

function IntegrationCard({ integration, onSaved, onExpired }) {
  const initial = () => Object.fromEntries(integration.fields.map(f => [f.name, ['PHONE_LIST', 'GROUP_LIST', 'CHAT_LIST'].includes(f.type) ? f.value.split(',').filter(Boolean).join('\n') : f.value]))
  const [values, setValues] = useState(initial)
  const [status, setStatus] = useState({ busy: false, message: '', error: '' })
  useEffect(() => { setValues(initial()) }, [integration])
  const set = (name, value) => setValues(v => ({ ...v, [name]: value }))

  async function save(event) {
    event.preventDefault()
    setStatus({ busy: true, message: '', error: '' })
    try {
      onSaved(await readJson(`/api/integrations/${integration.key}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(values) }))
      setStatus({ busy: false, message: 'Configuração salva.', error: '' })
    } catch (e) { e instanceof Unauthorized ? onExpired() : setStatus({ busy: false, message: '', error: e.message }) }
  }

  function input(field) {
    const value = values[field.name] ?? ''
    if (field.type === 'BOOLEAN') return <label className="switch"><input type="checkbox" checked={value === 'true'} onChange={e => set(field.name, String(e.target.checked))}/> {value === 'true' ? 'Ligado' : 'Desligado'}</label>
    if (field.type === 'GROUP_LIST') return <GroupPicker value={value} onChange={v => set(field.name, v)} onExpired={onExpired}/>
    if (field.type === 'CHAT_LIST') return <TelegramChatPicker value={value} onChange={v => set(field.name, v)} onExpired={onExpired}/>
    if (field.type === 'PHONE_LIST') return <textarea rows="3" value={value} onChange={e => set(field.name, e.target.value)} placeholder="5591999990000"/>
    if (field.type === 'STATUS_LIST') {
      const selected = value.split(',').filter(Boolean)
      const toggle = status => set(field.name, field.options.filter(o => o === status ? !selected.includes(o) : selected.includes(o)).join(','))
      return <div className="status-options">{field.options.map(o => <label key={o}><input type="checkbox" checked={selected.includes(o)} onChange={() => toggle(o)}/> {statusLabels[o]}</label>)}</div>
    }
    return <input value={value} inputMode={field.type === 'PHONE' ? 'numeric' : undefined} placeholder={field.type === 'PHONE' ? '5591999990000' : ''} onChange={e => set(field.name, e.target.value)}/>
  }

  return <section className="panel integration">
    <div className="panel-head"><div><h2>{integration.label}</h2><p>{integration.description}</p></div></div>
    <div className="integration-env">{integration.environment.map(env => <div key={env.envName}><span>{env.label}</span><code>{env.envName}</code><strong className={env.configured ? 'ok' : 'missing'}>{env.secret ? (env.configured ? 'Configurada' : 'Ausente no .env') : (env.value || 'Ausente no .env')}</strong></div>)}</div>
    <form className="integration-form" onSubmit={save}>
      {integration.fields.map(field => <div className="integration-field" key={field.name}>
        <div className="integration-label"><span>{field.label}</span><span className={`source source-${field.source === '.env' ? 'env' : field.source === 'tela' ? 'screen' : 'default'}`} title={`Variável ${field.envName}`}>{sourceLabels[field.source]}</span></div>
        {input(field)}
        <small>{field.help}</small>
      </div>)}
      {status.error && <div className="error" role="alert">{status.error}</div>}
      <div className="integration-actions">{status.message && <span className="saved">{status.message}</span>}<button type="submit" className="primary" disabled={status.busy}>{status.busy ? 'Salvando...' : 'Salvar'}</button></div>
    </form>
    {integration.key === 'evolution' && <EvolutionConnection onExpired={onExpired}/>}
    {integration.key === 'telegram' && <TelegramConnection onExpired={onExpired}/>}
  </section>
}

function GroupPicker({ value, onChange, onExpired }) {
  const [groups, setGroups] = useState(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const selected = value.split(/[\s,;]+/).filter(Boolean)
  async function load() {
    setBusy(true); setError('')
    try { setGroups(await readJson('/api/integrations/evolution/groups')) }
    catch (e) { e instanceof Unauthorized ? onExpired() : setError(e.message) }
    finally { setBusy(false) }
  }
  const toggle = id => onChange((selected.includes(id) ? selected.filter(s => s !== id) : [...selected, id]).join('\n'))
  return <div className="groups">
    <textarea rows="3" value={value} onChange={e => onChange(e.target.value)} placeholder="120363000000000000@g.us"/>
    <div className="groups-actions">
      <button type="button" className="secondary" disabled={busy} onClick={load}>{busy ? 'Buscando...' : groups ? 'Atualizar grupos' : 'Buscar grupos'}</button>
      {groups && <small>{groups.length ? `${groups.length} grupo(s) do WhatsApp conectado. Marque os que recebem mensagens.` : 'O WhatsApp conectado não participa de nenhum grupo.'}</small>}
    </div>
    {error && <div className="error" role="alert">{error}</div>}
    {groups?.length > 0 && <div className="group-options">{groups.map(g => <label key={g.id}>
      <input type="checkbox" checked={selected.includes(g.id)} onChange={() => toggle(g.id)}/>
      <span><strong>{g.subject || 'Sem nome'}</strong> <code>{g.id}</code>{g.size ? ` · ${g.size} participantes` : ''}{g.announce ? ' · só administradores enviam' : ''}</span>
    </label>)}</div>}
  </div>
}

const chatTypes = { private: 'conversa privada', group: 'grupo', supergroup: 'grupo', channel: 'canal' }

function TelegramChatPicker({ value, onChange, onExpired }) {
  const [chats, setChats] = useState(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const selected = value.split(/[\s,;]+/).filter(Boolean)
  async function load() {
    setBusy(true); setError('')
    try { setChats(await readJson('/api/integrations/telegram/chats')) }
    catch (e) { e instanceof Unauthorized ? onExpired() : setError(e.message) }
    finally { setBusy(false) }
  }
  const toggle = id => onChange((selected.includes(id) ? selected.filter(s => s !== id) : [...selected, id]).join('\n'))
  return <div className="groups">
    <textarea rows="3" value={value} onChange={e => onChange(e.target.value)} placeholder="-1001234567890"/>
    <div className="groups-actions">
      <button type="button" className="secondary" disabled={busy} onClick={load}>{busy ? 'Buscando...' : chats ? 'Atualizar conversas' : 'Buscar conversas'}</button>
      {chats && <small>{chats.length ? `${chats.length} conversa(s) vista(s) pelo bot nas últimas 24 horas. Marque as que recebem mensagens.` : 'Nenhuma conversa recente. Adicione o bot a um grupo ou mande /start a ele e busque de novo.'}</small>}
    </div>
    {error && <div className="error" role="alert">{error}</div>}
    {chats?.length > 0 && <div className="group-options">{chats.map(c => <label key={c.id}>
      <input type="checkbox" checked={selected.includes(c.id)} onChange={() => toggle(c.id)}/>
      <span><strong>{c.title || 'Sem nome'}</strong> <code>{c.id}</code> · {chatTypes[c.type] || c.type}</span>
    </label>)}</div>}
  </div>
}

function TelegramConnection({ onExpired }) {
  const [bot, setBot] = useState(null)
  const [deliveries, setDeliveries] = useState(null)
  const [busy, setBusy] = useState('')
  const [error, setError] = useState('')
  async function call(action, url, method = 'GET') {
    setBusy(action); setError('')
    try {
      const result = await readJson(url, { method })
      if (action === 'test') setDeliveries(result)
      else setBot(result)
    } catch (e) {
      if (e instanceof Unauthorized) onExpired()
      else { if (action === 'bot') setBot(null); setError(e.message) }
    }
    finally { setBusy('') }
  }
  useEffect(() => { call('bot', '/api/integrations/telegram/bot') }, [])
  return <div className="evolution">
    <div className="integration-label"><span>Bot do Telegram</span><span className={`badge ${bot ? 'badge-paid' : 'badge-awaiting_approval'}`}>{bot ? 'Conectado' : 'Sem conexão'}</span></div>
    {bot && <p><strong>{bot.name}</strong> · <a href={`https://t.me/${bot.username}`} target="_blank" rel="noreferrer">@{bot.username}</a>. Adicione o bot aos grupos que devem receber avisos.</p>}
    {error && <div className="error" role="alert">{error}</div>}
    <div className="evolution-actions">
      <button type="button" className="secondary" disabled={!!busy} onClick={() => call('bot', '/api/integrations/telegram/bot')}>{busy === 'bot' ? 'Verificando...' : 'Verificar bot'}</button>
      <button type="button" className="primary" disabled={!!busy} onClick={() => call('test', '/api/integrations/telegram/test', 'POST')}>{busy === 'test' ? 'Enviando...' : 'Enviar mensagem de teste'}</button>
    </div>
    {deliveries && <ul className="deliveries">{deliveries.map(d => <li key={d.number} className={d.sent ? 'ok' : 'missing'}>{d.number}: {d.sent ? 'enviada' : `falhou (${d.error})`}</li>)}</ul>}
  </div>
}

function EvolutionConnection({ onExpired }) {
  const [connection, setConnection] = useState(null)
  const [deliveries, setDeliveries] = useState(null)
  const [busy, setBusy] = useState('')
  const [error, setError] = useState('')
  async function call(action, url, method = 'GET') {
    setBusy(action); setError('')
    try {
      const result = await readJson(url, { method })
      if (action === 'test') setDeliveries(result)
      else setConnection(result)
    } catch (e) { e instanceof Unauthorized ? onExpired() : setError(e.message) }
    finally { setBusy('') }
  }
  useEffect(() => { call('state', '/api/integrations/evolution/connection') }, [])
  const state = connection?.state
  return <div className="evolution">
    <div className="integration-label"><span>Conexão do WhatsApp</span>{connection && <span className={`badge ${state === 'open' ? 'badge-paid' : 'badge-awaiting_approval'}`}>{connectionLabels[state] || state}</span>}</div>
    {connection?.pairingCode && <div className="pairing"><div><small>No celular: WhatsApp → Dispositivos conectados → Conectar com número de telefone. Digite:</small><strong>{connection.pairingCode}</strong></div>{connection.qrBase64 && <img src={connection.qrBase64} alt="QR Code para conectar o WhatsApp"/>}</div>}
    {connection && !connection.pairingCode && connection.qrBase64 && <div className="pairing"><small>Escaneie o QR Code em WhatsApp → Dispositivos conectados.</small><img src={connection.qrBase64} alt="QR Code para conectar o WhatsApp"/></div>}
    {error && <div className="error" role="alert">{error}</div>}
    <div className="evolution-actions">
      <button type="button" className="secondary" disabled={!!busy} onClick={() => call('state', '/api/integrations/evolution/connection')}>{busy === 'state' ? 'Verificando...' : 'Verificar conexão'}</button>
      {state !== 'open' && <button type="button" className="secondary" disabled={!!busy} onClick={() => call('connect', '/api/integrations/evolution/connect', 'POST')}>{busy === 'connect' ? 'Gerando...' : 'Conectar / gerar código'}</button>}
      {state === 'open' && <button type="button" className="secondary" disabled={!!busy} onClick={() => call('logout', '/api/integrations/evolution/logout', 'POST')}>Desconectar</button>}
      <button type="button" className="primary" disabled={!!busy} onClick={() => call('test', '/api/integrations/evolution/test', 'POST')}>{busy === 'test' ? 'Enviando...' : 'Enviar mensagem de teste'}</button>
    </div>
    {deliveries && <ul className="deliveries">{deliveries.map(d => <li key={d.number} className={d.sent ? 'ok' : 'missing'}>{d.number}: {d.sent ? 'enviada' : `falhou (${d.error})`}</li>)}</ul>}
  </div>
}

function Metric({ label, value, detail, icon, tone, loading }) {
  return <div className="metric"><div className={`metric-icon ${tone}`}><Icon name={icon} size={21}/></div><div className="metric-label">{label}</div><div className="metric-value">{loading ? '—' : value ?? '—'}</div><div className="metric-detail">{detail}</div></div>
}

createRoot(document.getElementById('root')).render(<Root />)
