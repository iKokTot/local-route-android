const SERVICES = {};
const VLESS_SERVICES = {};
const VLESS_APP_LIBRARY = [
  { name: 'Telegram Desktop', process: 'Telegram.exe' }, { name: 'Discord', process: 'Discord.exe' },
  { name: 'WhatsApp', process: 'WhatsApp.exe' }, { name: 'ChatGPT', process: 'ChatGPT.exe' },
  { name: 'Codex · сетевой процесс', process: 'codex.exe' },
  { name: 'Google Chrome', process: 'chrome.exe' }, { name: 'Microsoft Edge', process: 'msedge.exe' },
  { name: 'Firefox', process: 'firefox.exe' }, { name: 'Brave', process: 'brave.exe' },
  { name: 'Opera', process: 'opera.exe' }, { name: 'Яндекс Браузер', process: 'browser.exe' },
  { name: 'Steam', process: 'steam.exe' }, { name: 'Epic Games Launcher', process: 'EpicGamesLauncher.exe' },
  { name: 'Battle.net', process: 'Battle.net.exe' }, { name: 'Spotify', process: 'Spotify.exe' },
  { name: 'CapCut', process: 'CapCut.exe' }, { name: 'TikTok', process: 'TikTok.exe' }
];
const STRATEGY_LABELS = {};
const UPSTREAM_STRATEGY_IDS = [];
const PAGE_TITLES = { control: 'Обзор', lab: 'DPI', telegram: 'Telegram', routes: 'VPN', settings: 'Настройки' };
let settingsReturnPage = 'control';
let librarySignature = '';
const isAndroid = () => state?.platform === 'android';
const $ = (selector) => document.querySelector(selector);
const all = (selector) => [...document.querySelectorAll(selector)];
const escapeHtml = (value) => String(value ?? '').replace(/[&<>'"]/g, (character) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' })[character]);
const clone = (value) => JSON.parse(JSON.stringify(value));
const strategyLabel = (id) => STRATEGY_LABELS[id] || id;
const domainPlural = new Intl.PluralRules('ru');
const domainCount = (count) => `${count} ${{ one: 'домен', few: 'домена', many: 'доменов', other: 'домена' }[domainPlural.select(count)]}`;
let state;
let dpiDraft;
let vlessDraft;
let telegramDraft;
let savedDpi;
let savedVless;
let savedTelegram;
let pending = null;
let revision = 0;
let readPending = false;
let currentPage = 'control';
let toastTimer;
let progressTimer;
let progressOperation = null;
const scrollPositions = {};

function toast(message) {
  clearTimeout(toastTimer);
  $('#toast').textContent = message;
  $('#toast').classList.add('show');
  toastTimer = setTimeout(() => $('#toast').classList.remove('show'), 6500);
}
function showError(message) {
  const changed = $('#app-error').textContent !== message;
  $('#app-error').textContent = message;
  $('#app-error').classList.toggle('hidden', !message);
  if (message && changed) toast(message);
}
function dirty(kind) {
  if (!state) return false;
  if (kind === 'dpi') return JSON.stringify(dpiDraft) !== savedDpi;
  if (kind === 'vless') return JSON.stringify(vlessDraft) !== savedVless;
  return JSON.stringify(telegramDraft) !== savedTelegram;
}
function resetDraft(kind) {
  if (kind === 'dpi') {
    dpiDraft = { services: [...state.profile.services], customDomains: [...state.profile.customDomains], strategy: state.profile.strategy };
    savedDpi = JSON.stringify(dpiDraft);
  } else if (kind === 'vless') {
    vlessDraft = { uri: '', mode: state.vless?.mode || 'all', services: [...(state.vless?.services || [])], apps: [...(state.vless?.apps || [])], customDomains: [...(state.vless?.customDomains || [])] };
    savedVless = JSON.stringify(vlessDraft);
  } else {
    telegramDraft = { port: state.telegram?.port || 1443, enabled: Boolean(state.telegram?.enabled) };
    savedTelegram = JSON.stringify(telegramDraft);
  }
}
function confirmAction(message, actionLabel = 'Продолжить') {
  return new Promise(resolve => {
    if (document.querySelector('#confirmation-dialog')) { resolve(false); return; }
    const dialog = document.createElement('dialog'); dialog.id = 'confirmation-dialog';
    const text = document.createElement('p'); text.textContent = message;
    const actions = document.createElement('div'); actions.className = 'vless-actions';
    for (const [label, accepted] of [['Отмена', false], [actionLabel, true]]) {
      const button = document.createElement('button'); button.className = 'button ' + (accepted ? 'primary' : 'outlined'); button.textContent = label;
      button.onclick = () => { dialog.returnValue = accepted ? 'yes' : ''; dialog.close(); }; actions.append(button);
    }
    dialog.append(text, actions); document.body.append(dialog);
    dialog.addEventListener('close', () => { const accepted = dialog.returnValue === 'yes'; dialog.remove(); resolve(accepted); }, {once:true});
    dialog.showModal();
  });
}
async function navigate(page) {
  if (!PAGE_TITLES[page]) return;
  if (isAndroid() && currentPage === 'settings' && page !== 'settings' && (dirty('dpi') || dirty('telegram'))) {
    if (!await confirmAction('Есть несохранённые настройки. Выйти и отменить изменения?', 'Выйти без сохранения')) return;
    for (const kind of ['dpi','telegram']) { resetDraft(kind); renderEditor(kind); }
  }
  if (page === 'settings' && currentPage !== 'settings') settingsReturnPage = currentPage;
  scrollPositions[currentPage] = $('#page-content').scrollTop;
  currentPage = page;
  all('[data-page-panel]').forEach((panel) => panel.classList.toggle('hidden', panel.dataset.pagePanel !== page));
  all('.nav-button[data-page]').forEach((button) => {
    const active = button.dataset.page === page;
    button.classList.toggle('active', active);
    if (active) button.setAttribute('aria-current', 'page'); else button.removeAttribute('aria-current');
  });
  $('#page-title').textContent = PAGE_TITLES[page];
  if (isAndroid()) document.querySelector('.settings-link').classList.toggle('hidden', page === 'settings');
  if (isAndroid() && page === 'settings') {
    all('[data-mode-settings]').forEach(group => { group.open = group.dataset.modeSettings === settingsReturnPage; });
  }
  $('#page-content').scrollTo({ top: scrollPositions[page] || 0, behavior: 'instant' });
}
function status(selector, text, level = '') {
  $(selector).textContent = text;
  $(selector).className = `route-state ${level}`;
}
function disable(selector, disabled, reason = '') {
  all(selector).forEach((node) => { node.disabled = disabled; node.title = disabled ? reason : ''; });
}
function renderControls() {
  if (!state) {
    $('#page-content').inert = true;
    return;
  }
  $('#page-content').inert = false;
  if (isAndroid()) {
    const trial = Boolean(state.active && state.dpiTrial?.pendingConfirmation);
    $('#android-dpi-feedback').classList.toggle('hidden', !trial);
    $('#test-results').classList.toggle('hidden', trial);
    $('#test-results').closest('section').classList.toggle('hidden', !trial && !$('#test-results .test-result'));
    $('#test-results').closest('.service-feedback').classList.toggle('hidden', !trial && !$('#test-results .test-result'));
    const servers = state.vlessServers || [];
    $('#toggle-vless').classList.toggle('hidden', !state.vlessConfigured);
    $('#test-vless').classList.toggle('hidden', !state.vlessConfigured);
    $('#auto-server').classList.toggle('hidden', servers.length < 2);
    $('#refresh-subscription').classList.toggle('hidden', !state.vlessSubscriptionSaved);
    $('#delete-vless').classList.toggle('hidden', !servers.length && !state.vlessConfigured);
    $('#single-server-editor').classList.add('hidden');
    $('#vless-profile-hint').classList.add('hidden');
  }
  const busy = Boolean(pending) || Boolean(state.telegramBusy) || Boolean(state.operation);
  const dpiBusy = pending?.kind === 'dpi' || ['auto','test'].includes(state.operation?.kind);
  const dpiLocked = busy || state.active;
  const vlessLocked = busy || state.vlessActive;
  disable('#open-subscription, .remove-subscription, .refresh-subscription', vlessLocked, 'Сначала остановите VPN');
  disable('#dpi-command, #save-dpi-command', dpiLocked, 'Сначала остановите DPI');
  disable('#confirm-dpi', busy || !state.active || !state.dpiTrial?.pendingConfirmation, 'Сначала запустите вариант и проверьте сервис');
  disable('#reject-dpi', busy || !state.dpiTrial?.candidate, 'Начните проверку профилей');
  $('#reject-dpi').textContent=state.dpiTrial?.hasNext===false ? 'Не работает — остановить' : 'Не работает — следующий';
  disable('.bypass-app', busy || state.vlessActive, 'Сначала остановите VPN');
  disable('#subscription-input, #import-subscription, .select-server', vlessLocked, 'Сначала остановите VPN');
  all('.select-server').forEach(button=>{if(state.vlessServers?.some(item=>item.id===button.dataset.serverId && item.selected))button.disabled=true;});
  disable('#auto-server', vlessLocked || dirty('vless') || !state.vlessServers?.length, 'Добавьте список серверов и остановите VPN');
  disable('#refresh-subscription', vlessLocked || dirty('vless') || !state.vlessSubscriptionSaved, 'Сначала добавьте HTTPS-подписку');
  const tgLocked = busy || state.telegramActive;
  disable('#service-list input, #custom-domain, #add-domain, #custom-domains button', dpiLocked, 'Сначала остановите DPI');
  disable('#save-dpi', dpiLocked || !dirty('dpi'), state.active ? 'Сначала остановите DPI' : 'Нет изменений для сохранения');
  disable('#vless-uri, #vless-mode, .vless-service, .vless-app, #vless-custom-domain, #add-vless-domain, #vless-custom-domains button, #manual-vless-app, #add-vless-app', vlessLocked, 'Сначала остановите VLESS');
  disable('#save-vless', vlessLocked || !dirty('vless'), state.vlessActive ? 'Сначала остановите VLESS' : 'Нет изменений для сохранения');
  disable('#test-vless', busy || !state.vlessConfigured || dirty('vless'), 'Сначала сохраните профиль');
  disable('#start-vless, #toggle-vless', busy || (!state.vlessActive && (!state.vlessConfigured || dirty('vless'))), 'Сначала сохраните профиль VLESS');
  disable('#start-dpi', busy || (!state.active && (!state.verifiedStrategy || dirty('dpi'))), 'Подберите стратегию на вкладке «YouTube и Discord»');
  disable('#start-saved-dpi', busy || state.active || !state.verifiedStrategy || dirty('dpi'), 'Сначала подберите и сохраните стратегию');
  disable('[data-auto-select], .test-strategy', busy || dirty('dpi') || (!state.profile.services.length && !state.profile.customDomains.length), 'Сначала выберите YouTube или Discord и сохраните сервисы');
  disable('.stop-engine', (busy && !dpiBusy) || (!state.active && !dpiBusy), 'DPI уже остановлен');
  disable('#telegram-port', tgLocked, 'Остановите прокси перед изменением порта');
  disable('#telegram-enabled', tgLocked, 'Сначала остановите Telegram-прокси');
  disable('#telegram-save', tgLocked || !dirty('telegram'), state.telegramActive ? 'Сначала остановите Telegram-прокси' : 'Нет изменений для сохранения');
  disable('#telegram-start', busy || state.telegramActive || dirty('telegram'), state.telegramActive ? 'Прокси уже запущен' : 'Сначала сохраните настройки');
  disable('#start-telegram', busy || (!state.telegramActive && dirty('telegram')), 'Сначала сохраните настройки Telegram');
  disable('#telegram-stop, #telegram-connect, #telegram-copy', busy || !state.telegramActive, 'Сначала запустите прокси');
  const canStart = (!state.active && state.verifiedStrategy) || (!state.vlessActive && state.vlessConfigured) || (!state.telegramActive && state.telegram?.enabled);
  disable('#start-all', busy || !canStart || dirty('dpi') || dirty('vless') || dirty('telegram'), 'Сначала настройте компоненты и сохраните изменения');
  const canStop = state.active || state.vlessActive || state.telegramActive || Boolean(pending) || Boolean(state.operation);
  disable('#stop-all', !canStop || pending?.kind === 'stop', 'Все компоненты уже остановлены');
  disable('#autostart-toggle, #run-check, #refresh-log, #export-diagnostics', busy, 'Дождитесь завершения операции');
  disable('#delete-vless', busy || state.vlessActive || (!state.vlessConfigured && !state.vlessServers?.length), 'Сначала остановите VLESS');
  disable('#pick-vless-app', vlessLocked, 'Сначала остановите VLESS');
  all('[data-reset-kind]').forEach(button => { const kind=button.dataset.resetKind; button.disabled=busy || !dirty(kind) || (kind==='dpi' ? state.active : kind==='vless' ? state.vlessActive : state.telegramActive); });
  $('#dpi-save-status').textContent = dirty('dpi') ? 'Есть несохранённые изменения' : 'Изменения сохранены';
  $('#vless-save-status').textContent = dirty('vless') ? 'Есть несохранённые изменения. Сохраните профиль перед запуском.' : (state.vlessConfigured ? 'Профиль сохранён' : 'Добавьте VLESS-ссылку и сохраните профиль.');
  $('#telegram-save-status').textContent = dirty('telegram') ? 'Есть несохранённые изменения' : 'Изменения сохранены';
  $('#vless-edit-hint').classList.toggle('hidden', !state.vlessActive);
  $('#telegram-settings-hint').textContent = state.telegramActive ? 'Чтобы изменить настройки, остановите прокси.' : 'Порт и совместный запуск сохраняются кнопкой ниже.';
  $('#start-dpi').textContent = state.active ? 'Остановить DPI' : 'Запустить DPI';
  $('#start-vless').textContent = state.vlessActive ? 'Остановить VPN' : 'Запустить VPN';
  $('#toggle-vless').textContent = state.vlessActive ? 'Остановить VPN' : 'Запустить VPN';
  $('#start-telegram').textContent = state.telegramActive ? 'Остановить прокси' : 'Запустить прокси';
  const step = state.operation?.progress;
  const message = step ? step.message || step.label || 'Стратегия ' + step.index + ' из ' + step.total + ': ' + strategyLabel(step.strategy) : (pending?.message || (state.operation ? 'Выполняется операция…' : state.telegramBusy ? 'Telegram-прокси запускается…' : ''));
  const progress = $('#global-progress');
  const operation = pending || state.operation || (state.telegramBusy ? 'telegram-starting' : null);
  progress.querySelector('span').textContent = message;
  progress.title = message;
  if (operation !== progressOperation) {
    clearTimeout(progressTimer);
    progressOperation = operation;
    progress.classList.add('hidden');
    // Quick saves and refreshes finish without flashing an unnecessary spinner.
    if (operation) progressTimer = setTimeout(() => {
      if (progressOperation === operation) progress.classList.remove('hidden');
    }, 180);
  }
  all('[data-page-panel]').forEach((panel) => panel.setAttribute('aria-busy', String(busy)));
}
function renderState() {
  if (!state) return;
  const running = [state.active && 'DPI', state.vlessActive && 'VPN', state.telegramActive && 'Telegram'].filter(Boolean);
  for (const [selector, active] of [['#dpi-run-state', state.active], ['#vless-run-state', state.vlessActive], ['#telegram-run-state', state.telegramActive]]) {
    $(selector).closest('.component-card').classList.toggle('is-running', Boolean(active));
  }
  $('#engine-status').textContent = running.length ? `Запущено: ${running.join(' · ')}` : 'Компоненты выключены';
  $('#status-dot').className = `status-dot ${pending || state.telegramBusy ? 'waiting' : (running.length ? 'active' : '')}`;
  $('#protection-title').textContent = running.length === 3 ? 'Все компоненты запущены' : (running.length ? `Запущено компонентов: ${running.length} из 3` : 'Компоненты выключены');
  const ready = [state.verifiedStrategy && 'DPI', state.vlessConfigured && 'VPN', state.telegram?.enabled && 'Telegram'].filter(Boolean);
  status('#dpi-run-state', state.active ? 'Запущен' : (state.verifiedStrategy ? 'Готов к запуску' : 'Нужен подбор'), state.active ? 'active' : '');
  status('#vless-run-state', state.vlessActive ? 'Запущен' : (state.vlessConfigured ? 'Готов к запуску' : 'Не настроен'), state.vlessActive ? 'active' : '');
  const tgStatus = state.telegramBusy ? 'Запускается…' : (state.telegramActive ? 'Прокси готов' : (state.telegramError ? 'Ошибка прокси' : 'Выключен'));
  const tgLevel = state.telegramBusy ? 'waiting' : (state.telegramActive ? 'active' : (state.telegramError ? 'error' : ''));
  status('#telegram-run-state', tgStatus, tgLevel);
  status('#telegram-state', tgStatus, tgLevel);
  status('#vless-state', state.vlessActive ? 'Запущен' : (state.vlessConfigured ? 'Профиль сохранён' : 'Не настроен'), state.vlessActive ? 'active' : '');
  status('#dpi-strategy-state', state.verifiedStrategy ? 'Подтверждена' : 'Не подобрана', state.verifiedStrategy ? 'active' : '');
  $('#dpi-detail').textContent = state.active ? `Стратегия: ${strategyLabel(state.profile.strategy)}.` : (state.verifiedStrategy ? `Сохранена стратегия: ${strategyLabel(state.verifiedStrategy)}.` : 'Выберите сервисы и запустите подбор стратегии.');
  $('#dpi-strategy-description').textContent = state.verifiedStrategy ? `Сохранена стратегия «${strategyLabel(state.verifiedStrategy)}». Повторный подбор проверит стратегии заново и запустит подходящую.` : 'Автоподбор проверит стратегии по очереди и включит первую подходящую.';
  const allTraffic = state.vless?.mode === 'all';
  $('#vless-detail').textContent = state.vlessConfigured ? (allTraffic ? 'Весь внешний трафик; локальная сеть и TGWs — напрямую.' : 'Выбранные сайты и приложения через ваш сервер.') : 'Добавьте VLESS-ссылку и выберите маршруты.';
  $('#telegram-detail').textContent = state.telegramActive ? 'Прокси готов. Подтвердите подключение в Telegram.' : (state.telegramError || 'После запуска подключите прокси в Telegram.');
  $('#telegram-error').textContent = state.telegramError || '';
  $('#vless-profile-hint').textContent = state.vlessConfigured ? 'Ссылка уже сохранена и скрыта. Вставьте новую, чтобы заменить сервер.' : 'Ссылка хранится на компьютере в зашифрованном виде и не попадает в журнал.';
  $('#route-mode-title').textContent = allTraffic ? 'Через сервер VLESS' : 'Напрямую';
  $('#route-mode-description').textContent = state.vlessConfigured ? (allTraffic ? 'Сохранённый профиль: весь внешний трафик через сервер. Локальная сеть и TGWs — напрямую.' : 'Сохранённый профиль: только выбранные сайты и приложения через сервер. Остальные соединения — напрямую.') : 'После сохранения здесь появится описание маршрута для остального трафика.';
  if(isAndroid()) {
    $('#toggle-vless').textContent = state.vlessActive ? 'Отключить VPN' : 'Подключить';
    $('#vless-detail').textContent = state.vlessConfigured ? 'Весь интернет через сервер; исключения — напрямую.' : 'Добавьте подключение и выберите сервер.';
    $('#dpi-strategy-description').textContent=state.dpiTrial?.pendingConfirmation ? 'Запущен кандидат, он ещё не сохранён как рабочий. Проверьте видео и голос, затем выберите результат ниже.' : state.verifiedStrategy ? 'Профиль сохранён после вашей проверки. Можно запустить его или начать новую проверку вариантов.' : 'Профили проверяются вами по очереди: запустите вариант, откройте сервис и выберите результат ниже.';
    $('#dpi-trial-status').textContent=state.dpiTrial?.candidate ? 'Вариант '+state.dpiTrial.attempt+' из '+state.dpiTrial.total+': '+state.dpiTrial.name+'. Если не работает — нажмите следующий вариант.' : 'Здесь появятся кнопки оценки запущенного варианта. Повторный запуск сохранённого профиля сам по себе не ищет другой.';
    if(state.dpiTrial?.pendingConfirmation)status('#dpi-strategy-state','Нужна ваша проверка','waiting');
    $('#dpi-detail').textContent=state.active ? 'Локальный обход включён' : (state.verifiedStrategy ? 'Профиль сохранён' : 'Подберите профиль на вкладке DPI');
  }
  $('#autostart-toggle').checked = Boolean(state.autoStart);
  $('#autostart-description').textContent = `${state.autoStart ? 'При входе в Windows запустятся' : 'При включении будут запускаться'}: ${ready.join(', ') || 'пока нет настроенных компонентов'}. Приложение останется в трее.`;
  const items = state.diagnostics || [];
  $('#diagnostics').innerHTML = items.map((item) => `<div class="diag ${['ok', 'warning', 'neutral'].includes(item.level) ? item.level : 'neutral'}"><div class="diag-label"><i></i>${escapeHtml(item.label)}</div><div class="diag-value">${escapeHtml(item.value)}</div></div>`).join('');
  const logs = (state.logs || []).map(entry => String(entry).replace(/\u001b\[[0-?]*[ -/]*[@-~]/g, '').replace(/\[(?:\d+(?:;\d+)*)m/g, ''));
  const technical = (entry) => /\[(winws|vless|tgws)\]/.test(entry);
  const activity = logs.filter((entry) => !technical(entry) && !/(YouTube|Discord (API|Gateway|CDN|Updates)):/i.test(entry));
  $('#activity-log').innerHTML = activity.length ? activity.slice(0, 6).map((entry) => `<div class="activity-item">${escapeHtml(entry)}</div>`).join('') : '<p class="muted setting-description">Пока нет событий. Результаты запуска и проверки появятся здесь.</p>';
  $('#engine-log').textContent = logs.join('\n') || 'Нет событий.';
  renderServers();
  renderControls();
}
function serviceMarkup(catalog, selected, className = '') {
  return Object.entries(catalog).map(([key, label]) => `<label class="service"><input class="${className}" type="checkbox" value="${key}" ${selected.includes(key) ? 'checked' : ''}><span>${escapeHtml(label)}</span></label>`).join('');
}
function renderDomains(kind) {
  const domains = kind === 'dpi' ? dpiDraft.customDomains : vlessDraft.customDomains;
  $(kind === 'dpi' ? '#custom-domains' : '#vless-custom-domains').innerHTML = domains.map((domain, index) => `<span class="chip">${escapeHtml(domain)}<button data-domain-kind="${kind}" data-remove="${index}" aria-label="Удалить ${escapeHtml(domain)}">×</button></span>`).join('');
}
function renderApps() {
  const known = [...(state.appLibrary || VLESS_APP_LIBRARY)];
  vlessDraft.apps.forEach((process) => { if (!known.some((app) => app.process.toLowerCase() === process.toLowerCase())) known.push({ name: process, process }); });
  const query = $('#app-search').value.toLowerCase().trim();
  const matches = known.filter((app) => `${app.name} ${app.process}`.toLowerCase().includes(query));
  $('#vless-app-list').innerHTML = matches.map((app) => `<label class="service"><input class="vless-app" type="checkbox" value="${escapeHtml(app.process)}" ${vlessDraft.apps.some((name) => name.toLowerCase() === app.process.toLowerCase()) ? 'checked' : ''}><span>${escapeHtml(app.name)}<small>${escapeHtml(app.process)}</small></span></label>`).join('');
  $('#app-search-empty').classList.toggle('hidden', Boolean(matches.length));
  $('#vless-app-count').textContent = `${vlessDraft.apps.length} выбрано`;
}
function renderServers() {
  const servers=state.vlessServers || [];
  const groups=state.vlessSubscriptions?.length ? state.vlessSubscriptions : servers.length ? [{id:'legacy',name:'Мои серверы',serverIds:servers.map(item=>item.id),canRefresh:state.vlessSubscriptionSaved}] : [];
  const signature=JSON.stringify([servers,groups,state.vlessConfigured]);
  $('#subscription-empty').classList.toggle('hidden',Boolean(servers.length || state.vlessConfigured));
  $('#single-server-editor').classList.toggle('hidden',Boolean(servers.length));
  if(signature===librarySignature) return;
  librarySignature=signature;
  $('#vless-servers').innerHTML=groups.map(group=>'<section class="subscription-group"><div class="subscription-heading"><strong>'+escapeHtml(group.name)+'</strong><div class="subscription-tools">'+(group.canRefresh?'<button class="text-button refresh-subscription" data-refresh-subscription="'+escapeHtml(group.id)+'" aria-label="Обновить подписку '+escapeHtml(group.name)+'">Обновить</button>':'')+'<button class="text-button remove-subscription" data-remove-subscription="'+escapeHtml(group.id)+'" aria-label="Удалить подписку '+escapeHtml(group.name)+'">Удалить</button></div></div>'+servers.filter(item=>group.serverIds.includes(item.id)).map(item=>'<div class="server-row '+(item.selected?'selected':'')+'"><div class="server-description"><strong>'+escapeHtml(item.name)+'</strong><small>'+escapeHtml(item.server)+':'+escapeHtml(item.port)+'</small></div><button class="text-button select-server" data-server-id="'+escapeHtml(item.id)+'" '+(item.selected?'disabled':'')+'>'+(item.selected?'Выбран':'Выбрать')+'</button></div>').join('')+'</section>').join('');
}
function configurePlatform() {
  document.body.classList.toggle('platform-android',isAndroid());
  $('#settings-version').textContent=state.appVersion;
  const general=$('#general-platform-settings');
  const startup=document.querySelector('.autostart-panel');
  general.append(startup);
  if(!isAndroid()) return;
  general.textContent='';
  general.innerHTML='<p class="setting-description">Android запрашивает разрешение на локальный VPN при первом запуске. DPI и VPN используют один системный сервис.</p><div class="vless-actions"><button class="button outlined" data-android-tile="DPI">Плитка DPI</button><button class="button outlined" data-android-tile="TG">Плитка Telegram</button><button class="button outlined" data-android-tile="VLESS">Плитка VPN</button></div>';
  // Скрытая Windows-разметка остаётся для общего контроллера, но не входит в поток страницы.
  const windows=document.createElement('div');windows.className='hidden';windows.append(startup);
  $('#page-content').append(windows);
  const bypass=document.createElement('details');bypass.className='advanced';bypass.innerHTML='<summary>Приложения напрямую, вне VPN</summary><p class="setting-description">Выбранные приложения исключены из Android TUN. Остановите VPN перед изменением.</p><div class="app-list">'+(state.bypassApps || []).map(item=>'<label class="service"><input type="checkbox" class="bypass-app" value="'+escapeHtml(item.id)+'" '+(item.selected?'checked':'')+'><span>'+escapeHtml(item.name)+'</span></label>').join('')+'</div>';general.append(bypass);
  $('#android-dpi-profile').classList.remove('hidden');
  $('#android-dpi-feedback').classList.remove('hidden');
  $('#strategy-tests').closest('details').querySelector('.setting-description').textContent='Выбранный вариант включится для вашей проверки. Если видео или голос не работают, нажмите «Не работает — следующий». Рабочий профиль сохраняется только после подтверждения.';
  $('#dpi-command').value=state.dpiCommand || '';
  document.querySelector('[data-auto-select]').textContent='Начать проверку профилей';
  $('#manual-vless-app').placeholder='com.example.app';
  document.querySelector('label[for="manual-vless-app"]').textContent='Другое приложение · имя пакета';
  $('#pick-vless-app').classList.add('hidden');
  $('#export-diagnostics').textContent='Скопировать диагностику';
  document.querySelector('.component-subtitle').textContent='Обход DPI · ByeDPI';
  document.querySelector('.telegram-layout .page-intro')?.remove();
  document.querySelector('[data-page-panel="telegram"] > .page-intro').textContent='Запустите локальный прокси и подтвердите подключение в Telegram.';
  document.querySelector('footer').textContent='Local Route управляет подключениями на этом устройстве.';
  document.querySelector('#manual-vless-app').closest('details').querySelector('.setting-description').textContent='На Android правило выбирает пакет приложения. Домены DPI имеют приоритет перед правилом VPN на всё приложение.';
  const textNodes=document.createTreeWalker(document.querySelector('#page-content'),NodeFilter.SHOW_TEXT);
  while(textNodes.nextNode())textNodes.currentNode.textContent=textNodes.currentNode.textContent.replaceAll('компьютере','устройстве');
  configureAndroidSettings();
}

function configureAndroidSettings() {
  const settings = document.querySelector('[data-page-panel="settings"]');
  const quick = $('#general-platform-settings');
  quick.querySelector('p').textContent = 'Добавьте нужную кнопку в шторку Android.';
  quick.closest('section').querySelector('h2').textContent = 'Быстрый запуск';
  quick.closest('section').querySelector('.eyebrow').classList.add('hidden');
  const links = settings.querySelector('.project-links');
  const groups = {};
  for (const [mode, title] of [['lab','DPI · сервисы и профиль'], ['routes','VPN · сайты и приложения'], ['telegram','Telegram · параметры прокси']]) {
    const group = document.createElement('details');
    group.className = 'advanced mode-settings'; group.dataset.modeSettings = mode;
    const summary = document.createElement('summary'); summary.textContent = title; group.append(summary);
    const content = document.querySelector(`[data-page-panel="${mode}"] .service-settings`);
    if (content) group.append(content);
    settings.insertBefore(group, links); groups[mode] = group;
  }
  groups.lab.append($('#android-dpi-profile'));
  groups.routes.append(document.querySelector('.route-note'));
  groups.routes.append(document.querySelector('#general-platform-settings > details'));
  groups.lab.append($('#strategy-tests').closest('details'));
  const diagnostics = document.createElement('details');
  diagnostics.className = 'advanced diagnostics-settings';
  const summary = document.createElement('summary'); summary.textContent = 'Диагностика и журнал'; diagnostics.append(summary);
  diagnostics.append($('#diagnostics').closest('section'), $('#technical-log'));
  const events = $('#activity-log');
  events.previousElementSibling?.remove(); diagnostics.append(events);
  settings.insertBefore(diagnostics, links);
  $('#telegram-enabled').closest('.telegram-autostart').classList.add('hidden');
  $('#telegram-settings-hint').textContent = 'Порт можно изменить, когда прокси выключен. Затем подключите Telegram заново.';
  $('#vless-all-note').textContent = 'Интернет идёт через ваш сервер. Выбранные DPI-сервисы обрабатываются локально; исключения — напрямую.';
  all('[data-open-log]').forEach(button => { button.dataset.page = 'settings'; button.textContent = 'Диагностика →'; });
  // Android has one connection editor and an all-traffic model with exceptions.
  const exceptions = groups.routes.querySelector('.bypass-app')?.closest('details');
  if (exceptions) {
    exceptions.classList.add('vpn-exceptions');
    exceptions.querySelector('summary').textContent = 'Исключения VPN';
    exceptions.querySelector('p').textContent = 'Эти приложения подключаются напрямую. Для изменения остановите VPN. Если включён DPI, он тоже не обрабатывает исключённые приложения.';
    document.querySelector('.vless-connection-panel').after(exceptions);
  }
  groups.routes.classList.add('hidden');
  $('#open-subscription').textContent = 'Добавить подключение';
  $('#subscription-dialog h2').textContent = 'Добавить подключение';
  document.querySelector('label[for="subscription-input"]').textContent = 'Ссылка VLESS, HTTPS-подписка или список';
  $('#subscription-input').placeholder = 'vless://… или https://…';
  $('#auto-server').textContent = 'Проверить серверы';
  $('#test-vless').textContent = 'Проверить соединение';
  document.querySelector('[data-page-panel="routes"] > .page-intro').textContent = 'Весь интернет через ваш сервер. Исключения — напрямую, включённый DPI — локально.';
  document.querySelector('.vless-connection-panel > .info-note').classList.add('hidden');
  $('#vless-test-results').closest('section').classList.add('hidden');
  document.querySelector('.vless-connection-panel').append($('#vless-test-results'));
  $('#vless-test-results').textContent = '';
}

function renderEditor(kind) {
  if (kind === 'dpi') {
    $('#service-list').innerHTML = serviceMarkup(SERVICES, dpiDraft.services);
    $('#domain-count').textContent = domainCount(state.profile.domains.length);
    renderDomains('dpi');
  } else if (kind === 'vless') {
    renderServers();
    $('#vless-uri').value = vlessDraft.uri;
    $('#vless-mode').value = vlessDraft.mode;
    $('#vless-service-list').innerHTML = serviceMarkup(VLESS_SERVICES, vlessDraft.services, 'vless-service');
    $('#vless-domain-count').textContent = domainCount(state.vless?.domains?.length || 0);
    renderDomains('vless'); renderApps(); renderMode();
  } else {
    $('#telegram-port').value = telegramDraft.port;
    $('#telegram-enabled').checked = telegramDraft.enabled;
  }
  renderControls();
}
function renderMode() {
  $('#vless-selective-settings').classList.toggle('hidden', vlessDraft.mode === 'all');
  $('#vless-all-note').classList.toggle('hidden', vlessDraft.mode !== 'all');
}
async function refreshState() {
  if (readPending || pending) return;
  readPending = true;
  const version = revision;
  try {
    const next = await window.localRoute.readState();
    if (version !== revision) return;
    state = { ...state, ...next };
    renderState();
  } catch (error) { if (version === revision) showError(`Не удалось обновить состояние: ${error.message}`); }
  finally { readPending = false; }
}
function renderVlessReport(report) {
  const rows = report.results.map((item) => {
    const level = item.level || (item.success ? 'ok' : 'fail');
    const label = level === 'warning' ? 'Доступ ограничен' : (item.success ? 'Ответ получен' : 'Ошибка');
    return `<div class="test-result ${['ok', 'warning', 'fail'].includes(level) ? level : 'fail'}"><strong>${escapeHtml(item.label)} · ${label}</strong><span>${escapeHtml(item.detail)} · ${escapeHtml(item.durationMs)} мс</span></div>`;
  }).join('');
  const apps = report.appRules || [];
  $('#vless-test-results').innerHTML = `<div class="vless-check-list">${rows}</div><p class="setting-description">Это проверка соединения через сервер, а не полной работы приложений.</p>${!isAndroid() && apps.length ? `<details class="advanced"><summary>Правила приложений · ${apps.length}</summary><p class="setting-description">В профиль включены ${apps.map((app) => escapeHtml(app.process)).join(', ')}.</p></details>` : ''}`;
}
function renderDpiReport(report) {
  const summary = report.message ? `<p class="setting-description">${escapeHtml(report.message)}</p>` : '';
  $('#test-results').innerHTML = summary + report.results.map(item=>`<div class="test-result ${item.success ? 'ok' : 'fail'}"><strong>${escapeHtml(item.label || SERVICES[item.service])} · ${item.success ? 'Доступен' : item.stage==='dns' ? 'Ошибка DNS' : 'Ошибка соединения'}</strong><span>${escapeHtml(item.detail)}${item.durationMs ? ' · '+escapeHtml(item.durationMs)+' мс' : ''}</span><span>${escapeHtml(item.route || '')}</span></div>`).join('');
}
// All IPC actions restore controls even when Electron rejects the request.
// A cancelled DPI operation cannot overwrite a newer stop result.
async function perform(kind, message, action, onSuccess) {
  if (pending) return;
  const version = ++revision;
  pending = { kind, message };
  showError(''); renderControls();
  try {
    const result = await action();
    if (version !== revision) return;
    if (result.state) state = { ...state, ...result.state };
    if (kind === 'vless' && result.results) renderVlessReport(result);
    if (kind === 'dpi' && result.results) renderDpiReport(result);
    if (result.ok === false) {
      if (kind === 'vless' && result.results) { toast(result.message); return; }
      showError(result.message || 'Не удалось выполнить действие.');
      if (kind === 'dpi' && !result.results) $('#test-results').innerHTML = `<div class="test-result fail"><strong>Проверка завершена</strong><span>${escapeHtml(result.message)}</span></div>`;
      if (kind === 'telegram') $('#telegram-error').textContent = result.message;
      return;
    }
    if (onSuccess) await onSuccess(result);
    if (result.message) toast(result.message);
  } catch (error) {
    if (version === revision) {
      showError(`Не удалось выполнить действие: ${error.message}`);
      if (kind === 'dpi') $('#test-results').innerHTML = '<div class="test-result fail">Проверка прервана из-за ошибки. Подробности — в сообщении выше.</div>';
    }
  }
  finally {
    if (version === revision) {
      pending = null;
      renderState();
      await refreshState();
    }
  }
}
async function stopDpi(everything) {
  if (pending?.kind === 'stop' || (!everything && pending && pending.kind !== 'dpi')) return;
  // Invalidate an outstanding check before requesting cancellation.
  pending = null; revision++;
  let stopped = false;
  await perform('stop', 'Останавливаю компоненты…', async () => {
    const result = await window.localRoute[everything ? 'stopAll' : 'stopEngine']();
    return result.ok === false ? result : {state:result,message:everything ? 'DPI, VLESS и Telegram-прокси остановлены.' : 'DPI остановлен, подбор отменён.'};
  }, () => { stopped = true; });
  if (stopped) $('#test-results').innerHTML = '<div class="test-result">DPI остановлен. Можно запустить новую проверку.</div>';
}
function addDomain(kind) {
  const input = $(kind === 'dpi' ? '#custom-domain' : '#vless-custom-domain');
  const value = input.value.trim().toLowerCase().replace(/^https?:\/\//, '').replace(/^\*\./, '').replace(/\/.*$/, '');
  if (!/^(?!-)(?:[a-z0-9-]{1,63}\.)+[a-z]{2,63}$/.test(value) || value.length > 253) {
    input.setAttribute('aria-invalid', 'true'); return toast('Введите домен, например example.org. IP-адреса и порты не поддерживаются.');
  }
  const draft = kind === 'dpi' ? dpiDraft : vlessDraft;
  if (!draft.customDomains.includes(value)) draft.customDomains.push(value);
  input.value = ''; input.removeAttribute('aria-invalid'); renderDomains(kind); renderControls(); input.focus();
}
async function handleAction(button) {
  const id = button.id;
  if(id==='back-settings'){navigate(settingsReturnPage);return;}
  if(id==='open-subscription'){ $('#subscription-dialog').showModal(); $('#subscription-input').focus(); return; }
  if(button.dataset.refreshSubscription) return perform('vless','Обновляю подписку…',()=>window.localRoute.importVless({refresh:true,groupId:button.dataset.refreshSubscription}));
  if(button.dataset.removeSubscription) return perform('vless','Удаляю подписку…',()=>window.localRoute.removeSubscription(button.dataset.removeSubscription),()=>{resetDraft('vless');renderEditor('vless');});
  if(button.dataset.androidTile) return perform('tile','Добавляю плитку…',()=>window.localRoute.addTile(button.dataset.androidTile));
  if(id==='save-dpi-command') return perform('save','Сохраняю профиль DPI…',()=>window.localRoute.saveDpiCommand($('#dpi-command').value));
  if(id==='reject-dpi') return perform('dpi','Останавливаю неработающий вариант и запускаю следующий…',()=>window.localRoute.rejectDpi(),result=>{$('#test-results').textContent=result.message || '';});
  if(id==='confirm-dpi') return perform('save','Сохраняю подтверждение…',()=>window.localRoute.confirmDpi());
  if (button.dataset.page) { navigate(button.dataset.page); if (button.dataset.openLog) { const group = document.querySelector('.diagnostics-settings'); if (group) group.open = true; $('#technical-log').open=true; $('#technical-log').scrollIntoView({block:'start',behavior:'instant'}); } return; }
  if (!state) return;
  if (button.dataset.resetKind) { const kind=button.dataset.resetKind; resetDraft(kind); renderEditor(kind); return; }
  if (id === 'auto-server') return perform('vless','Проверяю серверы по HTTPS…',()=>window.localRoute.autoSelectServer(),()=>{resetDraft('vless');renderEditor('vless');});
  if (id === 'import-subscription' || id === 'refresh-subscription') return perform('vless', 'Загружаю список серверов…', () => window.localRoute.importVless({input:$('#subscription-input').value,refresh:id==='refresh-subscription'}), () => { $('#subscription-input').value=''; $('#subscription-dialog').close(); renderServers(); });
  if (button.dataset.serverId) return perform('vless','Выбираю сервер…',()=>window.localRoute.selectVless(button.dataset.serverId),()=>{renderEditor('vless');});
  if (id === 'pick-vless-app') return perform('pick', 'Выберите приложение…', () => window.localRoute.pickApplication(), result => {
    if (result.process && !vlessDraft.apps.some(name => name.toLowerCase()===result.process.toLowerCase())) vlessDraft.apps.push(result.process);
    $('#app-search').value=''; renderApps(); renderControls();
  });
  if (id === 'export-diagnostics') return perform('export', 'Сохраняю диагностику без секретов…', () => window.localRoute.exportDiagnostics());
  if (id === 'delete-vless') {
    if (isAndroid() && !await confirmAction('Удалить все подключения и подписки с этого устройства?', 'Удалить')) return;
    return perform('vless', 'Удаляю подключение…', () => window.localRoute.deleteVless(), () => { resetDraft('vless'); renderEditor('vless'); $('#vless-test-results').textContent='Подключения удалены.'; });
  }
  if (id === 'add-domain' || id === 'add-vless-domain') return addDomain(id === 'add-domain' ? 'dpi' : 'vless');
  if (button.dataset.domainKind) {
    const kind = button.dataset.domainKind;
    (kind === 'dpi' ? dpiDraft : vlessDraft).customDomains.splice(Number(button.dataset.remove), 1);
    renderDomains(kind); renderControls(); return;
  }
  if (id === 'add-vless-app') {
    const input = $('#manual-vless-app'); const value = input.value.trim();
    if (!(isAndroid() ? /^[a-zA-Z0-9_]+(?:\.[a-zA-Z0-9_]+)+$/ : /^[a-z0-9][a-z0-9._ -]{0,120}\.exe$/i).test(value)) return toast('Введите имя файла приложения, например my-app.exe.');
    if (!vlessDraft.apps.some((name) => name.toLowerCase() === value.toLowerCase())) vlessDraft.apps.push(value);
    input.value = ''; $('#app-search').value = ''; renderApps(); renderControls(); return;
  }
  if (id === 'save-dpi') return perform('save', 'Сохраняю сервисы DPI…', async () => {const result=await window.localRoute.saveProfile(clone(dpiDraft)); return result.ok===false ? result : {state:result,message:'Сервисы сохранены. Подберите стратегию для нового профиля.'};}, () => { resetDraft('dpi'); renderEditor('dpi'); });
  if (id === 'save-vless') return perform('vless', 'Проверяю и сохраняю профиль…', () => window.localRoute.saveVless(clone(vlessDraft)), () => { resetDraft('vless'); renderEditor('vless'); $('#vless-test-results').innerHTML = '<p class="setting-description">Профиль сохранён. Проверьте сервер и выбранные сайты.</p>'; });
  if (id === 'toggle-vless' || id === 'start-vless') return perform('vless', state.vlessActive ? 'Останавливаю VLESS…' : 'Запускаю VLESS…', () => state.vlessActive ? window.localRoute.toggleVless() : window.localRoute.startVless());
  if (id === 'test-vless') return perform('vless', 'Проверяю сервер и выбранные сайты через VLESS…', () => window.localRoute.testVless());
  if (id === 'start-all') return perform('all', 'Запускаю настроенные компоненты…', () => window.localRoute.startAll());
  if (id === 'stop-all') return stopDpi(true);
  if (id === 'start-dpi' || id === 'start-saved-dpi') return state.active ? stopDpi(false) : perform('start', 'Запускаю сохранённую стратегию DPI…', () => window.localRoute.startVerified());
  if (button.matches('.stop-engine')) return stopDpi(false);
  if (button.hasAttribute('data-auto-select') || button.dataset.strategy) {
    const strategy = button.dataset.strategy;
    $('#test-results').innerHTML = '<div class="test-result"><strong>Проверка выполняется</strong><span>Можно переключать вкладки. Для отмены нажмите «Остановить DPI».</span></div>';
    return perform('dpi', strategy ? `Проверяю ${strategyLabel(strategy)}…` : 'Подбираю стратегию DPI. Это может занять несколько минут…', () => strategy ? window.localRoute.testStrategy(strategy) : window.localRoute.autoSelect(), (result) => {
      if (result.results) renderDpiReport(result);
      else $('#test-results').innerHTML = `<div class="test-result ${result.ok ? 'ok' : 'fail'}"><strong>${result.pendingConfirmation ? 'Проверьте работу DPI' : result.ok ? 'Стратегия подобрана' : 'Проверка завершена'}</strong><span>${escapeHtml(result.message)}</span></div>`;
      if(isAndroid() && result.pendingConfirmation && currentPage==='lab')$('#android-dpi-feedback').scrollIntoView({block:'center',behavior:'smooth'});
    });
  }
  if (id === 'start-telegram' || id.startsWith('telegram-')) {
    if (id === 'telegram-save' && !$('#telegram-port').reportValidity()) return;
    const stop = id === 'telegram-stop' || (id === 'start-telegram' && state.telegramActive);
    const action = id === 'telegram-save' ? () => window.localRoute.saveTelegram(clone(telegramDraft)) : stop ? () => window.localRoute.stopTelegram() : (id === 'telegram-start' || id === 'start-telegram') ? () => window.localRoute.startTelegram() : id === 'telegram-copy' ? () => window.localRoute.copyTelegram() : () => window.localRoute.connectTelegram();
    return perform('telegram', stop ? 'Останавливаю Telegram-прокси…' : 'Выполняю действие Telegram…', action, (result) => {
      if (id === 'telegram-save') { resetDraft('telegram'); renderEditor('telegram'); }
      $('#telegram-result').textContent = result.message || '';
    });
  }
  if (id === 'run-check' || id === 'refresh-log') { await refreshState(); return; }
  const links = { 'open-release-channel': 'https://t.me/TheLocalRoute', 'open-max-channel': 'https://max.ru/channel_LocalRoute', 'open-repository': 'https://github.com/iKokTot/local-route' };
  if (links[id]) await window.localRoute.open(links[id]);
}
document.addEventListener('click', (event) => {
  const button = event.target.closest('button');
  if (!button || button.disabled) return;
  handleAction(button).catch((error) => showError(error.message));
});
document.addEventListener('change', (event) => {
  if (!state) return;
  const input = event.target;
  if(input.matches('.bypass-app')) { perform('save','Сохраняю исключения…',()=>window.localRoute.saveBypassApps(all('.bypass-app:checked').map(node=>node.value))); return; }
  if (input.matches('#service-list input')) dpiDraft.services = all('#service-list input:checked').map((node) => node.value);
  if (input.matches('.vless-service')) vlessDraft.services = all('.vless-service:checked').map((node) => node.value);
  if (input.matches('.vless-app')) {
    vlessDraft.apps = vlessDraft.apps.filter((name) => name.toLowerCase() !== input.value.toLowerCase());
    if (input.checked) vlessDraft.apps.push(input.value);
    $('#vless-app-count').textContent = `${vlessDraft.apps.length} выбрано`;
  }
  if (input.id === 'vless-mode') { vlessDraft.mode = input.value; renderMode(); }
  if (input.id === 'telegram-enabled') telegramDraft.enabled = input.checked;
  if (input.id === 'autostart-toggle') perform('save', 'Сохраняю автозапуск…', () => window.localRoute.setAutoStart(input.checked));
  renderControls();
});
document.addEventListener('input', (event) => {
  if (!state) return;
  if (event.target.id === 'vless-uri') vlessDraft.uri = event.target.value.trim();
  if (event.target.id === 'telegram-port') telegramDraft.port = Number(event.target.value);
  if (event.target.id === 'app-search') renderApps();
  renderControls();
});
document.addEventListener('keydown', (event) => {
  if (event.key !== 'Enter' || event.isComposing) return;
  const actions = { 'custom-domain': 'add-domain', 'vless-custom-domain': 'add-vless-domain', 'manual-vless-app': 'add-vless-app' };
  const action = actions[event.target.id];
  if (action) { event.preventDefault(); $(`#${action}`).click(); }
});
async function initialize() {
  renderControls();
  try {
    state = await window.localRoute.readState();
    configurePlatform();
    $('#app-version').textContent = state.appVersion || '';
    Object.assign(SERVICES,state.catalog.dpiServices);
    Object.assign(VLESS_SERVICES,state.catalog.vlessServices);
    Object.assign(STRATEGY_LABELS,Object.fromEntries(state.catalog.strategies.map(item=>[item.id,item.label])));
    UPSTREAM_STRATEGY_IDS.push(...state.catalog.autoStrategyIds);
    for (const kind of ['dpi', 'vless', 'telegram']) resetDraft(kind);
    $('#strategy-tests').innerHTML = UPSTREAM_STRATEGY_IDS.map((id) => `<button class="button outlined test-strategy" data-strategy="${id}">${STRATEGY_LABELS[id]}</button>`).join('');
    $('#strategy-count').textContent = String(UPSTREAM_STRATEGY_IDS.length);
    for (const kind of ['dpi', 'vless', 'telegram']) renderEditor(kind);
    renderState();
  } catch (error) { showError(`Не удалось загрузить состояние приложения: ${error.message}. Перезапустите Local Route.`); }
}
window.localRoute.onProgress?.((operation) => {
  if (!state) return;
  state.operation=operation;
  renderControls();
  if (!operation && !pending) refreshState();
});
window.addEventListener('focus', () => { if (state) refreshState(); });
// Local process status only. Editors are never rebuilt by a background refresh.
setInterval(() => { if (state && !document.hidden) refreshState(); }, 15000);
initialize();
