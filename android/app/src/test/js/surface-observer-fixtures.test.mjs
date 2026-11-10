import assert from 'node:assert/strict';
import fs from 'node:fs';

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

await new Promise((resolve, reject) => {
  ws.onopen = resolve;
  ws.onerror = reject;
});

const expression = `(() => {
  const source = ${JSON.stringify(asset)};
  const run = (html) => {
    const frame = document.createElement('iframe');
    frame.style.display = 'none';
    document.body.appendChild(frame);
    const doc = frame.contentDocument;
    doc.body.innerHTML = html;
    frame.contentWindow.eval(source);
    const snapshot = frame.contentWindow.__mspSurface.snapshot();
    frame.remove();
    return snapshot;
  };
  const music = run('<div data-testid="floating-now-playing-bar">Advertisement\\nSome Artist</div>');
  const ad = run('<div data-testid="npv-container"><button>Pelajari lebih lanjut</button><div data-testid="npv-progress-bar-time-labels">0:04 0:00</div></div>');
  const audioAd = run('<div data-testid="npv-container"><button aria-label="Tutup"></button><button aria-label="Berikutnya"></button><div>Mengubah progres</div><div data-testid="npv-progress-bar-time-labels">0:05 0:00</div></div>');
  const cssCover = run('<div data-testid="npv-container"><div data-testid="npv-cover-art" style="background-image: url(&quot;https://i.scdn.co/image/current&quot;)"></div><div data-testid="npv-cover-art" style="background-image: url(&quot;https://i.scdn.co/image/next&quot;)"></div><a href="/track/abc">Current Song</a><a href="/artist/def">Some Artist</a></div>');
  const imgFallback = run('<div data-testid="floating-now-playing-bar"><img src="https://i.scdn.co/image/fallback"><a href="/track/x">Img Song</a></div>');
  return JSON.stringify({ music, ad, audioAd, cssCover, imgFallback });
})()`;

const response = await send('Runtime.evaluate', { expression, returnByValue: true });
if (response.result.exceptionDetails) {
  throw new Error(response.result.exceptionDetails.exception?.description || 'fixture evaluation failed');
}
const { music, ad, audioAd, cssCover, imgFallback } = JSON.parse(response.result.result.value);

assert.equal(music.isAd, false, 'a track titled Advertisement must not be classified as an ad');
assert.equal(music.state.track.name, 'Advertisement');
assert.equal(ad.isAd, true, 'Premium CTA must still be classified as an ad');
assert.equal(audioAd.isAd, true, 'audio ad without CTA or artwork must be classified as an ad');
assert.equal(audioAd.state.track.name, 'Iklan', 'UI progress label must not become the ad title');
assert.equal(cssCover.isAd, false, 'a track with CSS cover art must not be classified as an ad');
assert.equal(cssCover.state.track.name, 'Current Song');
assert.equal(cssCover.state.track.image, 'https://i.scdn.co/image/current', 'NPV cover is a CSS background div; the first (current) cover must be read, not the queued next cover');
assert.equal(imgFallback.state.track.image, 'https://i.scdn.co/image/fallback', 'an <img> cover must still be read when present');

console.log('surface_observer_fixtures_passed', JSON.stringify({ musicIsAd: music.isAd, adIsAd: ad.isAd, audioAdIsAd: audioAd.isAd, cssCoverImage: cssCover.state.track.image, imgFallbackImage: imgFallback.state.track.image }));
ws.close();
process.exit(0);