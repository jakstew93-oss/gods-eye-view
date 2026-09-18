(() => {
  const ids = ['flights', 'military', 'satellites'];
  window.foldLiveStart = () => {
    const buttons = ids.map(id => document.querySelector('[data-layer-id="' + id + '"] .data-toggle-btn'));
    if (buttons.some(button => !button)) return false;
    buttons.forEach(button => {
      if (!button.classList.contains('active') && button.getAttribute('aria-disabled') !== 'true') button.click();
    });
    document.getElementById('data-panel')?.classList.add('active');
    return true;
  };
  window.foldLiveCheck = async () => {
    window.foldLiveDiagnostic = {state: 'Checking feeds…', results: []};
    const feeds = [
      ['Aircraft near Leicester', '/api/opensky?lat=52.6&lon=-1.2', 'states'],
      ['Military aircraft', '/api/adsblol/mil', 'ac'],
      ['Satellite catalog', '/api/celestrak/stations', 'tle']
    ];
    const results = await Promise.all(feeds.map(async ([name, path, key]) => {
      try {
        const response = await fetch(path, {signal: AbortSignal.timeout(40000)});
        if (!response.ok) {
          const body = await response.json().catch(() => ({}));
          return {name, status: response.status, detail: body.error || 'Feed unavailable'};
        }
        const body = key === 'tle' ? await response.text() : await response.json();
        const count = key === 'tle' ? body.split(/\r?\n/).filter(line => line.startsWith('1 ')).length : body[key]?.length;
        return {name, status: response.status, count: count ?? 0};
      } catch (error) { return {name, status: 0, detail: error.message}; }
    }));
    window.foldLiveDiagnostic = {state: 'Feed check complete', results};
  };
  if (document.documentElement.dataset.bundledGlobe === 'true' && localStorage.getItem('fold-live-enabled-v3') !== 'yes') {
    let attempts = 0;
    const timer = setInterval(() => {
      if (window.foldLiveStart()) {
        localStorage.setItem('fold-live-enabled-v3', 'yes');
        clearInterval(timer);
      } else if (++attempts >= 240) clearInterval(timer);
    }, 250);
  }
})();
