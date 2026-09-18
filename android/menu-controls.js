(() => {
  if (window.foldOpenPanel) return;
  const panels = ['data-panel', 'location-bar', 'control-panel', 'pp-toggles', 'scene-panel', 'global-context-panel', 'cctv-panel', 'top-center-actions'];
  document.documentElement.dataset.foldMenu = 'true';
  const close = document.createElement('button');
  close.id = 'fold-panel-close';
  close.type = 'button';
  close.textContent = 'Close ✕';
  close.hidden = true;
  close.setAttribute('aria-label', 'Close controls and return to globe');
  document.body.append(close);
  let current = null;
  let observer = null;
  let pinned = false;
  window.foldClosePanel = () => {
    if (!current) return false;
    observer?.disconnect();
    current.classList.remove('fold-menu-panel');
    current.classList.toggle('dock-pinned', pinned);
    current = null;
    close.hidden = true;
    return true;
  };
  window.foldOpenPanel = id => {
    if (!panels.includes(id)) return false;
    const panel = document.getElementById(id);
    if (!panel) return false;
    window.foldClosePanel();
    current = panel;
    pinned = panel.classList.contains('dock-pinned');
    panel.classList.add('fold-menu-panel', 'active');
    if (panel.classList.contains('collapsed')) {
      const toggle = panel.querySelector('[data-dock-toggle-target="' + id + '"], [data-collapse-target="' + id + '"]');
      toggle?.click();
      panel.classList.remove('collapsed');
    }
    if (id === 'location-bar' || id === 'control-panel') panel.classList.add('dock-pinned');
    observer = new MutationObserver(() => {
      if (current === panel && panel.classList.contains('collapsed')) panel.classList.remove('collapsed');
    });
    observer.observe(panel, {attributes: true, attributeFilter: ['class']});
    close.hidden = false;
    panel.scrollTop = 0;
    panel.querySelector('input, select, button')?.focus({preventScroll: true});
    return true;
  };
  close.addEventListener('click', () => window.foldClosePanel());
  document.addEventListener('keydown', event => {
    if (event.key === 'Escape' && window.foldClosePanel()) event.preventDefault();
  });
  document.addEventListener('pointerdown', event => {
    if (current && !current.contains(event.target) && !close.contains(event.target)) window.foldClosePanel();
  });
})();
