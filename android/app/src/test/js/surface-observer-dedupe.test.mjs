import assert from 'node:assert/strict';
import fs from 'node:fs';

const pages = await (await fetch('http://127.0.0.1:9222/json/list')).json();
const page = pages.find((p) => p.type === 'page' && p.url.includes('open.spotify.com'));
assert(page, 'open.spotify.com WebView page not found');

const asset = fs.readFileSync('android/app/src/main/assets/myspoti-surface.js', 'utf8');
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

const expression = `(() => {
  const source = ${JSON.stringify(asset)};
  const frame = document.createElement('iframe');
  frame.style.display = 'none';
  document.body.appendChild(frame);
  const doc = frame.contentDocument;
  doc.body.innerHTML = '<div data-testid="floating-now-playing-bar"><span>Track Name</span><span>Artist Name</span><div style="--finalAnimationDuration: 100000ms; --animationDelay: -1000ms; --animationPlayState: running;"></div></div>';
  const win = frame.contentWindow;
  let posts = 0;
  win.MyspotiHost = { postMessage: function () { posts++; } };
  win.eval(source);
  for (let i = 0; i < 5; i++) win.__mspSurface.tick();
  const burst = posts;
  doc.querySelector('span').textContent = 'Changed Track';
  win.__mspSurface.tick();
  const afterChange = posts;
  frame.remove();
  return JSON.stringify({ burst, afterChange });
})()`;

const response = await send('Runtime.evaluate', { expression, returnByValue: true });
if (response.result.exceptionDetails) {
  throw new Error(response.result.exceptionDetails.exception?.description || 'fixture evaluation failed');
}
const { burst, afterChange } = JSON.parse(response.result.result.value);

assert.equal(burst, 1, 'identical state within the same second must post once, not once per mutation tick');
assert.equal(afterChange, 2, 'a metadata change must still post immediately');

console.log('surface_observer_dedupe_passed', JSON.stringify({ burst, afterChange }));
ws.close();
process.exit(0);