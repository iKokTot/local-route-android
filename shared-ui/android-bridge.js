// Один интерфейс для Electron и Android; WebView передаёт команды нативному хосту.
if (!window.localRoute && window.LocalRouteNative) {
  const waiting = new Map();
  let sequence = 0;
  window.localRouteReply = (id, result) => {
    const entry = waiting.get(id);
    if (!entry) return;
    waiting.delete(id); clearTimeout(entry.timeout);
    entry.resolve(result);
  };
  window.localRoute = new Proxy({}, {get: (_target, method) => {
    if (method === 'onProgress') return () => () => {};
    return (payload) => new Promise((resolve,reject) => {
      const id = ++sequence;
      const timeout = setTimeout(()=>{waiting.delete(id);reject(new Error('Приложение не ответило вовремя. Проверьте состояние подключения.'));}, 150000);
      waiting.set(id,{resolve,reject,timeout});
      window.LocalRouteNative.postMessage(JSON.stringify({id,method,payload:payload ?? null}));
    });
  }});
  // Кнопка Android «Назад» закрывает форму, затем общие настройки, затем возвращает обзор.
  window.localRouteBack = () => {
    if (document.querySelector('#subscription-dialog')?.open) { document.querySelector('#subscription-dialog').close(); return true; }
    if (typeof currentPage !== 'undefined' && currentPage !== 'control') {
      if (currentPage === 'settings') document.querySelector('#back-settings').click();
      else navigate('control');
      return true;
    }
    return false;
  };
}
