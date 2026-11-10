import assert from 'node:assert/strict';
import fs from 'node:fs';

const base = 'http://127.0.0.1:9222';
const pages = await (await fetch(`${base}/json/list`)).json();
const page = pages.find((p) => p.type === 'page' && p.url.includes('open.spotify.com'));
assert(page, 'open.spotify.com WebView page not found');

const ws = new WebSocket(page.webSocketDebuggerUrl);
let nextId = 0;
const pending = new Map();

function send(method, params = {}) {
  const id = ++nextId;
  return new Promise((resolve, reject) => {
    pending.set(id, { resolve, reject });
    ws.send(JSON.stringify({ id, method, params }));
  });
}

async function evaluate(expression) {
  const response = await send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true });
  if (response.error) throw new Error(JSON.stringify(response.error));
  if (response.result.exceptionDetails) {
    throw new Error(response.result.exceptionDetails.exception?.description || 'page evaluation failed');
  }
  return response.result.result.value;
}

const timer = setTimeout(() => {
  console.error('surface_observer_probe_timeout');
  process.exit(1);
}, 20000);

ws.onmessage = (event) => {
  const message = JSON.parse(event.data);
  const waiter = pending.get(message.id);
  if (!waiter) return;
  pending.delete(message.id);
  if (message.error) waiter.reject(new Error(JSON.stringify(message.error)));
  else waiter.resolve(message);
};

await new Promise((resolve, reject) => {
  ws.onopen = resolve;
  ws.onerror = reject;
});

const asset = fs.readFileSync('android/app/src/main/assets/myspoti-surface.js', 'utf8');
await evaluate(asset);
const raw = await evaluate('JSON.stringify(window.__mspSurface && window.__mspSurface.snapshot ? window.__mspSurface.snapshot() : null)');
const snapshot = JSON.parse(raw);

assert(snapshot, 'observer snapshot missing');
assert.equal(snapshot.isAd, true, 'paused first-party Premium ad was not detected');
assert.equal(snapshot.state.track.kind, 'ad');
assert.equal(snapshot.state.isPaused, true);
assert(snapshot.controls && typeof snapshot.controls.play === 'boolean', 'control availability missing');

clearTimeout(timer);
console.log('surface_observer_probe_passed', JSON.stringify(snapshot));
ws.close();
process.exit(0);