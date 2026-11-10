import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

// Run the script actually shipped by the native host, not a copy of its selectors.
const hostUrl = new URL('../../main/java/com/cpotify/app/myspoti/WebPlayerHost.java', import.meta.url);
const host = fs.readFileSync(hostUrl, 'utf8');
const constant = /private static final String COSMETIC_FILTER_SCRIPT =([\s\S]*?);\r?\n/.exec(host);
assert(constant, 'native cosmetic script not found');
const script = constant[1].match(/"(?:[^"\\]|\\.)*"/g).map(s => JSON.parse(s)).join('');
const endpoint = process.env.CDP_HTTP_URL || 'http://127.0.0.1:9222';
const pages = await (await fetch(`${endpoint}/json/list`, { signal: AbortSignal.timeout(5000) })).json();
const page = pages.find(p => p.type === 'page' && new URL(p.url).hostname === 'open.spotify.com');
assert(page, 'Spotify WebView must be attached through CDP_HTTP_URL');

const expression = `(() => {
  const frame = document.createElement('iframe');
  frame.style.display = 'none';
  document.body.appendChild(frame);
  try {
    const win = frame.contentWindow;
    const doc = frame.contentDocument;
    doc.body.innerHTML = '<main><div data-testid="npv-header">Now playing</div>' +
      '<div data-testid="track-metadata">Advertisement</div>' +
      '<button aria-label="Advertisement">Play track</button>' +
      '<div class="ad-banner">Promotion</div>' +
      '<div data-testid="premium-banner">Premium upsell</div></main>';
    const parent = doc.querySelector('main');
    const header = doc.querySelector('[data-testid="npv-header"]');
    const title = doc.querySelector('[data-testid="track-metadata"]');
    const button = doc.querySelector('button');
    const ad = doc.querySelector('.ad-banner');
    const premium = doc.querySelector('[data-testid="premium-banner"]');
    win.eval(${JSON.stringify(script)});
    const before = {
      headerAttached: header.parentNode === parent,
      headerDisplay: win.getComputedStyle(header).display,
      titleAttached: title.parentNode === parent,
      titleDisplay: win.getComputedStyle(title).display,
      buttonDisplay: win.getComputedStyle(button).display,
      adAttached: ad.parentNode === parent,
      adDisplay: win.getComputedStyle(ad).display,
      premiumAttached: premium.parentNode === parent,
      premiumDisplay: win.getComputedStyle(premium).display
    };
    const nodesBefore = doc.querySelectorAll('*').length;
    for (let i = 0; i < 20; i++) win.eval(${JSON.stringify(script)});
    const nodesAfter = doc.querySelectorAll('*').length;
    const laterAd = doc.createElement('div');
    laterAd.className = 'ad-banner';
    parent.appendChild(laterAd);
    const dynamicAdDisplay = win.getComputedStyle(laterAd).display;
    let reconciliationError = null;
    try {
      parent.removeChild(header);
      parent.removeChild(title);
      parent.removeChild(ad);
    } catch (error) { reconciliationError = error.name + ': ' + error.message; }
    return { before, nodesBefore, nodesAfter, dynamicAdDisplay, reconciliationError };
  } finally { frame.remove(); }
})()`;

const ws = new WebSocket(page.webSocketDebuggerUrl);
let result;
try {
  result = await new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('Cosmetic fixture timed out after 12s')), 12000);
    ws.onopen = () => ws.send(JSON.stringify({ id: 1, method: 'Runtime.evaluate', params: { expression, returnByValue: true, timeout: 8000 } }));
    ws.onerror = () => { clearTimeout(timer); reject(new Error('CDP socket error')); };
    ws.onmessage = event => {
      const message = JSON.parse(event.data);
      if (message.id !== 1) return;
      clearTimeout(timer);
      if (message.error || message.result?.exceptionDetails) reject(new Error(JSON.stringify(message)));
      else resolve(message.result.result.value);
    };
  });
} finally { ws.close(); }

// Reintroducing the substring selector or text-based removal must break these.
test('preserves player header and track metadata, including a song named Advertisement', () => {
  assert.equal(result.before.headerAttached, true);
  assert.equal(result.before.headerDisplay, 'block');
  assert.equal(result.before.titleAttached, true);
  assert.equal(result.before.titleDisplay, 'block');
  assert.notEqual(result.before.buttonDisplay, 'none');
});

test('hides dedicated ad banners without detaching React-owned nodes', () => {
  assert.equal(result.before.adAttached, true);
  assert.equal(result.before.adDisplay, 'none');
});

test('hides the home Premium upsell banner without detaching it', () => {
  assert.equal(result.before.premiumAttached, true);
  assert.equal(result.before.premiumDisplay, 'none');
});

test('allows the page to reconcile its own children without NotFoundError', () => {
  assert.equal(result.reconciliationError, null);
});

test('hides newly inserted banners without another scan or injection', () => {
  assert.equal(result.dynamicAdDisplay, 'none');
});

test('repeated page-finish injection does not accumulate DOM nodes', () => {
  assert.equal(result.nodesAfter, result.nodesBefore);
});
