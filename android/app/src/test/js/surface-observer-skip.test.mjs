import assert from 'node:assert/strict';
import fs from 'node:fs';

// Notification skip must work when the Now Playing View is minimized to the
// floating bar. In that state Spotify removes the skip buttons from the DOM;
// the only way to reach them is to expand the NPV (cover click) and click once
// rendered. Run the asset actually shipped by the native host.
const pages = await (await fetch('http://127.0.0.1:9222/json/list', { signal: AbortSignal.timeout(5000) })).json();
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
await new Promise((resolve, reject) => { ws.onopen = resolve; ws.onerror = reject; });

const expression = `(async () => {
  const source = ${JSON.stringify(asset)};
  const fresh = (html) => {
    const frame = document.createElement('iframe');
    frame.style.display = 'none';
    document.body.appendChild(frame);
    frame.contentDocument.body.innerHTML = html;
    return frame;
  };

  // Minimized: floating bar with a cover; clicking the cover renders the skip
  // button (stands in for Spotify expanding the NPV).
  const minFrame = fresh('<div data-testid="floating-now-playing-bar"><img src="https://i.scdn.co/image/x"></div>');
  const minDoc = minFrame.contentDocument;
  let minClicked = false;
  minDoc.querySelector('img').addEventListener('click', () => {
    const b = minDoc.createElement('button');
    b.setAttribute('aria-label', 'Berikutnya');
    b.setAttribute('data-testid', 'npv-skip-forward-button');
    b.addEventListener('click', () => { minClicked = true; });
    minDoc.body.appendChild(b);
  });
  minFrame.contentWindow.eval(source);
  const minRet = minFrame.contentWindow.MyspotiBridge.next();
  await new Promise((r) => setTimeout(r, 400));
  const minimized = { ret: minRet, clicked: minClicked };
  minFrame.remove();

  // Open NPV: skip button already present, must be clicked synchronously.
  const openFrame = fresh('<div data-testid="npv-container"><button aria-label="Berikutnya" data-testid="npv-skip-forward-button"></button></div>');
  let openClicked = false;
  openFrame.contentDocument.querySelector('button').addEventListener('click', () => { openClicked = true; });
  openFrame.contentWindow.eval(source);
  const openRet = openFrame.contentWindow.MyspotiBridge.next();
  const open = { ret: openRet, clicked: openClicked };
  openFrame.remove();

  // Neither skip button nor floating bar: nothing to do.
  const noneFrame = fresh('<div>nothing</div>');
  noneFrame.contentWindow.eval(source);
  const none = { ret: noneFrame.contentWindow.MyspotiBridge.next() };
  noneFrame.remove();

  return JSON.stringify({ minimized, open, none });
})()`;

const response = await send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true });
if (response.result.exceptionDetails) {
  throw new Error(response.result.exceptionDetails.exception?.description || 'skip fixture evaluation failed');
}
const { minimized, open, none } = JSON.parse(response.result.result.value);

assert.equal(open.ret, true, 'open NPV: next must report success');
assert.equal(open.clicked, true, 'open NPV: skip button must be clicked synchronously');
assert.equal(none.ret, false, 'no bar and no button: next must report failure');
assert.equal(minimized.ret, true, 'minimized: next must expand the NPV and report success');
assert.equal(minimized.clicked, true, 'minimized: the rendered skip button must be clicked after expansion');

console.log('surface_observer_skip_passed', JSON.stringify({ minimized, open, none }));
ws.close();
process.exit(0);