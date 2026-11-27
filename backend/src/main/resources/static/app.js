'use strict';
(() => {
/* ============================================================ utilities */
const $ = (s, r = document) => r.querySelector(s);
const $$ = (s, r = document) => [...r.querySelectorAll(s)];
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const kg = n => n == null || n === '' ? 'n/d' : Number(n).toLocaleString('pt-PT', { maximumFractionDigits: 3 }) + ' kg';
const fmtDate = d => d ? new Date(d.length === 10 ? d + 'T00:00:00' : d).toLocaleDateString('pt-PT') : 'n/d';
const fmtTime = t => { const d = new Date(t), today = d.toDateString() === new Date().toDateString();
  return (today ? '' : d.toLocaleDateString('pt-PT', { day: '2-digit', month: '2-digit' }) + ' ') + d.toLocaleTimeString('pt-PT', { hour: '2-digit', minute: '2-digit' }); };
const shortName = p => { const s = String(p || ''); const i = s.indexOf(':'); return (i >= 0 ? s.slice(i + 1) : s).trim(); };
const productName = l => l?.productCode ? `${l.productCode} ${shortName(l.product)}` : '';
const norm = s => String(s || '').toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').trim();

const ROLES = ['VIEWER', 'OPERATOR', 'MANAGER', 'ADMIN'];
const ROLE_NAME = { VIEWER: 'Consulta', OPERATOR: 'Operador', MANAGER: 'Gestor', ADMIN: 'Administrador', LOGISTICS: 'Logística' };
let me = null;
const can = p => !!me && ROLES.indexOf(me.role) >= ROLES.indexOf(p); // raw-material room roles (logistics is outside this ladder)
const canFg = () => !!me && ['LOGISTICS', 'MANAGER', 'ADMIN'].includes(me.role);
const isFg = z => z === 'fg';
const canChange = z => isFg(z) ? canFg() : can('OPERATOR');
const sameArea = (a, b) => isFg(a) === isFg(b);

let toastTimer;
function toast(msg, bad) {
  const el = $('#toast'); el.textContent = msg; el.classList.toggle('bad', !!bad); el.classList.add('on');
  clearTimeout(toastTimer); toastTimer = setTimeout(() => el.classList.remove('on'), bad ? 6000 : 3500);
}

/* ============================================================ API */
const cookie = n => document.cookie.split('; ').find(c => c.startsWith(n + '='))?.slice(n.length + 1) || '';
async function csrf() { await fetch('/api/auth/csrf', { credentials: 'same-origin' }); }

async function api(method, url, body, retry = true) {
  const h = { Accept: 'application/json' };
  if (body !== undefined) h['Content-Type'] = 'application/json';
  if (method !== 'GET') {
    if (!cookie('XSRF-TOKEN')) await csrf();
    h['X-XSRF-TOKEN'] = decodeURIComponent(cookie('XSRF-TOKEN'));
  }
  let r;
  try {
    r = await fetch(url, { method: method, headers: h, credentials: 'same-origin', body: body === undefined ? undefined : JSON.stringify(body) });
  } catch {
    throw Object.assign(new Error('Sem ligação ao servidor. Confirma a rede e tenta de novo.'), { status: 0 });
  }
  if (r.status === 204) return null;
  const txt = await r.text();
  let d = null; try { d = txt ? JSON.parse(txt) : null; } catch { /* not a JSON response */ }
  if (!r.ok) {
    // 403 with no message = missing or stale CSRF token: fetch a new one and retry once
    if (r.status === 403 && !d?.error && retry && method !== 'GET') { await csrf(); return api(method, url, body, false); }
    const e = Object.assign(new Error(d?.error || `Erro ${r.status}`), { status: r.status });
    if (r.status === 401 && !url.startsWith('/api/auth/')) sessionEnded(e.message);
    throw e;
  }
  return d;
}

/* ============================================================ login */
const STEPS = ['f-login', 'f-mfa', 'f-pass', 'f-setup-mfa'];
function showStep(id, error = '') {
  $('#app').hidden = true; $('#footer').hidden = true; $('#login').hidden = false;
  STEPS.forEach(p => { $('#' + p).hidden = p !== id; });
  $('#login-error').textContent = error;
  const f = $('#' + id + ' input'); if (f) { f.value = id === 'f-login' ? f.value : ''; f.focus(); }
}
async function followStep(r) {
  switch (r.step) {
    case 'OK': me = r.me; $('#l-pass').value = ''; enterApp(); break;
    case 'MFA': showStep('f-mfa'); break;
    case 'CHANGE_PASSWORD': showStep('f-pass'); break;
    case 'SETUP_MFA': await prepareQr($('#qr'), $('#qr-key')); showStep('f-setup-mfa'); break;
  }
}
async function prepareQr(target, keyEl) {
  const d = await api('POST', '/api/auth/mfa/start');
  const q = qrcode(0, 'M'); q.addData(d.uri); q.make();
  target.innerHTML = q.createSvgTag({ cellSize: 5, margin: 2, scalable: true });
  keyEl.textContent = d.secret.match(/.{1,4}/g).join(' ');
}
function loginError(e) {
  if (e.status === 401 && /expirou/.test(e.message)) return showStep('f-login', e.message);
  $('#login-error').textContent = e.message;
}
function loginForm(id, fn) {
  $('#' + id).addEventListener('submit', async ev => {
    ev.preventDefault(); $('#login-error').textContent = '';
    const b = $('button', ev.target); b.disabled = true;
    try { await followStep(await fn()); } catch (e) { loginError(e); } finally { b.disabled = false; }
  });
}
loginForm('f-login', () => api('POST', '/api/auth/login', { username: $('#l-user').value, password: $('#l-pass').value }));
loginForm('f-mfa', () => api('POST', '/api/auth/mfa', { code: $('#m-code').value, remember: $('#m-remember').checked }));
loginForm('f-setup-mfa', () => api('POST', '/api/auth/mfa/confirm', { code: $('#c-code').value, remember: $('#c-remember').checked }));
loginForm('f-pass', () => {
  if ($('#p-new').value !== $('#p-conf').value) return Promise.reject(new Error('As duas palavras-passe não são iguais.'));
  return api('POST', '/api/auth/password', { newPassword: $('#p-new').value });
});

function sessionEnded(msg) {
  if (!me) return;
  me = null; closeEvents(); closeDialog();
  showStep('f-login', msg || 'A sessão terminou. Entra de novo.');
}

/* ============================================================ start-up and navigation */
async function boot() {
  try { await csrf(); me = await api('GET', '/api/auth/me'); enterApp(); }
  catch { showStep('f-login'); }
}
function enterApp() {
  $('#login').hidden = true; $('#app').hidden = false; $('#footer').hidden = false;
  $('#b-account').textContent = me.name;
  $('.brand span').textContent = can('VIEWER') ? 'Câmara C5' : 'Produto acabado';
  $('#account-info').textContent = `${me.username}, ${ROLE_NAME[me.role]}`;
  $$('[data-role]').forEach(el => { el.hidden = !can(el.dataset.role); });
  $('#nav [data-view=terminal]').hidden = !canTerminal();
  $('[data-action=enable-mfa]').hidden = me.mfaEnabled;
  connectEvents();
  goTo(viewFromHash());
}
const VIEWS = ['map', 'terminal', 'lots', 'products', 'packing', 'history', 'users'];
const canTerminal = () => can('OPERATOR') || canFg();
function viewFromHash() { const h = location.hash.slice(1); return VIEWS.includes(h) ? h : 'map'; }
let view = 'map';
function goTo(v) {
  if ((v === 'users' || v === 'packing') && !can('ADMIN')) v = 'map';
  if (v === 'terminal' && !canTerminal()) v = 'map';
  if (!can('VIEWER') && v !== 'terminal') v = 'map'; // logistics: only the finished-goods map (and the terminal)
  view = v;
  if (location.hash.slice(1) !== v) history.replaceState(null, '', '#' + v);
  VIEWS.forEach(x => { $('#v-' + x).hidden = x !== v; });
  $$('#nav button').forEach(b => b.toggleAttribute('aria-current', b.dataset.view === v));
  $$('#nav button[aria-current]').forEach(b => b.setAttribute('aria-current', 'page'));
  document.body.classList.toggle('in-terminal', v === 'terminal');
  ({ map: loadMap, terminal: loadTerminal, lots: loadLots, products: loadProducts, packing: loadBoard, history: loadHistory, users: loadUsers })[v]();
}
$('#nav').addEventListener('click', e => { const b = e.target.closest('[data-view]'); if (b) goTo(b.dataset.view); });
addEventListener('hashchange', () => { if (me) goTo(viewFromHash()); });

const menu = $('#account-menu');
$('#b-account').addEventListener('click', () => { menu.hidden = !menu.hidden; $('#b-account').setAttribute('aria-expanded', !menu.hidden); });
document.addEventListener('click', e => { if (!e.target.closest('.account')) menu.hidden = true; });
menu.addEventListener('click', async e => {
  const a = e.target.closest('[data-action]')?.dataset.action; if (!a) return;
  menu.hidden = true;
  if (a === 'logout') {
    try { await api('POST', '/api/auth/logout'); } catch { /* log out anyway */ }
    me = null; closeEvents(); await csrf(); showStep('f-login');
  }
  if (a === 'password') dialog(`<h2 id="dlg-t">Mudar palavra-passe</h2>
      <label for="d-current">Palavra-passe atual</label><input id="d-current" type="password" autocomplete="current-password" required>
      <label for="d-new">Nova (mínimo 12 caracteres)</label><input id="d-new" type="password" autocomplete="new-password" minlength="12" maxlength="64" required>
      <label for="d-conf">Repetir</label><input id="d-conf" type="password" autocomplete="new-password" required>`, 'Guardar', async () => {
    if ($('#d-new').value !== $('#d-conf').value) throw new Error('As duas palavras-passe não são iguais.');
    await api('POST', '/api/auth/password', { current: $('#d-current').value, newPassword: $('#d-new').value });
    toast('Palavra-passe alterada');
  });
  if (a === 'enable-mfa') {
    dialog(`<h2 id="dlg-t">Verificação em dois passos</h2><p>Lê o código QR com a app de autenticação e escreve o código que aparecer.</p>
      <div class="qr" id="d-qr"></div><p class="muted">Chave: <code id="d-key"></code></p>
      <label for="d-code">Código</label><input id="d-code" inputmode="numeric" autocomplete="one-time-code" maxlength="7" required>`, 'Ativar', async () => {
      await api('POST', '/api/auth/mfa/confirm', { code: $('#d-code').value });
      me.mfaEnabled = true; $('[data-action=enable-mfa]').hidden = true; toast('Verificação em dois passos ativada');
    });
    try { await prepareQr($('#d-qr'), $('#d-key')); } catch (er) { closeDialog(); toast(er.message, true); }
  }
});

/* ============================================================ dialog */
let onConfirm = null;
function dialog(html, buttonText, fn) {
  const c = $('.dialog-box');
  c.onclick = null;
  c.innerHTML = `<form id="d-form">${html}<p class="error" id="d-error" role="alert"></p>
    <div class="acts">${buttonText ? `<button class="btn pri" type="submit">${esc(buttonText)}</button>` : ''}<button class="btn" type="button" data-close>${buttonText ? 'Cancelar' : 'Fechar'}</button></div></form>`;
  onConfirm = fn; $('#dialog').hidden = false;
  ($('input,select,textarea', c) || $('button', c)).focus();
}
function closeDialog() { $('#dialog').hidden = true; onConfirm = null; }
$('#dialog').addEventListener('click', e => { if (e.target.id === 'dialog' || e.target.closest('[data-close]')) closeDialog(); });
$('#dialog').addEventListener('submit', async e => {
  e.preventDefault(); const b = $('button[type=submit]', e.target); if (b) b.disabled = true;
  try { if (onConfirm) await onConfirm(); closeDialog(); }
  catch (er) { $('#d-error').textContent = er.message; }
  finally { if (b) b.disabled = false; }
});

/* ============================================================ real time */
let source = null, reconnectTimer = null, dropped = false;
function connectEvents() {
  closeEvents();
  source = new EventSource('/api/events');
  source.addEventListener('connected', () => { setLive(true); if (dropped) { dropped = false; reloadSoon(); } });
  source.addEventListener('change', e => applyChange(JSON.parse(e.data)));
  source.onerror = async () => {
    setLive(false); dropped = true;
    if (source.readyState === EventSource.CLOSED) {
      closeEvents();
      try { await api('GET', '/api/auth/me'); reconnectTimer = setTimeout(connectEvents, 5000); }
      catch (e) { if (e.status === 401) sessionEnded(); else reconnectTimer = setTimeout(connectEvents, 5000); }
    }
  };
}
function closeEvents() { clearTimeout(reconnectTimer); if (source) { source.close(); source = null; } }
function setLive(ok) { const l = $('#live'); l.textContent = ok ? 'Em direto' : 'A religar…'; l.classList.toggle('off', !ok); }

let reloadTimer;
function reloadSoon() {
  clearTimeout(reloadTimer);
  reloadTimer = setTimeout(() => {
    if (view === 'map') loadMap();
    else if (view === 'terminal') loadMap().then(drawTerminal);
    else if (view === 'lots') loadLots(true);
    else if (view === 'products') loadProducts();
    M.stale = view !== 'map' && view !== 'terminal';
  }, 250);
}
const VERB = { STORE: 'arrumou', MOVE: 'moveu', SHIP: 'deu saída a', REMOVE: 'tirou', ADD_NOTE: 'reservou', REMOVE_NOTE: 'tirou a reserva de' };
function applyChange(a) {
  for (const p of a.locations || []) {
    const current = M.pos.get(p.code);
    if (!current || p.version >= current.version) { withErp(p.pallet); M.pos.set(p.code, p); }
  }
  if (a.movement && !M.movs.some(m => m.id === a.movement.id)) M.movs.unshift(a.movement);
  if (M.mode?.from && a.locations?.some(p => p.code === M.mode.from)) { M.mode = null; toast('A posição que estavas a mover foi alterada por outra pessoa.', true); }
  if (a.reload || a.lotsChanged) reloadSoon();
  else if (view === 'map') drawMap();
  if (a.by && a.by !== me?.username && a.movement) {
    const m = a.movement;
    toast(`${a.by} ${VERB[m.type] || 'alterou'} ${m.lot || shortCode(m.to || m.from || '')}${m.to && m.lot ? ' em ' + shortCode(m.to) : ''}`);
    (a.locations || []).forEach(p => $(`.cell[data-id="${p.code}"]`)?.classList.add('flash'));
  }
}

/* ============================================================ MAP */
const LISTS = [{ id: 'CORRIDOR', name: 'Corredor' }, { id: 'LEFTOVERS', name: 'Sobras' }];
const M = { zones: [], pos: new Map(), lists: [], movs: [], erp: new Map(), zone: 'f1', sel: null, mode: null, q: '', confirm: false, loaded: false, stale: false };
const AGE_BANDS = [[14, 'Até 14 dias'], [60, '15 a 60 dias'], [120, '61 a 120 dias'], [240, '121 a 240 dias'], [1e9, 'Mais de 240 dias, sai primeiro']];
const TODAY = (() => { const d = new Date(); d.setHours(0, 0, 0, 0); return d; })();
function lotDate(l) {
  let d = l?.productionDate ? new Date(l.productionDate + 'T00:00:00') : null;
  if (!d) { const m = /^L(\d{2})(\d{2})(\d{2})/.exec(l?.code || ''); if (m) d = new Date(2000 + +m[1], m[2] - 1, +m[3]); }
  return d && !isNaN(d) ? d : null;
}
function ageDays(l) { const d = lotDate(l); return d ? Math.round((TODAY - d) / 864e5) : null; }
const ageBand = a => a == null ? 2 : AGE_BANDS.findIndex(b => a <= b[0]);
const isList = z => LISTS.some(l => l.id === z);
// Finished goods: FEFO, the colour shows how long until expiry
const EXPIRY_BANDS = [[30, 'Validade até 30 dias, sai primeiro'], [90, '31 a 90 dias'], [180, '91 a 180 dias'], [365, '181 a 365 dias'], [1e9, 'Mais de 1 ano']];
const daysToExpiry = pl => pl?.expiryDate ? Math.round((new Date(pl.expiryDate + 'T00:00:00') - TODAY) / 864e5) : null;
const expiryBand = d => d == null ? 1 : 4 - EXPIRY_BANDS.findIndex(b => d <= b[0]);
const shortDate = d => d ? new Date(d + 'T00:00:00').toLocaleDateString('pt-PT', { day: '2-digit', month: '2-digit', year: '2-digit' }) : '';
const palletName = pl => pl.lot || (pl.sscc ? '…' + pl.sscc.slice(-9) : 'Palete ' + pl.id);
// ERP stock (per lot): gives a product to pallets without one and is used to check the map
const erpOf = pl => pl?.lot ? M.erp.get(pl.lot.toUpperCase()) : null;
function withErp(pl) {
  const e = erpOf(pl);
  if (pl && pl.noData && e?.productCode) Object.assign(pl, { productCode: e.productCode, product: e.product, noData: false, productFromErp: true });
  return pl;
}

async function loadMap() {
  try {
    const d = await api('GET', '/api/map');
    M.zones = d.zones; M.pos = new Map(d.locations.map(p => [p.code, p]));
    M.erp = new Map((d.erpFgStock || []).map(e => [e.lot, e]));
    M.lists = d.lists; M.movs = d.movements;
    for (const p of M.pos.values()) withErp(p.pallet);
    if (!M.loaded) { M.zone = d.zones[0]?.id || 'f1'; M.loaded = true; }
    if (M.sel && !M.pos.has(M.sel)) M.sel = null;
    M.stale = false;
    drawMap();
  } catch (e) { if (e.status !== 401) toast(e.message, true); }
}
function zoneLocations(z) { return [...M.pos.values()].filter(p => p.zone === z).sort((a, b) => a.rowNo - b.rowNo || a.colNo - b.colNo); }
// The location code is the visible name: R514a, R521c (raw material), R324 (finished goods).
const shortCode = c => c || '';
const locationGroup = c => c.replace(/[a-c]$/, ''); // R514a -> R514 (the location with all its slots)
// "R514" finds R514a and R514b; "r514b" finds R514b
const isLocation = (p, c) => { const u = p.code.toUpperCase(); return u === c || (u.length === c.length + 1 && u.startsWith(c) && /[A-C]$/.test(u)); };
// careful: these take a 2nd parameter (q). In .filter/.find always use x => matches(x), or the index is passed as q.
function matches(p, q = M.q) {
  if (!q) return false; const l = p.lot, pl = p.pallet;
  return norm([p.code, l?.code, l?.product, l?.productCode, l?.notes, p.note, pl?.lot, pl?.sscc, pl?.gtin, pl?.product, pl?.productCode, pl?.notes, erpOf(pl)?.notes].join(' ')).includes(q);
}
function lotMatches(l, q = M.q) { return !q || norm([l.code, l.product, l.productCode, l.notes].join(' ')).includes(q); }

function drawMap() {
  drawZones();
  const m = M.mode;
  $('#modebar').innerHTML = m ? `<div class="modebar"><span>${m.type === 'move' ? `A mover ${esc(m.lot)} de ${shortCode(m.from)}.` : `A arrumar ${esc(m.lot)}.`} Toca numa posição livre; podes mudar de andar.</span><button class="btn" data-action="cancel">Cancelar</button></div>` : '';
  if (isList(M.zone)) drawList(); else drawGrid();
  drawSide();
}
function drawZones() {
  const tabs = M.zones.map(z => {
    const ps = zoneLocations(z.id), occ = ps.filter(p => p.lot || p.pallet).length;
    return { id: z.id, name: z.name, n: M.q ? ps.filter(x => matches(x)).length + ' encontrados' : `${occ}/${ps.length}` };
  }).concat((can('VIEWER') ? LISTS : []).map(l => {
    const ls = M.lists.filter(x => x.holding === l.id);
    return { id: l.id, name: l.name, n: M.q ? ls.filter(x => lotMatches(x)).length + ' encontrados' : ls.length + ' lotes' };
  }));
  $('#zones').innerHTML = tabs.map(t => `<button role="tab" aria-selected="${t.id === M.zone}" data-zone="${t.id}">${esc(t.name)}<span class="n">${t.n}</span></button>`).join('');
}
function drawGrid() {
  const z = M.zones.find(x => x.id === M.zone); if (!z) return;
  const ps = zoneLocations(z.id);
  const groups = [];
  for (const p of ps) { let g = groups.find(x => x.label === p.groupLabel); if (!g) groups.push(g = { label: p.groupLabel, min: p.rowNo, max: p.rowNo }); g.max = Math.max(g.max, p.rowNo); g.min = Math.min(g.min, p.rowNo); }
  let h = '<div class="grid" id="grid-map">' + z.columnLabels.map((c, i) => `<div class="col-head" data-r="1" data-c="${i + 1}">${esc(c)}</div>`).join('');
  for (const p of ps) {
    const g = groups.find(x => x.label === p.groupLabel), groupTop = p.rowNo === g.min && groups.indexOf(g) > 0;
    h += cellHtml(p, groupTop);
  }
  $('#map').innerHTML = h + '</div>';
  const grid = $('#grid-map'); grid.style.setProperty('--n', z.columnLabels.length);
  $$('[data-r]', grid).forEach(el => { el.style.gridRow = el.dataset.s ? `${el.dataset.r} / span ${el.dataset.s}` : el.dataset.r; el.style.gridColumn = el.dataset.c; });
}
function cellHtml(p, groupTop) {
  const cls = ['cell']; const l = p.lot; let body = `<span class="code">${shortCode(p.code)}</span>`, ariaLabel;
  if (p.pallet) {
    const pl = p.pallet, d = daysToExpiry(pl);
    cls.push(pl.noData ? 'no-data' : 'g' + expiryBand(d)); if (pl.notes) cls.push('has-notes');
    body += `<span class="lot">${esc(palletName(pl))}</span><span class="meta">${pl.noData ? 'Sem dados' : esc(pl.productCode + '  ' + (pl.expiryDate ? shortDate(pl.expiryDate) : 's/ validade'))}</span>`;
    ariaLabel = `${shortCode(p.code)}, palete ${palletName(pl)}${pl.noData ? ', sem dados' : `, ${pl.productCode}, validade ${fmtDate(pl.expiryDate)}`}`;
  } else if (l) {
    const a = ageDays(l);
    cls.push(l.noData ? 'no-data' : 'g' + ageBand(a)); if (l.notes) cls.push('has-notes');
    body += `<span class="lot">${esc(l.code)}</span><span class="meta">${l.noData ? 'Sem dados' : esc(l.productCode + '  ' + kg(l.weightKg))}</span>`;
    ariaLabel = `${shortCode(p.code)}, lote ${l.code}${l.noData ? ', sem dados' : `, ${productName(l)}, ${a} dias`}`;
  } else if (p.note) {
    cls.push('noted'); body += `<span class="lot">${esc(p.note)}</span><span class="meta">Nota</span>`; ariaLabel = `${shortCode(p.code)}, nota: ${p.note}`;
  } else {
    cls.push('empty'); body += '<span class="lot">Livre</span>'; ariaLabel = `${shortCode(p.code)}, livre`; if (M.mode && sameArea(M.mode.zone, p.zone)) cls.push('target');
  }
  if (M.sel === p.code || M.mode?.from === p.code) cls.push('sel');
  if (M.q) cls.push(matches(p) ? 'hit' : 'dim');
  if (groupTop) cls.push('gtop');
  return `<button class="${cls.join(' ')}" data-id="${p.code}" data-r="${p.rowNo + 1}" data-c="${p.colNo}" aria-label="${esc(ariaLabel)}">${body}</button>`;
}
function drawList() {
  const list = LISTS.find(l => l.id === M.zone);
  const ls = M.lists.filter(x => x.holding === list.id && lotMatches(x));
  $('#map').innerHTML = `<div class="list-head"><h2>${list.name}</h2>
    ${can('OPERATOR') ? `<form id="f-list"><div><label for="list-scan">Ler ou escrever lote</label><input id="list-scan" autocomplete="off" spellcheck="false" placeholder="L261121000009"></div><button class="btn pri">Adicionar</button></form>` : ''}</div>
    <div class="scroll"><table class="table stack"><thead><tr><th>Lote</th><th>Produto</th><th class="num">Peso</th><th>Produção</th><th class="num">Dias</th><th></th></tr></thead><tbody>
    ${ls.length ? ls.map(l => `<tr data-lot="${esc(l.code)}"><td class="code-col" data-l="Lote">${esc(l.code)}</td><td data-l="Produto">${l.noData ? '<span class="tag orange">Sem dados</span>' : esc(productName(l))}</td>
      <td class="num" data-l="Peso">${kg(l.weightKg)}</td><td data-l="Produção">${fmtDate(l.productionDate)}</td><td class="num" data-l="Dias">${ageDays(l) ?? ''}</td>
      <td data-l="">${can('OPERATOR') ? `<button class="btn" data-action="store-from-list">Arrumar</button> <button class="btn" data-action="remove-from-list">Tirar</button>` : ''}</td></tr>`).join('')
      : `<tr class="no-rows"><td colspan="6">${M.q ? 'Nenhum lote corresponde à pesquisa.' : `Não há lotes em ${list.name}.`}</td></tr>`}</tbody></table></div>`;
}

/* side panel */
function drawSide() {
  const s = $('#side');
  s.innerHTML = (M.sel ? '' : '<div class="close-panel"><button class="btn" data-action="close">Fechar painel</button></div>')
    + (M.sel ? locationDetail(M.pos.get(M.sel)) : zoneSummary()) + erpSection() + legendSection();
  // 1024 screen: the panel is a drawer over the map; it opens when a location is picked (not while moving)
  const open = !!((M.sel && !M.mode) || M.panel);
  document.body.classList.toggle('panel-open', open);
  $('#b-panel').setAttribute('aria-expanded', open);
  const scan = $('#scan', s); if (scan && !M.mode) scan.focus({ preventScroll: true });
  if (M.sel) loadProductsIfMissing();
}
function zoneSummary() {
  if (isList(M.zone)) {
    const ls = M.lists.filter(x => x.holding === M.zone); const t = ls.reduce((s, l) => s + (+l.weightKg || 0), 0);
    return `<section><h2>${LISTS.find(l => l.id === M.zone).name}</h2><p class="big"><b>${ls.length}</b>lotes</p><dl><dt>Peso</dt><dd>${kg(t)}</dd></dl>
      <p class="muted">Lista de consulta. Para pôr um lote aqui, lê-o no campo acima ou liberta-o de uma posição escolhendo ${LISTS.find(l => l.id === M.zone).name}.</p></section>`;
  }
  if (isFg(M.zone)) return fgSummary();
  const z = M.zones.find(x => x.id === M.zone), ps = zoneLocations(M.zone);
  const occ = ps.filter(p => p.lot).length, res = ps.filter(p => p.note).length;
  let total = 0, n = 0, oldest = null, noData = 0;
  for (const p of M.pos.values()) { if (!p.lot) continue; n++; total += +p.lot.weightKg || 0; if (p.lot.noData) noData++; const a = ageDays(p.lot); if (a != null && (!oldest || a > oldest.a)) oldest = { p, a }; }
  return `<section><h2>${esc(z?.name || '')}</h2><p class="big"><b>${occ}</b>de ${ps.length} posições ocupadas</p>
    <div class="meter"><i data-w="${ps.length ? occ / ps.length * 100 : 0}"></i></div><p class="muted">${res} com nota, ${ps.length - occ - res} livres</p>
    <dl><dt>Nos andares</dt><dd>${kg(total)} em ${n} lotes</dd>
    ${oldest ? `<dt>Mais antigo</dt><dd><button class="link" data-go="${oldest.p.code}">${esc(oldest.p.lot.code)} em ${shortCode(oldest.p.code)}</button>, ${oldest.a} dias</dd>` : ''}
    ${noData ? `<dt>Sem dados</dt><dd>${noData} lotes a laranja</dd>` : ''}</dl>
    <p class="muted">${can('OPERATOR') ? 'Toca numa posição para arrumar, mover ou libertar.' : 'Toca numa posição para ver o lote.'}</p></section>`;
}
function fgSummary() {
  const ps = zoneLocations('fg'), withPallet = ps.filter(p => p.pallet), res = ps.filter(p => p.note).length;
  let totalKg = 0, boxCount = 0, upcoming = null, noData = 0, expired = 0;
  for (const p of withPallet) {
    const pl = p.pallet, d = daysToExpiry(pl); totalKg += +pl.weightKg || 0; boxCount += pl.boxes || 0; if (pl.noData) noData++;
    if (d != null) { if (d < 0) expired++; if (!upcoming || d < upcoming.d) upcoming = { p, d }; }
  }
  return `<section><h2>Produto acabado</h2><p class="big"><b>${withPallet.length}</b>de ${ps.length} lugares ocupados</p>
    <div class="meter"><i data-w="${ps.length ? withPallet.length / ps.length * 100 : 0}"></i></div><p class="muted">${res} com nota, ${ps.length - withPallet.length - res} livres</p>
    <dl><dt>Total</dt><dd>${boxCount} caixas, ${kg(totalKg)}</dd>
    ${upcoming ? `<dt>Sai primeiro</dt><dd><button class="link" data-go="${upcoming.p.code}">${esc(palletName(upcoming.p.pallet))} em ${upcoming.p.code}</button>, ${upcoming.d < 0 ? `expirou há ${-upcoming.d} dias` : `validade em ${upcoming.d} dias`}</dd>` : ''}
    ${expired ? `<dt>Expiradas</dt><dd><b>${expired} paletes</b></dd>` : ''}
    ${noData ? `<dt>Sem dados</dt><dd>${noData} paletes a laranja</dd>` : ''}</dl>
    <p class="muted">${canFg() ? 'Toca num lugar livre e lê a etiqueta da palete.' : 'Toca num lugar para ver a palete.'}</p></section>`;
}
function palletDetail(p, head, whoLine) {
  const pl = p.pallet, d = daysToExpiry(pl), canEdit = canFg();
  return `<section>${head}<p class="lotbig">${esc(pl.lot || 'Sem lote')}</p>
    ${pl.noData ? `<p class="warning">Produto desconhecido${pl.gtin ? ` (o GTIN ${esc(pl.gtin)} não está em nenhum produto)` : ''}. Fica a laranja até escolheres o produto em "Corrigir dados"${pl.gtin && can('MANAGER') ? ', ou até pores este GTIN no produto certo em Produtos' : ''}.</p>` : ''}
    ${M.erp.size && pl.lot && !erpOf(pl) ? '<p class="warning">Este lote não está no último stock do ERP. Se a palete já foi expedida, liberta o lugar.</p>' : ''}
    <dl>${pl.noData ? '' : `<dt>Produto</dt><dd><b>${esc(pl.productCode)}</b> - ${esc(pl.product)}${pl.productFromErp ? ' <span class="muted">(do ERP)</span>' : ''}</dd>`}
    <dt>Validade</dt><dd>${pl.expiryDate ? fmtDate(pl.expiryDate) + (d < 0 ? `, <b>expirou há ${-d} dias</b>` : `, faltam ${d} dias`) : 'n/d'}</dd>
    <dt>Produção</dt><dd>${fmtDate(pl.productionDate)}</dd><dt>Caixas</dt><dd>${pl.boxes ?? 'n/d'}</dd><dt>Peso</dt><dd>${kg(pl.weightKg)}</dd>
    ${pl.sscc ? `<dt>SSCC</dt><dd>${esc(pl.sscc)}</dd>` : ''}${pl.gtin ? `<dt>GTIN</dt><dd>${esc(pl.gtin)}</dd>` : ''}
    ${erpOf(pl) ? `<dt>No ERP</dt><dd>${esc(erpOf(pl).quantity)} <span class="muted">(lote todo)</span>${erpOf(pl).notes ? `<br>${esc(erpOf(pl).notes)}` : ''}</dd>` : ''}</dl>
    ${canEdit ? `<details ${pl.noData ? 'open' : ''}><summary>Corrigir dados</summary>${palletForm(pl)}</details>
    <label for="notes-input">Observações</label><textarea id="notes-input" rows="2" maxlength="500">${esc(pl.notes)}</textarea>
    <div class="acts"><button class="btn pri" data-action="move">Mover</button><button class="btn" data-action="save-notes">Guardar observação</button><button class="btn warn" data-action="release">Libertar lugar</button></div>
    ${M.confirm ? `<div class="confirm"><p>A palete ${esc(palletName(pl))} foi expedida? Para a mudar de lugar usa Mover.</p><div class="acts">
      <button class="btn warn" data-release="SHIPPED">Foi expedida</button>
      <button class="btn" data-action="cancel-confirm">Cancelar</button></div></div>` : ''}` : (pl.notes ? `<p>${esc(pl.notes)}</p>` : '')}${whoLine}</section>`;
}
function palletForm(pl) {
  return `<form id="f-pallet" data-id="${pl.id}" data-version="${pl.version}"><label for="fp-product">Produto</label>
    <select id="fp-product"><option value="">${pl.noData ? 'Escolhe…' : 'Manter (' + esc(pl.productCode) + ')'}</option>${(P.list || []).filter(x => x.active).map(x => `<option value="${esc(x.code)}">${esc(x.code)} - ${esc(shortName(x.description))}</option>`).join('')}</select>
    <label for="fp-lot">Lote</label><input id="fp-lot" maxlength="30" value="${esc(pl.lot)}">
    <label for="fp-boxes">Caixas</label><input id="fp-boxes" inputmode="numeric" value="${pl.boxes ?? ''}">
    <label for="fp-kg">Peso (kg)</label><input id="fp-kg" inputmode="decimal" value="${pl.weightKg ?? ''}">
    <label for="fp-expiry">Validade</label><input id="fp-expiry" type="date" value="${pl.expiryDate || ''}">
    <div class="acts"><button class="btn pri">Guardar dados</button></div></form>`;
}
function locationDetail(p) {
  if (!p) return '';
  const head = `<div class="dhead"><h2>${shortCode(p.code)}</h2><button class="x" data-action="close" aria-label="Fechar">×</button></div>`;
  const whoLine = p.updatedBy ? `<p class="note">Última alteração: ${esc(p.updatedBy)}, ${fmtTime(p.updatedAt)}</p>` : '';
  const l = p.lot;
  if (p.pallet) return palletDetail(p, head, whoLine);
  if (l) {
    const a = ageDays(l);
    return `<section>${head}<p class="lotbig">${esc(l.code)}</p>
      ${l.noData ? `<p class="warning">Este lote não está na base de dados. Fica a laranja até ser criado com produto e peso.</p>${can('MANAGER') ? completeForm(l) : ''}` : ''}
      <dl>${l.noData ? '' : `<dt>Produto</dt><dd><b>${esc(l.productCode)}</b> - ${esc(l.product)}</dd><dt>Peso</dt><dd>${kg(l.weightKg)}</dd>`}
      <dt>Produção</dt><dd>${lotDate(l) ? lotDate(l).toLocaleDateString('pt-PT') + `, há ${a} dias` : 'n/d'}</dd></dl>
      ${can('OPERATOR') ? `<label for="notes-input">Observações</label><textarea id="notes-input" rows="2" maxlength="500">${esc(l.notes)}</textarea>
      <div class="acts"><button class="btn pri" data-action="move">Mover</button><button class="btn" data-action="save-notes">Guardar observação</button><button class="btn warn" data-action="release">Libertar posição</button></div>
      ${M.confirm ? `<div class="confirm"><p>Para onde vai o lote ${esc(l.code)}?</p><div class="acts">
        <button class="btn warn" data-release="SHIPPED">Saiu da câmara</button><button class="btn" data-release="CORRIDOR">Corredor</button>
        <button class="btn" data-release="LEFTOVERS">Sobras</button>
        <button class="btn" data-action="cancel-confirm">Cancelar</button></div></div>` : ''}` : (l.notes ? `<p>${esc(l.notes)}</p>` : '')}
      <p class="note"><button class="link" data-history="${esc(l.code)}">Ver o percurso deste lote</button></p>${whoLine}</section>`;
  }
  if (p.note) return `<section>${head}<p class="lotbig">${esc(p.note)}</p><p class="muted">Nota na posição, sem lote.</p>
      ${canChange(p.zone) ? '<div class="acts"><button class="btn" data-action="remove-note">Apagar nota</button></div>' : ''}${whoLine}</section>`;
  if (!canChange(p.zone)) return `<section>${head}<p class="muted">Lugar livre.</p>${whoLine}</section>`;
  const isFgZone = isFg(p.zone);
  return `<section>${head}<form id="f-store"><label for="scan">${isFgZone ? 'Ler a etiqueta da palete, ou escrever lote ou texto' : 'Ler ou escrever lote ou texto'}</label>
      <input id="scan" autocomplete="off" spellcheck="false" placeholder="${isFgZone ? 'Etiqueta GS1, lote ou texto' : 'L261121000009 ou texto'}" maxlength="300">
      <div class="acts"><button class="btn pri">Guardar aqui</button></div></form>
      <p class="note">${isFgZone ? 'A etiqueta GS1 traz SSCC, produto (GTIN), lote, validade, caixas e peso.' : 'Um código de lote fica arrumado aqui.'} Qualquer outro texto (ex.: Casa do Bacalhau) fica como nota. O leitor do terminal carrega Enter sozinho.</p>${whoLine}</section>`;
}
function completeForm(l) {
  return `<form id="f-complete" data-lot="${esc(l.code)}"><label for="cp-product">Produto</label>
    <select id="cp-product" required><option value="">Escolhe…</option>${(P.list || []).filter(x => x.active).map(x => `<option value="${esc(x.code)}">${esc(x.code)} - ${esc(shortName(x.description))}</option>`).join('')}</select>
    <label for="cp-kg">Peso (kg)</label><input id="cp-kg" inputmode="decimal" value="${l.weightKg ?? ''}">
    <div class="acts"><button class="btn pri">Completar lote</button></div></form>`;
}
function erpSection() {
  if (!isFg(M.zone)) return '';
  const buttonText = can('MANAGER') ? '<p class="note">Importa em Lotes, Importar stock, escolhendo "Stock de produto acabado".</p>' : '';
  if (!M.erp.size) return `<section><h3>Stock do ERP</h3><p class="muted">Ainda não há stock do ERP. Com ele, a app diz que lotes faltam no mapa e que paletes já não estão no ERP.</p>${buttonText}</section>`;
  const inApp = new Set([...M.pos.values()].map(p => p.pallet).filter(pl => pl?.lot).map(pl => pl.lot.toUpperCase()));
  const noPallet = [...M.erp.values()].filter(e => !inApp.has(e.lot));
  const missing = zoneLocations('fg').filter(p => p.pallet?.lot && !erpOf(p.pallet));
  const occurredAt = [...M.erp.values()][0].importedAt;
  return `<section><h3>Stock do ERP <span class="muted">${M.erp.size} lotes</span></h3><p class="note">Importado ${fmtTime(occurredAt)}.</p>
    <details><summary>No ERP, sem palete no mapa (${noPallet.length})</summary>${noPallet.length ? `<ul class="plist">${noPallet.map(e => `<li><button disabled><span class="pl">${esc(e.lot)}</span><span>${esc((e.productCode || '') + ' ' + shortName(e.product || ''))}</span><span class="kg">${esc(e.quantity || '')}</span></button></li>`).join('')}</ul>` : '<p class="muted">Todos os lotes do ERP têm palete.</p>'}</details>
    <details><summary>No mapa, fora do ERP (${missing.length})</summary>${missing.length ? `<ul class="plist">${missing.map(p => `<li><button data-go="${p.code}"><span class="pl">${esc(p.pallet.lot)}</span><span>${esc(shortCode(p.code))}</span><span class="kg"></span></button></li>`).join('')}</ul>` : '<p class="muted">Todas as paletes do mapa estão no ERP.</p>'}</details>
    ${buttonText}</section>`;
}
function legendSection() {
  if (isFg(M.zone)) return `<section><h3>Cores</h3><ul class="legend">${EXPIRY_BANDS.map((b, i) => `<li><span class="sw g${4 - i}"></span>${b[1]}</li>`).join('')}
    <li><span class="sw no-data"></span>Produto desconhecido</li><li><span class="sw noted"></span>Nota (texto livre)</li><li><span class="sw empty"></span>Livre</li></ul>
    <p class="note">FEFO: sai primeiro o que expira primeiro. Paletes sem validade ficam azul claro. Um ponto no canto indica observações.</p></section>`;
  return `<section><h3>Cores</h3><ul class="legend">${AGE_BANDS.map((b, i) => `<li><span class="sw g${i}"></span>${b[1]}</li>`).join('')}
    <li><span class="sw no-data"></span>Lote sem dados na base</li><li><span class="sw noted"></span>Nota (texto livre)</li><li><span class="sw empty"></span>Livre</li></ul>
    <p class="note">A idade conta a partir da data de produção do lote. Um ponto no canto indica que o lote tem observações.</p></section>`;
}
new MutationObserver(() => $$('.meter i[data-w]').forEach(i => { i.style.width = i.dataset.w + '%'; i.removeAttribute('data-w'); }))
  .observe(document.body, { childList: true, subtree: true });

/* map actions */
async function runMapAction(promise, ok) {
  try {
    const r = await promise;
    for (const p of [].concat(r || [])) if (p?.code && p.zone) M.pos.set(p.code, p);
    if (ok) toast(ok);
    return r;
  } catch (e) {
    if (e.status === 409) { toast(e.message, true); await loadMap(); }
    else if (e.status !== 401) toast(e.message, true);
    throw e;
  } finally { drawMap(); }
}
function selectLocation(id) {
  M.zone = M.pos.get(id)?.zone || M.zone; M.sel = id; M.confirm = false; drawMap();
  const el = $(`.cell[data-id="${id}"]`); if (el) { el.scrollIntoView({ block: 'nearest', inline: 'center' }); }
  if (innerWidth <= 1100) $('#side').scrollIntoView({ block: 'start' });
}
$('#zones').addEventListener('click', e => { const b = e.target.closest('[data-zone]'); if (!b) return; M.zone = b.dataset.zone; if (M.sel && M.pos.get(M.sel)?.zone !== M.zone) M.sel = null; drawMap(); });
$('#map').addEventListener('click', async e => {
  const c = e.target.closest('.cell');
  if (c) {
    const id = c.dataset.id, p = M.pos.get(id);
    if (M.mode) {
      if (p.lot || p.pallet || p.note) return toast('Esse lugar está ocupado. Escolhe um livre.', true);
      if (!sameArea(M.mode.zone, p.zone)) return toast(isFg(M.mode.zone) ? 'Escolhe um lugar no Produto acabado.' : 'Escolhe uma posição da C5.', true);
      const m = M.mode; M.mode = null; M.sel = id;
      if (m.type === 'move') {
        const origin = M.pos.get(m.from);
        await runMapAction(api('POST', `/api/locations/${m.from}/move`, { destination: id, version: origin.version, versionTo: p.version }), `${m.lot} movido para ${shortCode(id)}`).catch(() => {});
      } else {
        await runMapAction(api('POST', `/api/locations/${id}/store`, { input: m.input || m.lot, version: p.version }), `${m.lot} arrumado em ${shortCode(id)}`).then(loadMap).catch(() => {});
      }
      return;
    }
    M.sel = M.sel === id ? null : id; M.confirm = false; drawMap();
    if (M.sel && innerWidth <= 1100) $('#side').scrollIntoView({ block: 'start' });
    return;
  }
  const tr = e.target.closest('tr[data-lot]'); const act = e.target.closest('[data-action]')?.dataset.action;
  if (tr && act === 'store-from-list') { M.mode = { type: 'store', lot: tr.dataset.lot, zone: 'f1' }; M.zone = M.zones[0]?.id; M.sel = null; drawMap(); }
  if (tr && act === 'remove-from-list') removeFromList(tr.dataset.lot);
});
$('#map').addEventListener('submit', async e => {
  if (e.target.id !== 'f-list') return; e.preventDefault();
  const i = $('#list-scan'), v = i.value.trim(); if (!v) return;
  try { const l = await api('POST', `/api/lists/${M.zone}`, { input: v }); i.value = ''; toast(`${l.code} adicionado`); await loadMap(); $('#list-scan')?.focus(); }
  catch (er) { toast(er.message, true); i.select(); }
});
$('#modebar').addEventListener('click', e => { if (e.target.closest('[data-action=cancel]')) { M.mode = null; drawMap(); toast('Operação cancelada'); } });
function removeFromList(code) {
  const other = LISTS.find(l => l.id !== M.zone);
  dialog(`<h2 id="dlg-t">Tirar ${esc(code)}</h2><p>Para onde vai?</p>
    <label class="check"><input type="radio" name="dest" value="EXPEDIDO" checked> Saiu da câmara</label>
    <label class="check"><input type="radio" name="dest" value="${other.id}"> ${other.name}</label>`, 'Confirmar', async () => {
    await api('POST', `/api/lists/lot/${encodeURIComponent(code)}/remove`, { destination: $('input[name=dest]:checked').value });
    toast(`${code} tirado de ${LISTS.find(l => l.id === M.zone).name}`); await loadMap();
  });
}

$('#side').addEventListener('click', async e => {
  const go = e.target.closest('[data-go]'); if (go) return selectLocation(go.dataset.go);
  const hist = e.target.closest('[data-history]'); if (hist) { L.pending = hist.dataset.history; return goTo('lots'); }
  const storeBtn = e.target.closest('[data-store]'); if (storeBtn) { M.mode = { type: 'store', lot: storeBtn.dataset.store, zone: 'f1' }; M.sel = null; if (isList(M.zone)) M.zone = M.zones[0]?.id; drawMap(); return; }
  const release = e.target.closest('[data-release]');
  const p = M.sel && M.pos.get(M.sel);
  if (release && p) { M.confirm = false; const d = release.dataset.release, name = p.pallet ? palletName(p.pallet) : p.lot.code; return runMapAction(api('POST', `/api/locations/${p.code}/release`, { destination: d, version: p.version }), `${name}: ${{ SHIPPED: p.pallet ? 'expedida' : 'saiu da câmara', CORRIDOR: 'foi para o Corredor', LEFTOVERS: 'foi para as Sobras' }[d]}`).then(loadMap).catch(() => {}); }
  const a = e.target.closest('[data-action]')?.dataset.action; if (!a) return;
  if (a === 'close') { M.sel = null; M.confirm = false; M.panel = false; drawMap(); }
  if (a === 'move') { M.mode = { type: 'move', from: p.code, lot: p.pallet ? palletName(p.pallet) : p.lot.code, zone: p.zone }; M.confirm = false; drawMap(); }
  if (a === 'release') { M.confirm = true; drawSide(); }
  if (a === 'cancel-confirm') { M.confirm = false; drawSide(); }
  if (a === 'save-notes') runMapAction(p.pallet
    ? api('PUT', `/api/pallets/${p.pallet.id}/notes`, { notes: $('#notes-input').value, version: p.pallet.version }).then(pl => { p.pallet = pl; })
    : api('PUT', `/api/lots/${encodeURIComponent(p.lot.code)}/notes`, { notes: $('#notes-input').value }).then(l => { p.lot = l; }), 'Observação guardada').catch(() => {});
  if (a === 'remove-note') runMapAction(api('POST', `/api/locations/${p.code}/remove-note`, { version: p.version }), 'Nota apagada').catch(() => {});
});
$('#side').addEventListener('submit', async e => {
  e.preventDefault(); const p = M.pos.get(M.sel);
  if (e.target.id === 'f-store') {
    const v = $('#scan').value.trim(); if (!v) return $('#scan').focus();
    await runMapAction(api('POST', `/api/locations/${p.code}/store`, { input: v, version: p.version }), `Guardado em ${shortCode(p.code)}`).then(loadMap).catch(() => { $('#scan')?.select(); });
  }
  if (e.target.id === 'f-pallet') {
    const boxCount = $('#fp-boxes').value.trim(), kgValue = parseKg($('#fp-kg').value);
    if (kgValue != null && isNaN(kgValue)) return toast('O peso tem de ser um número.', true);
    try {
      await api('PUT', `/api/pallets/${e.target.dataset.id}`, { productCode: $('#fp-product').value || null, lot: $('#fp-lot').value, weightKg: kgValue, boxes: boxCount === '' ? null : +boxCount, expiryDate: $('#fp-expiry').value || null, version: +e.target.dataset.version });
      toast('Dados da palete guardados'); await loadMap();
    } catch (er) { toast(er.message, true); if (er.status === 409) await loadMap(); }
  }
  if (e.target.id === 'f-complete') {
    try {
      await api('PUT', `/api/lots/${encodeURIComponent(e.target.dataset.lot)}`, { code: e.target.dataset.lot, productCode: $('#cp-product').value, weightKg: parseKg($('#cp-kg').value), notes: p.lot.notes });
      toast('Lote completo'); await loadMap();
    } catch (er) { toast(er.message, true); }
  }
});
const parseKg = v => { const s = String(v || '').replace(/\s|kg/gi, '').replace(',', '.'); return s === '' ? null : Number(s); };

$('#b-panel').addEventListener('click', () => { M.panel = !document.body.classList.contains('panel-open'); if (!M.panel) M.sel = null; drawMap(); });
$('#q').addEventListener('input', e => { M.q = norm(e.target.value); drawMap(); });
$('#q').addEventListener('keydown', e => {
  if (e.key !== 'Enter' || !M.q) return; e.preventDefault();
  const order = [M.zone, ...M.zones.map(z => z.id).filter(z => z !== M.zone)];
  for (const z of order) { const p = zoneLocations(z).find(x => matches(x)); if (p) return selectLocation(p.code); }
  const inList = M.lists.find(x => lotMatches(x)); if (inList) { M.zone = inList.holding; M.sel = null; return drawMap(); }
  toast(`Nada encontrado para "${e.target.value.trim()}".`, true);
});
document.addEventListener('keydown', e => {
  if (e.key !== 'Escape') return;
  if (!$('#dialog').hidden) return closeDialog();
  if (view === 'map') { if (M.mode) { M.mode = null; toast('Operação cancelada'); } else { M.sel = null; M.confirm = false; } drawMap(); }
});

/* ============================================================ TERMINAL */
// For handhelds: one question at a time, one field, the scanner sends Enter.
const T = { action: null, step: 0, d: {}, msg: null, choices: null, area: null };
// Terminal area: raw material (room, corridor, leftovers) or finished goods. Everything works only in the chosen area.
const areaOf = z => isFg(z) ? 'FG' : 'RM';
const inArea = p => areaOf(p.zone) === T.area;
function terminalArea() {
  if (!can('VIEWER')) return (T.area = 'FG'); // logistics: finished goods only
  if (!T.area) { try { T.area = localStorage.getItem('arrumo.terminalArea'); } catch { /* no storage */ } }
  if (T.area !== 'RM' && T.area !== 'FG') T.area = 'RM';
  return T.area;
}
const canChangeHere = () => canChange(T.area === 'FG' ? 'fg' : 'f1');
const TERMINAL_ACTIONS = [['store', 'Arrumar'], ['move', 'Mover'], ['remove', 'Tirar'], ['search', 'Pesquisar']];
const isFree = p => !p.lot && !p.pallet && !p.note;
const contentName = p => p.pallet ? palletName(p.pallet) : p.lot ? p.lot.code : p.note ? 'Nota: ' + p.note : '';
const terminalWhere = p => shortCode(p.code);
async function loadTerminal() { if (!M.loaded || M.stale) await loadMap(); drawTerminal(); }
// "R522" matches raw material (R522a, R522b…) and finished goods (R522): keep only what the user may change
function terminalLocations(txt) {
  const c = txt.trim().toUpperCase();
  return allLocations().filter(p => isLocation(p, c) && canChange(p.zone) && inArea(p));
}
const choiceName = p => shortCode(p.code);
const allLocations = () => [...M.pos.values()].sort((a, b) => a.code.localeCompare(b.code, 'pt', { numeric: true }));
function findLots(txt, allAreas = false) {
  const c = txt.trim().toUpperCase(), noParens = c.replace(/[()]/g, '');
  const sscc = /^(?:\]C1|\]D2)?00(\d{18})/.exec(noParens)?.[1];
  const target = [c, /\(10\)([^(]+)/.exec(c)?.[1]];
  if (/^\d{6,}$/.test(c)) target.push('L' + c);
  const e = x => x && target.includes(String(x).toUpperCase());
  const matchPallet = pl => pl && ((sscc && pl.sscc === sscc) || e(pl.lot));
  const r = { pos: allLocations().filter(p => e(p.lot?.code) || matchPallet(p.pallet)), list: M.lists.filter(l => e(l.code)) };
  if (allAreas) return r;
  return { pos: r.pos.filter(inArea), list: T.area === 'RM' ? r.list : [] };
}
function drawTerminal() {
  const el = $('#terminal'); if (!el) return;
  terminalArea();
  if (T.action && T.action !== 'search' && !canChangeHere()) T.action = null; // in this area the user can only search
  const g = T.action === 'search' ? T.d.groups : null;
  const tabBar = can('VIEWER') ? `<div class="t-tabs" role="tablist">${TERMINAL_TABS.map(([id, name]) => `<button role="tab" data-tab="${id}" aria-selected="${T.area === id}">${name}${g ? ` <span>${g[id].length}</span>` : ''}</button>`).join('')}</div>` : '';
  const msg = T.msg ? `<p class="t-msg ${T.msg.good ? 'good' : 'bad'}" role="status">${esc(T.msg.text)}</p>` : '';
  if (!T.action) {
    const actions = TERMINAL_ACTIONS.filter(([a]) => a === 'search' || canChangeHere());
    el.innerHTML = `${tabBar}<div class="t-menu">${actions.map(([a, n]) => `<button class="t-btn" data-t="${a}">${n}</button>`).join('')}</div>
      ${actions.length === 1 ? '<p class="note">No produto acabado só podes pesquisar.</p>' : ''}${msg}`;
    return;
  }
  const title = TERMINAL_ACTIONS.find(x => x[0] === T.action)[1];
  const question = {
    store: ['Lê a posição', `Lê o lote para ${T.d.pos && terminalWhere(T.d.pos)}`],
    move: ['Lê o lote ou a posição de onde sai', `${T.d.pos && contentName(T.d.pos)} está em ${T.d.pos && terminalWhere(T.d.pos)}. Lê a posição para onde vai`],
    remove: ['Lê o lote ou a posição', 'Para onde vai?'],
    search: ['Lê ou escreve: lote, código ERP, produto, posição ou nota', ''],
  }[T.action][T.step];
  const field = T.choices ? '' : `<form id="t-form" autocomplete="off"><input id="t-in" class="t-in" enterkeyhint="go" autocapitalize="characters" spellcheck="false" aria-label="${esc(question)}"><button class="t-btn pri">OK</button></form>`;
  const choiceButtons = (T.choices || []).map((x, i) => `<button class="t-btn ${x.cls || ''}" data-choice="${i}">${esc(x.name)}</button>`).join('');
  // wide screen (1024×768 terminal): the operation on the left, results or recent movements on the right
  el.innerHTML = `<div class="t-left">${tabBar}<div class="t-head"><button class="t-back" data-t="">‹ Menu</button><h1>${title}</h1></div>
    ${msg}<p class="t-question">${esc(question)}</p>${field}${choiceButtons ? `<div class="t-menu t-choices">${choiceButtons}</div>` : ''}
    ${T.step || T.choices ? '<button class="t-back" data-t="restart">Cancelar</button>' : ''}</div>
    <div class="t-right">${T.action === 'search' ? terminalResults() : terminalMovements()}</div>`;
  $('#t-in')?.focus();
}
const TERMINAL_TABS = [['RM', 'Matéria-prima'], ['FG', 'Produto acabado']];
function terminalMovements() {
  const ms = M.movs.filter(m => (m.area || 'RM') === T.area).slice(0, 8);
  if (!ms.length) return '';
  return `<h2 class="t-sub">Últimos movimentos</h2>${ms.map(m => `<div class="t-mov"><span>${esc(fmtTime(m.occurredAt))}</span><span><b>${esc(m.username)}</b> ${esc(VERB[m.type] || m.type)} ${esc(m.lot || '')}</span><span>${esc([m.from, m.to].filter(Boolean).map(shortCode).join(' → '))}</span></div>`).join('')}`;
}
function terminalResults() {
  const g = T.d.groups; if (!g) return '';
  const items = g[T.area];
  const card = ([n, o, x]) => `<div class="t-card"><b>${esc(o)}</b><span>${esc(n)}</span>${x.code || x.lot || x.sscc ? `<span>${esc([x.productCode, x.product].filter(Boolean).join(' - ') || 'Sem dados')}</span>` : ''}${x.notes ? `<span class="muted">${esc(x.notes)}</span>` : ''}</div>`;
  const countLine = items.length ? `<p class="note">${items.length === 1 ? '1 resultado' : `${items.length} resultados`}${items.length > 60 ? ', mostro os primeiros 60' : ''}</p>` : '<p class="muted">Nada aqui.</p>';
  return countLine + items.slice(0, 60).map(card).join('');
}
// not found in this area but present in the other one? say which tab to use
function otherAreaHint(v) {
  const r = findLots(v, true), c = v.trim().toUpperCase();
  const pos = [...r.pos, ...allLocations().filter(p => isLocation(p, c))].find(p => !inArea(p));
  const other = pos ? areaOf(pos.zone) : (T.area === 'FG' && r.list.length) ? 'RM' : null;
  return other && can('VIEWER') ? ` Está na ${TERMINAL_TABS.find(x => x[0] === other)[1].toLowerCase()}: muda de aba.` : '';
}
function terminalOk(text) { T.msg = { text, good: true }; T.step = 0; T.d = {}; T.choices = null; drawTerminal(); }
function terminalError(text) { T.msg = { text, good: false }; drawTerminal(); }
function chooseTerminal(list, name, onPick) {
  if (list.length === 1) return onPick(list[0]);
  T.choices = list.map(x => ({ name: name(x), fn: () => { T.choices = null; onPick(x); } })); T.msg = null; drawTerminal();
}
async function runTerminal(promise, ok) {
  try { const r = await promise; applyChange({ locations: [].concat(r || []).filter(x => x && M.pos.has(x.code)) }); terminalOk(typeof ok === 'function' ? ok(r) : ok); }
  catch (e) { if (e.status !== 401) terminalError(e.message); }
}
function terminalInput(v) {
  T.msg = null; T.d.groups = null;
  if (!v) return drawTerminal();
  if (T.action === 'search') {
    let r = findLots(v, true);
    if (!r.pos.length && !r.list.length) { // not a lot: search all the text
      const q = norm(v);
      r = { pos: allLocations().filter(p => !isFree(p) && matches(p, q)), list: M.lists.filter(l => lotMatches(l, q)) };
    }
    // split by area: raw material (room, corridor, leftovers) and finished goods
    const g = { RM: [], FG: [] };
    r.pos.forEach(p => g[isFg(p.zone) ? 'FG' : 'RM'].push([contentName(p), terminalWhere(p), p.lot || p.pallet || {}]));
    r.list.forEach(l => g.RM.push([l.code, LISTS.find(x => x.id === l.holding)?.name, l]));
    if (!g.RM.length && !g.FG.length) return terminalError(`Nada encontrado para "${v.trim()}".`);
    T.d.groups = g;
    if (!g[T.area].length) T.area = T.area === 'RM' ? 'FG' : 'RM'; // jump to the tab that has results
    return drawTerminal();
  }
  if (T.action === 'store' && T.step === 0) {
    const ps = terminalLocations(v);
    if (!ps.length) return terminalError(`${v.trim()} não é uma posição.${otherAreaHint(v)}`);
    const free = ps.filter(isFree);
    if (!free.length) return terminalError(`${ps.length > 1 ? locationGroup(ps[0].code) : shortCode(ps[0].code)} está ocupada: ${ps.map(contentName).filter(Boolean).join(', ')}.`);
    return chooseTerminal(free, choiceName, p => { T.d.pos = p; T.step = 1; drawTerminal(); });
  }
  if (T.action === 'store') {
    const p = M.pos.get(T.d.pos.code);
    return runTerminal(api('POST', `/api/locations/${p.code}/store`, { input: v.trim(), version: p.version }), r => `${contentName(r) || v.trim()} arrumado em ${terminalWhere(p)}.`);
  }
  if (T.step === 0) { // move or remove: find the source
    const r = findLots(v); let ps = r.pos;
    if (!ps.length && !r.list.length) ps = terminalLocations(v).filter(p => !isFree(p));
    if (T.action === 'remove' && !ps.length && r.list.length) {
      const l = r.list[0], other = l.holding === 'LEFTOVERS' ? ['CORRIDOR', 'Corredor'] : ['LEFTOVERS', 'Sobras'];
      const removeTo = d => runTerminal(api('POST', `/api/lists/lot/${encodeURIComponent(l.code)}/remove`, { destination: d }), `${l.code}: feito.`);
      T.step = 1; T.d.pos = null;
      T.choices = [{ name: 'Saiu da câmara', cls: 'warn', fn: () => removeTo('SHIPPED') }, { name: other[1], fn: () => removeTo(other[0]) }];
      T.msg = { text: `${l.code} está no ${LISTS.find(x => x.id === l.holding)?.name}.`, good: true };
      return drawTerminal();
    }
    if (!ps.length) return terminalError(r.list.length ? `${v.trim()} está numa lista, não numa posição. Para arrumar usa Arrumar.` : `Não encontrei ${v.trim()} aqui.${otherAreaHint(v)}`);
    if (!canChange(ps[0].zone)) return terminalError('Não podes mexer nesta zona.');
    return chooseTerminal(ps, p => `${contentName(p)} em ${terminalWhere(p)}`, p => {
      T.d.pos = p; T.step = 1;
      if (T.action === 'remove') {
        const release = d => runTerminal(api('POST', `/api/locations/${p.code}/release`, { destination: d, version: M.pos.get(p.code).version }), `${contentName(p)} tirado de ${terminalWhere(p)}.`);
        T.choices = p.pallet ? [{ name: 'Foi expedida', cls: 'warn', fn: () => release('SHIPPED') }]
          : p.lot ? [{ name: 'Saiu da câmara', cls: 'warn', fn: () => release('SHIPPED') }, { name: 'Corredor', fn: () => release('CORRIDOR') }, { name: 'Sobras', fn: () => release('LEFTOVERS') }]
          : [{ name: 'Apagar nota', fn: () => runTerminal(api('POST', `/api/locations/${p.code}/remove-note`, { version: M.pos.get(p.code).version }), 'Nota apagada.') }];
        T.msg = { text: `${contentName(p)} em ${terminalWhere(p)}.`, good: true };
      }
      drawTerminal();
    });
  }
  // move: destination
  const from = M.pos.get(T.d.pos.code), ps = terminalLocations(v).filter(p => sameArea(p.zone, from.zone));
  if (!ps.length) return terminalError(`${v.trim()} não é uma posição desta área.`);
  const free = ps.filter(isFree);
  if (!free.length) return terminalError(`${ps.length > 1 ? locationGroup(ps[0].code) : shortCode(ps[0].code)} está ocupada.`);
  chooseTerminal(free, choiceName, p => runTerminal(api('POST', `/api/locations/${from.code}/move`, { destination: p.code, version: from.version, versionTo: M.pos.get(p.code).version }), `${contentName(from)} movido para ${terminalWhere(p)}.`));
}
$('#terminal').addEventListener('submit', e => { e.preventDefault(); const v = $('#t-in').value; $('#t-in').value = ''; terminalInput(v); });
$('#terminal').addEventListener('click', e => {
  const tabBtn = e.target.closest('[data-tab]');
  if (tabBtn) {
    if (T.area === tabBtn.dataset.tab) return;
    T.area = tabBtn.dataset.tab; try { localStorage.setItem('arrumo.terminalArea', T.area); } catch { /* no storage */ }
    // switching area mid-operation cancels it; a search stays (only the tab changes)
    if (T.action !== 'search') { T.step = 0; T.d = {}; T.choices = null; T.msg = null; }
    return drawTerminal();
  }
  const b = e.target.closest('[data-t],[data-choice]'); if (!b) return;
  if (b.dataset.choice != null) return T.choices?.[+b.dataset.choice]?.fn();
  const t = b.dataset.t;
  if (t === 'restart') { T.step = 0; T.d = {}; T.choices = null; T.msg = null; }
  else { T.action = t || null; T.step = 0; T.d = {}; T.choices = null; T.msg = null; }
  drawTerminal();
});

/* ============================================================ PRODUCTS */
const P = { list: null, sel: null };
async function loadProductsIfMissing() { if (!P.list && (can('MANAGER') || canFg())) { try { P.list = await api('GET', '/api/products'); if (view === 'map' && M.sel) drawSide(); } catch { /* no list */ } } }
async function loadProducts() {
  try { P.list = await api('GET', '/api/products'); } catch (e) { return toast(e.message, true); }
  $('#t-products').innerHTML = `<thead><tr><th>Código</th><th>Descrição</th><th>GTIN</th><th>Estado</th></tr></thead><tbody>${P.list.length ? P.list.map(p => `<tr data-id="${p.id}" class="${P.sel === p.id ? 'on' : ''}">
    <td class="code-col" data-l="Código">${esc(p.code)}</td><td data-l="Descrição">${esc(p.description)}</td><td data-l="GTIN">${esc(p.gtin || '')}</td>
    <td data-l="Estado">${p.active ? 'Ativo' : '<span class="tag off">Inativo</span>'}</td></tr>`).join('') : '<tr class="no-rows"><td colspan="4">Ainda não há produtos. Cria o primeiro ou importa do Excel em Lotes.</td></tr>'}</tbody>`;
  if (P.sel) productForm(P.list.find(p => p.id === P.sel)); else $('#product-detail').innerHTML = '';
}
function productForm(p) {
  const canEdit = can('MANAGER');
  $('#product-detail').innerHTML = `<section><div class="dhead"><h2>${p ? esc(p.code) : 'Novo produto'}</h2><button class="x" data-action="close" aria-label="Fechar">×</button></div>
    <form id="f-product"><fieldset class="sr-fs" ${canEdit ? '' : 'disabled'}>
    <label for="pr-code">Código</label><input id="pr-code" value="${esc(p?.code)}" maxlength="20" required>
    <label for="pr-desc">Descrição</label><input id="pr-desc" value="${esc(p?.description)}" maxlength="200" required>
    <label for="pr-gtin">GTIN (produto acabado, para ler as etiquetas GS1)</label><input id="pr-gtin" value="${esc(p?.gtin)}" inputmode="numeric" maxlength="14">
    ${p ? `<label class="check"><input type="checkbox" id="pr-active" ${p.active ? 'checked' : ''}> Ativo (aparece nas listas de escolha)</label>` : ''}
    ${canEdit ? `<div class="acts"><button class="btn pri">${p ? 'Guardar' : 'Criar produto'}</button>${p ? '<button class="btn warn" type="button" data-action="delete">Apagar</button>' : ''}</div>` : ''}
    </fieldset></form></section>`;
  $('#pr-code').focus();
}
$('#t-products').addEventListener('click', e => { const tr = e.target.closest('tr[data-id]'); if (!tr) return; P.sel = +tr.dataset.id; $$('#t-products tr').forEach(r => r.classList.toggle('on', r === tr)); productForm(P.list.find(p => p.id === P.sel)); });
$('#b-new-product').addEventListener('click', () => { P.sel = null; productForm(null); });
$('#product-detail').addEventListener('click', async e => {
  const a = e.target.closest('[data-action]')?.dataset.action;
  if (a === 'close') { P.sel = null; $('#product-detail').innerHTML = ''; }
  if (a === 'delete') {
    const p = P.list.find(x => x.id === P.sel);
    dialog(`<h2 id="dlg-t">Apagar ${esc(p.code)}?</h2><p>Só é possível se não houver lotes deste produto. Com lotes, desativa-o.</p>`, 'Apagar', async () => {
      await api('DELETE', `/api/products/${p.id}`); P.sel = null; toast('Produto apagado'); loadProducts();
    });
  }
});
$('#product-detail').addEventListener('submit', async e => {
  e.preventDefault();
  const body = { code: $('#pr-code').value.trim(), description: $('#pr-desc').value.trim(), gtin: $('#pr-gtin').value.trim(), active: $('#pr-active') ? $('#pr-active').checked : true };
  try {
    const r = P.sel ? await api('PUT', `/api/products/${P.sel}`, body) : await api('POST', '/api/products', body);
    P.sel = r.id; toast(`Produto ${r.code} guardado`); loadProducts();
  } catch (er) { toast(er.message, true); }
});

/* ============================================================ LOTS */
const L = { list: [], pending: null, sel: null };
let lotTimer;
async function loadLots(quiet) {
  const state = $('#lot-status').value, q = $('#lot-q').value.trim();
  const params = new URLSearchParams({ q, limit: 300 });
  if (state === 'NO_DATA') params.set('noData', 'true'); else if (state) params.set('status', state);
  if (L.pending) { params.set('q', L.pending.toLowerCase()); $('#lot-q').value = L.pending; $('#lot-status').value = ''; params.delete('status'); }
  try { L.list = await api('GET', '/api/lots?' + params); } catch (e) { return toast(e.message, true); }
  const where = l => l.status === 'SHIPPED' ? '<span class="tag off">Saiu</span>' : l.location ? esc(shortCode(l.location)) : l.holding ? esc(LISTS.find(x => x.id === l.holding)?.name) : '<span class="tag">Sem lugar</span>';
  $('#t-lots').innerHTML = `<thead><tr><th>Lote</th><th>Produto</th><th class="num">Peso</th><th>Produção</th><th>Onde</th></tr></thead><tbody>${L.list.length ? L.list.map(l => `<tr data-lot="${esc(l.code)}" class="${L.sel === l.code ? 'on' : ''}">
    <td class="code-col" data-l="Lote">${esc(l.code)}</td><td data-l="Produto">${l.noData ? '<span class="tag orange">Sem dados</span>' : esc(l.productCode + ' ' + shortName(l.product))}</td>
    <td class="num" data-l="Peso">${kg(l.weightKg)}</td><td data-l="Produção">${fmtDate(l.productionDate)}</td><td data-l="Onde">${where(l)}</td></tr>`).join('')
    : '<tr class="no-rows"><td colspan="5">Nenhum lote com estes filtros.</td></tr>'}</tbody>`;
  if (L.pending) { const c = L.pending; L.pending = null; openLot(c); }
  else if (L.sel && !quiet) openLot(L.sel);
}
$('#f-lot-filter').addEventListener('input', () => { clearTimeout(lotTimer); lotTimer = setTimeout(() => loadLots(true), 250); });
$('#f-lot-filter').addEventListener('submit', e => e.preventDefault());
$('#t-lots').addEventListener('click', e => { const tr = e.target.closest('tr[data-lot]'); if (tr) openLot(tr.dataset.lot); });
const MOVEMENT_LABEL = { STORE: 'Arrumado', MOVE: 'Movido', SHIP: 'Saiu da câmara', REMOVE: 'Retirado', ADD_NOTE: 'Reserva', REMOVE_NOTE: 'Reserva retirada' };
async function openLot(code) {
  L.sel = code; $$('#t-lots tbody tr').forEach(r => r.classList.toggle('on', r.dataset.lot === code));
  let h; try { h = await api('GET', `/api/lots/${encodeURIComponent(code)}`); } catch (e) { return toast(e.message, true); }
  if (!P.list && can('MANAGER')) { try { P.list = await api('GET', '/api/products'); } catch { P.list = []; } }
  const l = h.lot, canEdit = can('MANAGER');
  $('#lot-detail').innerHTML = `<section><div class="dhead"><h2>${esc(l.code)}</h2><button class="x" data-action="close" aria-label="Fechar">×</button></div>
    ${l.noData ? '<p class="warning">Sem dados: escolhe o produto e o peso para o lote deixar de estar a laranja.</p>' : ''}
    ${canEdit ? lotForm(l) : `<dl><dt>Produto</dt><dd>${l.productCode ? esc(l.productCode + ' - ' + l.product) : 'Sem dados'}</dd><dt>Peso</dt><dd>${kg(l.weightKg)}</dd><dt>Produção</dt><dd>${fmtDate(l.productionDate)}</dd><dt>Obs.</dt><dd>${esc(l.notes)}</dd></dl>`}
    <div class="acts">${l.location ? `<button class="btn" data-show="${esc(l.location)}">Ver no mapa</button>` : ''}
      ${canEdit && l.status === 'SHIPPED' ? '<button class="btn" data-action="restore">Voltou à câmara</button>' : ''}
      ${canEdit && !l.location && !l.holding && !h.movements.length ? '<button class="btn warn" data-action="delete">Apagar lote</button>' : ''}</div></section>
    <section><h3>Percurso</h3>${h.movements.length ? `<ul class="log">${h.movements.map(m => `<li><time>${fmtTime(m.occurredAt)}</time>${MOVEMENT_LABEL[m.type] || m.type}${m.from ? ' de ' + esc(shortCode(m.from)) : ''}${m.to ? (m.type === 'STORE' ? ' em ' : ' para ') + esc(shortCode(m.to)) : ''}, ${esc(m.username)}${m.note ? ` (${esc(m.note)})` : ''}</li>`).join('')}</ul>` : '<p class="muted">Ainda sem movimentos.</p>'}</section>`;
}
function lotForm(l) {
  return `<form id="f-lot" data-code="${esc(l?.code || '')}">
    ${l ? '' : '<label for="lt-code">Código do lote</label><input id="lt-code" required maxlength="30" autocomplete="off" spellcheck="false">'}
    <label for="lt-product">Produto</label><select id="lt-product" ${l && !l.noData ? 'required' : ''}><option value="">${l?.noData || !l ? 'Sem produto (fica a laranja)' : 'Escolhe…'}</option>
      ${(P.list || []).filter(p => p.active || p.code === l?.productCode).map(p => `<option value="${esc(p.code)}" ${p.code === l?.productCode ? 'selected' : ''}>${esc(p.code)} - ${esc(shortName(p.description))}</option>`).join('')}</select>
    <label for="lt-kg">Peso (kg)</label><input id="lt-kg" inputmode="decimal" value="${l?.weightKg ?? ''}">
    <label for="lt-date">Data de produção</label><input id="lt-date" type="date" value="${l?.productionDate || ''}">
    <label for="lt-notes">Observações</label><textarea id="lt-notes" rows="2" maxlength="500">${esc(l?.notes)}</textarea>
    <div class="acts"><button class="btn pri">${l ? 'Guardar' : 'Criar lote'}</button></div></form>`;
}
$('#b-new-lot').addEventListener('click', async () => {
  if (!P.list) { try { P.list = await api('GET', '/api/products'); } catch { P.list = []; } }
  L.sel = null; $('#lot-detail').innerHTML = `<section><div class="dhead"><h2>Novo lote</h2><button class="x" data-action="close" aria-label="Fechar">×</button></div>${lotForm(null)}</section>`; $('#lt-code').focus();
});
$('#lot-detail').addEventListener('click', async e => {
  const showBtn = e.target.closest('[data-show]'); if (showBtn) { goTo('map'); return selectLocation(showBtn.dataset.show); }
  const a = e.target.closest('[data-action]')?.dataset.action;
  if (a === 'close') { L.sel = null; $('#lot-detail').innerHTML = ''; }
  if (a === 'restore') { try { await api('POST', `/api/lots/${encodeURIComponent(L.sel)}/restore`); toast('Lote reposto: está no Corredor'); loadLots(); } catch (er) { toast(er.message, true); } }
  if (a === 'delete') dialog(`<h2 id="dlg-t">Apagar ${esc(L.sel)}?</h2><p>Só para lotes criados por engano. Não pode ser desfeito.</p>`, 'Apagar', async () => {
    await api('DELETE', `/api/lots/${encodeURIComponent(L.sel)}`); L.sel = null; $('#lot-detail').innerHTML = ''; toast('Lote apagado'); loadLots();
  });
});
$('#lot-detail').addEventListener('submit', async e => {
  e.preventDefault(); const f = e.target, lotCode = f.dataset.code;
  const body = { code: lotCode || $('#lt-code').value.trim(), productCode: $('#lt-product').value || null, weightKg: parseKg($('#lt-kg').value), productionDate: $('#lt-date').value || null, notes: $('#lt-notes').value };
  if (body.weightKg != null && isNaN(body.weightKg)) return toast('O peso tem de ser um número.', true);
  try {
    const l = lotCode ? await api('PUT', `/api/lots/${encodeURIComponent(lotCode)}`, body) : await api('POST', '/api/lots', body);
    toast(`Lote ${l.code} guardado`); L.sel = l.code; loadLots();
  } catch (er) { toast(er.message, true); }
});

/* import: paste from Excel or a CSV file */
const IMPORT_COLUMNS = { lot: ['lote', 'lot', 'codigo'], product: ['produto', 'artigo', 'product'], weight: ['estoque', 'stock', 'peso', 'kg', 'quantidade', 'weight'], notes: ['observacoes', 'obs', 'observacao', 'notes'], location: ['posicao', 'pos', 'localizacao', 'location'], holding: ['local', 'lista', 'zona', 'holding'] };
// labels shown to the user for the recognised columns
const COLUMN_LABEL = { lot: 'lote', product: 'produto', weight: 'peso', notes: 'observações', location: 'posição', holding: 'local' };
function parseTable(text) {
  const lines = text.replace(/\r/g, '').split('\n').filter(l => l.trim());
  if (!lines.length) return { error: 'Não há nada para importar.' };
  const sep = lines[0].includes('\t') ? '\t' : (lines[0].split(';').length > lines[0].split(',').length ? ';' : ',');
  const splitLine = l => { const out = []; let cur = '', q = false; for (let i = 0; i < l.length; i++) { const ch = l[i]; if (q) { if (ch === '"' && l[i + 1] === '"') { cur += '"'; i++; } else if (ch === '"') q = false; else cur += ch; } else if (ch === '"') q = true; else if (ch === sep) { out.push(cur); cur = ''; } else cur += ch; } out.push(cur); return out.map(s => s.trim()); };
  const head = splitLine(lines[0]).map(norm);
  const idx = {}; for (const [k, names] of Object.entries(IMPORT_COLUMNS)) idx[k] = head.findIndex(c => names.includes(c));
  let firstRow = 1;
  if (idx.lot < 0) {
    // No header (ERP export): Lot, Product, Stock, Notes, [Location], [Holding]
    if (!/^[A-Za-z0-9._/-]{3,30}$/.test(splitLine(lines[0])[0] || '')) return { error: 'Não reconheço as colunas. Usa a exportação do ERP (Lote, Produto, Estoque, Observações) ou põe um cabeçalho com "Lote".' };
    // only the 4 ERP columns: extra columns (warehouse, etc.) must not be read as location or list
    Object.keys(IMPORT_COLUMNS).forEach((k, i) => { idx[k] = i < 4 ? i : -1; }); firstRow = 0;
  }
  const rows = lines.slice(firstRow).map(splitLine).map(c => Object.fromEntries(Object.keys(IMPORT_COLUMNS).map(k => [k, idx[k] >= 0 ? c[idx[k]] ?? '' : null]))).filter(r => r.lot);
  return { rows, columns: firstRow ? Object.keys(IMPORT_COLUMNS).filter(k => idx[k] >= 0).map(k => COLUMN_LABEL[k]) : ['lote', 'produto', 'peso', 'observações (sem cabeçalho, ordem do ERP)'] };
}
$('#b-import').addEventListener('click', () => {
  const el = $('#import-panel'); el.hidden = !el.hidden; if (el.hidden) return;
  el.innerHTML = `<section><div class="dhead"><h2>Importar stock</h2><button class="x" data-action="close" aria-label="Fechar">×</button></div>
    <p>Copia a exportação do ERP (Lote, Produto, Estoque, Observações) e cola aqui com Ctrl+V. Pode vir sem cabeçalho. Com cabeçalho, também aceito as colunas Posicao (ex.: R522_7) e Local (CORREDOR ou SOBRAS) para pôr os lotes logo no sítio. Também serve um ficheiro CSV.</p>
    <label for="imp-txt">Dados</label><textarea id="imp-txt" rows="8" placeholder="Lote&#9;Produto&#9;Estoque&#9;Observacoes"></textarea>
    <label for="imp-file">Ou ficheiro CSV</label><input id="imp-file" type="file" accept=".csv,.txt,text/csv">
    <label for="imp-type">Que stock é</label><select id="imp-type" required><option value="">Escolhe…</option><option value="FG">Stock de produto acabado</option><option value="RM">Stock de matéria-prima</option><option value="LEFTOVERS">Sobras</option></select>
    <p class="note">Matéria-prima: atualiza os lotes da C5. Os que estão numa posição ficam lá; os outros ficam no Corredor.
    Sobras: os lotes vão para a lista Sobras. Produto acabado: substitui o stock do ERP do mapa de produto acabado; as paletes não mudam, a app compara.</p>
    <p id="imp-info" class="note"></p><div class="acts"><button class="btn pri" data-action="import" disabled>Importar</button></div><div id="imp-res"></div></section>`;
  $('#imp-txt').focus();
});
let pendingImport = null, missingFromStock = [];
// Whatever is in the app but missing from the full ERP list has left: show it all and ship it in one click.
function missingBlock(missing) {
  missingFromStock = missing || [];
  if (!missingFromStock.length) return '';
  const where = f => LISTS.find(x => x.id === f.where)?.name || shortCode(f.where);
  return `<div id="imp-missing" class="missing-box"><h3>${missingFromStock.length} ${missingFromStock.length === 1 ? 'lote está' : 'lotes estão'} na app mas não ${missingFromStock.length === 1 ? 'veio' : 'vieram'} no stock</h3>
    <p>Se a lista do ERP está completa, já saíram da câmara.</p>
    <ul class="summary missing">${missingFromStock.map(f => `<li><b>${esc(f.lot)}</b> ${esc(where(f))}</li>`).join('')}</ul>
    <div class="acts"><button class="btn pri" data-action="ship">Dar saída a ${missingFromStock.length === 1 ? 'este lote' : `todos (${missingFromStock.length})`}</button><button class="btn" data-action="keep-missing">Agora não</button></div></div>`;
}
function preview(text) {
  const r = parseTable(text); pendingImport = r.rows || null;
  $('#imp-info').textContent = r.error || `${r.rows.length} lotes encontrados. Colunas reconhecidas: ${r.columns.join(', ')}.`;
  $('[data-action=import]').disabled = !r.rows?.length;
}
$('#import-panel').addEventListener('input', e => { if (e.target.id === 'imp-txt') preview(e.target.value); });
$('#import-panel').addEventListener('change', async e => {
  if (e.target.id !== 'imp-file' || !e.target.files[0]) return;
  const f = e.target.files[0]; if (f.size > 2e6) return toast('Ficheiro demasiado grande (máximo 2 MB).', true);
  let t = await f.text(); if (t.includes('�')) t = new TextDecoder('windows-1252').decode(await f.arrayBuffer());
  $('#imp-txt').value = t; preview(t);
});
$('#import-panel').addEventListener('click', async e => {
  const a = e.target.closest('[data-action]')?.dataset.action;
  if (a === 'close') { $('#import-panel').hidden = true; }
  if (a === 'keep-missing') $('#imp-missing').innerHTML = '<p class="note">Ficaram como estavam. Podes tirá-los no mapa ou no terminal.</p>';
  if (a === 'ship' && missingFromStock.length) {
    e.target.disabled = true;
    try {
      const r = await api('POST', '/api/ship', { lots: missingFromStock.filter(f => !f.palletId).map(f => f.lot), pallets: missingFromStock.filter(f => f.palletId).map(f => f.palletId) });
      $('#imp-missing').innerHTML = `<p class="note">${r.shipped} ${r.shipped === 1 ? 'saiu' : 'saíram'} da câmara. Fica no histórico, com a nota "não veio no stock do ERP".</p>`;
      missingFromStock = []; loadLots(); P.list = null; M.stale = true;
    } catch (er) { toast(er.message, true); e.target.disabled = false; }
  }
  if (a === 'import' && pendingImport) {
    e.target.disabled = true;
    try {
      const dest = $('#imp-type').value;
      if (!dest) { toast('Escolhe que stock é.', true); e.target.disabled = false; $('#imp-type').focus(); return; }
      if (dest === 'FG') {
        const r = await api('POST', '/api/lots/import-fg', { lines: pendingImport });
        $('#imp-res').innerHTML = `<h3>Feito</h3><ul class="summary"><li>${r.lots} lotes no stock do ERP do produto acabado</li><li>${r.withoutPallet} sem palete na app</li><li>${r.newProducts} produtos novos</li></ul>
          ${missingBlock(r.missing)}${r.warnings.length ? `<h3>Avisos</h3><ul class="summary">${r.warnings.map(x => `<li>${esc(x)}</li>`).join('')}</ul>` : ''}`;
        toast('Importação concluída'); P.list = null; M.stale = true; $('[data-action=ship]')?.focus(); return;
      }
      const lines = pendingImport.map(l => dest === 'LEFTOVERS' && !l.location && !l.holding ? { ...l, holding: dest } : l);
      const r = await api('POST', '/api/lots/import', { lines });
      $('#imp-res').innerHTML = `<h3>Feito</h3><ul class="summary"><li>${r.created} lotes novos</li><li>${r.updated} lotes atualizados</li><li>${r.newProducts} produtos novos</li><li>${r.placed} colocados no mapa ou nas listas</li></ul>
        ${missingBlock(r.missing)}${r.warnings.length ? `<h3>Avisos</h3><ul class="summary">${r.warnings.map(x => `<li>${esc(x)}</li>`).join('')}</ul>` : ''}`;
      toast('Importação concluída'); loadLots(); P.list = null; $('[data-action=ship]')?.focus();
    } catch (er) { toast(er.message, true); e.target.disabled = false; }
  }
});

/* ============================================================ HISTORY */
const H = { type: 'movements', rows: [] };
async function loadHistory() {
  $$('[data-hist]').forEach(b => b.setAttribute('aria-pressed', b.dataset.hist === H.type));
  try { H.rows = await api('GET', H.type === 'movements' ? '/api/movements?limit=2000' : '/api/audit?limit=2000'); } catch (e) { return toast(e.message, true); }
  drawHistory();
}
function drawHistory() {
  const q = norm($('#hq').value);
  const ds = H.rows.filter(d => !q || norm(Object.values(d).join(' ')).includes(q)).slice(0, 1000);
  $('#t-history').innerHTML = H.type === 'movements'
    ? `<thead><tr><th>Quando</th><th>Quem</th><th>O quê</th><th>Lote</th><th>De</th><th>Para</th><th>Nota</th></tr></thead><tbody>${ds.map(m => `<tr><td data-l="Quando">${new Date(m.occurredAt).toLocaleString('pt-PT')}</td><td data-l="Quem">${esc(m.username)}</td><td data-l="O quê">${MOVEMENT_LABEL[m.type] || m.type}</td><td class="code-col" data-l="Lote">${esc(m.lot || '')}</td><td data-l="De">${esc(m.from || '')}</td><td data-l="Para">${esc(m.to || '')}</td><td data-l="Nota">${esc(m.note)}</td></tr>`).join('') || '<tr class="no-rows"><td colspan="7">Sem movimentos.</td></tr>'}</tbody>`
    : `<thead><tr><th>Quando</th><th>Quem</th><th>IP</th><th>Ação</th><th>Detalhe</th></tr></thead><tbody>${ds.map(a => `<tr><td data-l="Quando">${new Date(a.occurredAt).toLocaleString('pt-PT')}</td><td data-l="Quem">${esc(a.username || '')}</td><td data-l="IP">${esc(a.ip || '')}</td><td data-l="Ação">${esc(a.action)}</td><td data-l="Detalhe">${esc(a.detail)}</td></tr>`).join('') || '<tr class="no-rows"><td colspan="5">Sem registos.</td></tr>'}</tbody>`;
}
$('#v-history').addEventListener('click', e => { const b = e.target.closest('[data-hist]'); if (b) { H.type = b.dataset.hist; loadHistory(); } });
$('#hq').addEventListener('input', drawHistory);

/* ============================================================ USERS */
const U = { list: [], sel: null };
async function loadUsers() {
  try { U.list = await api('GET', '/api/users'); } catch (e) { return toast(e.message, true); }
  $('#t-users').innerHTML = `<thead><tr><th>Utilizador</th><th>Nome</th><th>Perfil</th><th>Estado</th><th>2FA</th><th>Último login</th></tr></thead><tbody>${U.list.map(u => `<tr data-id="${u.id}" class="${U.sel === u.id ? 'on' : ''}">
    <td class="code-col" data-l="Utilizador">${esc(u.username)}</td><td data-l="Nome">${esc(u.name)}</td><td data-l="Perfil">${ROLE_NAME[u.role]}</td>
    <td data-l="Estado">${!u.active ? '<span class="tag off">Inativo</span>' : u.locked ? '<span class="tag orange">Bloqueado</span>' : u.mustChangePassword ? '<span class="tag">Falta 1.º login</span>' : 'Ativo'}</td>
    <td data-l="2FA">${u.mfaEnabled ? 'Sim' : 'Não'}</td><td data-l="Último login">${u.lastLogin ? new Date(u.lastLogin).toLocaleString('pt-PT') : ''}</td></tr>`).join('')}</tbody>`;
  if (U.sel) userForm(U.list.find(u => u.id === U.sel));
}
const roleOptions = sel => [...ROLES, 'LOGISTICS'].map(p => `<option value="${p}" ${p === sel ? 'selected' : ''}>${ROLE_NAME[p]}</option>`).join('');
const ROLE_HELP = '<p class="note">Consulta: só vê. Operador: arruma, move e liberta. Gestor: também produtos, lotes e importação. Administrador: também utilizadores e registo de acessos. Logística: só o mapa de produto acabado (gestor e admin também mexem nele).</p>';
function userForm(u) {
  $('#user-detail').innerHTML = u ? `<section><div class="dhead"><h2>${esc(u.username)}</h2><button class="x" data-action="close" aria-label="Fechar">×</button></div>
    <form id="f-user"><label for="u-name">Nome</label><input id="u-name" value="${esc(u.name)}" maxlength="100" required>
    <label for="u-role">Perfil</label><select id="u-role">${roleOptions(u.role)}</select>${ROLE_HELP}
    <label class="check"><input type="checkbox" id="u-active" ${u.active ? 'checked' : ''}> Conta ativa</label>
    <div class="acts"><button class="btn pri">Guardar</button></div></form></section>
    <section><h3>Acesso</h3><div class="acts"><button class="btn" data-action="reset-password">Nova palavra-passe temporária</button>
      ${u.mfaEnabled ? '<button class="btn" data-action="reset-mfa">Repor 2FA (telemóvel novo)</button>' : ''}${u.locked ? '<button class="btn" data-action="unlock">Desbloquear</button>' : ''}</div>
      <p class="note">Repor a palavra-passe termina logo as sessões abertas desta pessoa.</p></section>`
    : `<section><div class="dhead"><h2>Novo utilizador</h2><button class="x" data-action="close" aria-label="Fechar">×</button></div>
    <form id="f-user"><label for="u-user">Nome de utilizador</label><input id="u-user" maxlength="40" pattern="[a-z0-9._\\-]{3,40}" autocapitalize="none" spellcheck="false" placeholder="ex.: joao.silva" required>
    <label for="u-name">Nome</label><input id="u-name" maxlength="100" required>
    <label for="u-role">Perfil</label><select id="u-role">${roleOptions('OPERATOR')}</select>${ROLE_HELP}
    <div class="acts"><button class="btn pri">Criar</button></div></form></section>`;
}
function showTemporaryPassword(r) {
  dialog(`<h2 id="dlg-t">Palavra-passe de ${esc(r.user.username)}</h2><p>Entrega-a à pessoa em mão. Só aparece agora; no primeiro login vai ter de escolher outra.</p><p class="temp">${esc(r.temporaryPassword)}</p>`, null, null);
}
$('#t-users').addEventListener('click', e => { const tr = e.target.closest('tr[data-id]'); if (!tr) return; U.sel = +tr.dataset.id; $$('#t-users tr').forEach(r => r.classList.toggle('on', r === tr)); userForm(U.list.find(u => u.id === U.sel)); });
$('#b-new-user').addEventListener('click', () => { U.sel = null; userForm(null); $('#u-user').focus(); });
$('#user-detail').addEventListener('submit', async e => {
  e.preventDefault();
  try {
    if (U.sel) { await api('PUT', `/api/users/${U.sel}`, { name: $('#u-name').value, role: $('#u-role').value, active: $('#u-active').checked }); toast('Guardado'); }
    else { const r = await api('POST', '/api/users', { username: $('#u-user').value.trim(), name: $('#u-name').value.trim(), role: $('#u-role').value }); U.sel = r.user.id; showTemporaryPassword(r); }
    loadUsers();
  } catch (er) { toast(er.message, true); }
});
$('#user-detail').addEventListener('click', async e => {
  const a = e.target.closest('[data-action]')?.dataset.action; if (!a) return;
  if (a === 'close') { U.sel = null; $('#user-detail').innerHTML = ''; return; }
  try {
    if (a === 'reset-password') showTemporaryPassword(await api('POST', `/api/users/${U.sel}/reset-password`));
    if (a === 'reset-mfa') { await api('POST', `/api/users/${U.sel}/reset-mfa`); toast('2FA reposto: configura de novo no próximo login'); }
    if (a === 'unlock') { await api('POST', `/api/users/${U.sel}/unlock`); toast('Conta desbloqueada'); }
    loadUsers();
  } catch (er) { toast(er.message, true); }
});

/* ============================================================ PACKING (admin only) */
const Q = { columns: [], cards: [], status: null, dragging: null };
async function loadBoard() {
  try { Object.assign(Q, await api('GET', '/api/packing')); } catch (e) { return toast(e.message, true); }
  drawBoard();
  if (!$('#dl-products').options.length) {
    try { $('#dl-products').innerHTML = (await api('GET', '/api/products')).filter(p => p.active).map(p => `<option value="${esc(p.code + ' - ' + p.description)}">`).join(''); } catch { /* no suggestions */ }
  }
}
function drawBoard() {
  const e = Q.status, last = e?.lastDispatch;
  const upcoming = e?.nextDispatch ? new Date(e.nextDispatch) : null;
  $('#packing-status').innerHTML = (upcoming ? `Próxima lista: <b>${upcoming.toLocaleDateString('pt-PT', { weekday: 'long', day: '2-digit', month: '2-digit' })} às ${upcoming.toLocaleTimeString('pt-PT', { hour: '2-digit', minute: '2-digit' })}</b>. ` : '')
    + (last ? (last.ok ? `Última: ${fmtTime(last.occurredAt)}, enviada (${esc(last.detail)}). ` : `<span class="bad">Última (${fmtTime(last.occurredAt)}) falhou: ${esc(last.detail)}</span> `) : '')
    + (!e?.dailyConfigured || !e?.finishedConfigured ? `<span class="bad">Falta configurar o Power Automate em "Configurar envios" (${[!e?.dailyConfigured && 'lista diária', !e?.finishedConfigured && 'concluídos'].filter(Boolean).join(' e ')}).</span>` : '');
  $('#board').innerHTML = Q.columns.map(c => {
    const cs = Q.cards.filter(k => k.columnId === c.id);
    return `<section class="kcol" data-col="${c.id}"><div class="khead"><div><h2>${esc(c.name)}<span class="n">${cs.length}</span></h2>
      <div class="kflags">${c.dailyList ? '<span class="tag">Lista das 17h</span>' : ''}${c.reports ? '<span class="tag">Reporta no Teams</span>' : ''}</div></div>
      <button class="kmenu" data-column="${c.id}" aria-label="Opções da coluna ${esc(c.name)}">⋯</button></div>
      <div class="klist">${cs.map(cardHtml).join('')}</div>
      <button class="kadd" data-new="${c.id}">+ Adicionar cartão</button></section>`;
  }).join('');
}
function cardHtml(k) {
  const state = k.reportedAt ? `<span class="ks ok">Reportado ${fmtTime(k.reportedAt)}</span>` : k.sendError ? `<span class="ks bad">Não reportado: ${esc(k.sendError)}</span>` : '';
  return `<div class="kcard" role="button" tabindex="0" draggable="true" data-card="${k.id}"><b>${esc(k.title)}</b>
    ${k.product || k.quantity ? `<span class="kp">${esc([k.product, k.quantity].filter(Boolean).join(' | '))}</span>` : ''}
    ${k.notes ? `<span class="kn">${esc(k.notes)}</span>` : ''}${state}</div>`;
}
function cardForm(k, columnId) {
  const col = k ? k.columnId : columnId;
  dialog(`<h2 id="dlg-t">${k ? 'Editar embalamento' : 'Novo embalamento'}</h2>
    <label for="k-title">Título</label><input id="k-title" maxlength="200" required value="${esc(k?.title)}">
    <label for="k-product">Produto</label><input id="k-product" maxlength="250" list="dl-products" value="${esc(k?.product)}" placeholder="Código ERP ou nome">
    <label for="k-qty">Quantidade</label><input id="k-qty" maxlength="60" value="${esc(k?.quantity)}" placeholder="Ex.: 120 cx, 800 kg">
    <label for="k-notes">Notas</label><textarea id="k-notes" rows="3" maxlength="2000">${esc(k?.notes)}</textarea>
    <label for="k-col">Coluna</label><select id="k-col">${Q.columns.map(c => `<option value="${c.id}" ${c.id === col ? 'selected' : ''}>${esc(c.name)}</option>`).join('')}</select>
    ${k ? `<div class="acts">${k.sendError || k.reportedAt ? '<button class="btn" type="button" data-k="report">Reportar no Teams</button>' : ''}<button class="btn" type="button" data-k="archive">Arquivar</button><button class="btn warn" type="button" data-k="delete">Apagar</button></div>` : ''}`,
  k ? 'Guardar' : 'Criar', async () => {
    const body = { columnId: col, title: $('#k-title').value, product: $('#k-product').value, quantity: $('#k-qty').value, notes: $('#k-notes').value };
    const destination = +$('#k-col').value;
    if (k) {
      const r = await api('PUT', `/api/packing/cards/${k.id}`, { ...body, version: k.version });
      if (destination !== k.columnId) await moveCard(k.id, destination, 1e6, r.version);
    } else {
      const r = await api('POST', '/api/packing/cards', { ...body, columnId: destination });
      if (Q.columns.find(c => c.id === destination)?.reports) await moveCard(r.id, destination, 1e6, r.version);
    }
    await loadBoard();
  });
  $('.dialog-box').onclick = async ev => {
    const a = ev.target.closest('[data-k]')?.dataset.k; if (!a) return;
    try {
      if (a === 'report') { const r = await api('POST', `/api/packing/cards/${k.id}/report`); toast(r.sendError ? r.sendError : 'Reportado no Teams', !!r.sendError); }
      if (a === 'archive') { await api('POST', `/api/packing/cards/${k.id}/archive`); toast('Cartão arquivado'); }
      if (a === 'delete') { await api('DELETE', `/api/packing/cards/${k.id}`); toast('Cartão apagado'); }
      closeDialog(); loadBoard();
    } catch (er) { $('#d-error').textContent = er.message; }
  };
}
async function moveCard(id, columnId, index, version) {
  const r = await api('POST', `/api/packing/cards/${id}/move`, { columnId, index, version });
  if (r?.reportedAt) toast('Reportado no Teams');
  else if (r?.sendError) toast('Não foi reportado: ' + r.sendError, true);
}
function columnForm(c) {
  const i = Q.columns.indexOf(c);
  dialog(`<h2 id="dlg-t">${c ? 'Coluna' : 'Nova coluna'}</h2>
    <label for="col-name">Nome</label><input id="col-name" maxlength="60" required value="${esc(c?.name)}">
    ${c ? `<label class="check"><input type="checkbox" id="c-daily" ${c.dailyList ? 'checked' : ''}> Os cartões desta coluna vão na lista das 17h</label>
    <label class="check"><input type="checkbox" id="c-reports" ${c.reports ? 'checked' : ''}> Reportar no Teams os cartões que entram aqui</label>
    <div class="acts"><button class="btn" type="button" data-c="left" ${i === 0 ? 'disabled' : ''}>Mover para a esquerda</button><button class="btn" type="button" data-c="right" ${i === Q.columns.length - 1 ? 'disabled' : ''}>Mover para a direita</button></div>
    <div class="acts"><button class="btn" type="button" data-c="archive">Arquivar todos os cartões</button><button class="btn warn" type="button" data-c="delete">Apagar coluna</button></div>` : ''}`,
  c ? 'Guardar' : 'Criar', async () => {
    if (c) await api('PUT', `/api/packing/columns/${c.id}`, { name: $('#col-name').value, dailyList: $('#c-daily').checked, reports: $('#c-reports').checked });
    else await api('POST', '/api/packing/columns', { name: $('#col-name').value });
    await loadBoard();
  });
  $('.dialog-box').onclick = async ev => {
    const a = ev.target.closest('[data-c]')?.dataset.c; if (!a) return;
    try {
      if (a === 'left' || a === 'right') await api('POST', `/api/packing/columns/${c.id}/move`, { index: a === 'left' ? i - 1 : i + 1 });
      if (a === 'archive') toast(`${await api('POST', `/api/packing/columns/${c.id}/archive`)} cartões arquivados`);
      if (a === 'delete') { await api('DELETE', `/api/packing/columns/${c.id}`); toast('Coluna apagada'); }
      closeDialog(); loadBoard();
    } catch (er) { $('#d-error').textContent = er.message; }
  };
}
$('#board').addEventListener('click', e => {
  const k = e.target.closest('[data-card]'); if (k) return cardForm(Q.cards.find(x => x.id === +k.dataset.card));
  const n = e.target.closest('[data-new]'); if (n) return cardForm(null, +n.dataset.new);
  const m = e.target.closest('[data-column]'); if (m) return columnForm(Q.columns.find(x => x.id === +m.dataset.column));
});
$('#board').addEventListener('keydown', e => { const k = e.target.closest('[data-card]'); if (k && (e.key === 'Enter' || e.key === ' ')) { e.preventDefault(); cardForm(Q.cards.find(x => x.id === +k.dataset.card)); } });
$('#b-pk-column').addEventListener('click', () => columnForm(null));
$('#b-pk-send').addEventListener('click', () => dialog(`<h2 id="dlg-t">Enviar a lista agora?</h2><p>Vai por email e Teams a lista das colunas marcadas para as 17h, como no envio automático.</p>`, 'Enviar', async () => {
  const r = await api('POST', '/api/packing/send-now'); toast(r.ok ? 'Lista enviada' : r.detail, !r.ok); await loadBoard();
}));
// Power Automate URLs: stored encrypted by the app; the screen only shows the tail of each
$('#b-pk-config').addEventListener('click', async () => {
  const c = await api('GET', '/api/packing/config');
  const cfgField = (id, name, i) => `<fieldset class="cfg"><legend>${name}</legend>
    <p class="note">${i.defined ? `Definido (termina em <code>…${esc(i.tail)}</code>)${i.origin === 'SERVER' ? ', vem do servidor (.env)' : `, guardado por ${esc(i.updatedBy)} ${fmtTime(i.updatedAt)}`}.` : '<span class="bad">Não definido.</span>'}</p>
    <label for="cfg-${id}">Novo URL</label><input id="cfg-${id}" type="url" autocomplete="off" spellcheck="false" placeholder="Deixa vazio para não mudar">
    ${i.origin === 'APP' ? `<label class="chk"><input type="checkbox" id="cfg-${id}-apagar"> Apagar o URL guardado</label>` : ''}</fieldset>`;
  dialog(`<h2 id="dlg-t">Configurar envios</h2>
    <p>Cola o URL de cada fluxo do Power Automate (o que o Teams dá ao criar o workflow). Fica guardado cifrado; ninguém o consegue ver depois, nem tu.</p>
    ${cfgField('daily', 'Lista das 17h (Teams + email)', c.daily)}${cfgField('finished', 'Embalamento concluído (Teams)', c.finished)}`, 'Guardar', async () => {
    const v = id => $(`#cfg-${id}-apagar`)?.checked ? '' : ($(`#cfg-${id}`).value.trim() || null);
    const request = { daily: v('daily'), finished: v('finished') };
    if (request.daily === null && request.finished === null) return;
    await api('PUT', '/api/packing/config', request); toast('Configuração guardada'); await loadBoard();
  });
});
// drag and drop (on phones/terminals use the card's Column field)
$('#board').addEventListener('dragstart', e => {
  const k = e.target.closest('[data-card]'); if (!k) return;
  Q.dragging = +k.dataset.card; k.classList.add('dragging'); e.dataTransfer.effectAllowed = 'move'; e.dataTransfer.setData('text/plain', k.dataset.card);
});
$('#board').addEventListener('dragend', () => { Q.dragging = null; $$('.dragging,.drop-target').forEach(x => x.classList.remove('dragging', 'drop-target')); $$('.kmarker').forEach(x => x.remove()); });
function dropIndex(list, y) {
  const cs = $$('.kcard:not(.dragging)', list);
  const i = cs.findIndex(c => { const r = c.getBoundingClientRect(); return y < r.top + r.height / 2; });
  return { i: i < 0 ? cs.length : i, before: cs[i] };
}
$('#board').addEventListener('dragover', e => {
  const col = e.target.closest('.kcol'); if (!col || Q.dragging == null) return;
  e.preventDefault();
  $$('.drop-target').forEach(x => x !== col && x.classList.remove('drop-target')); col.classList.add('drop-target');
  const list = $('.klist', col), { before } = dropIndex(list, e.clientY);
  let m = $('.kmarker'); if (!m) { m = document.createElement('div'); m.className = 'kmarker'; }
  before ? list.insertBefore(m, before) : list.appendChild(m);
});
$('#board').addEventListener('drop', async e => {
  const col = e.target.closest('.kcol'); if (!col || Q.dragging == null) return;
  e.preventDefault();
  const id = Q.dragging, k = Q.cards.find(x => x.id === id), { i } = dropIndex($('.klist', col), e.clientY);
  try { await moveCard(id, +col.dataset.col, i, k.version); } catch (er) { toast(er.message, true); }
  loadBoard();
});

boot();
})();
