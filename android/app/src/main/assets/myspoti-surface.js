// Surface observer for the Spotify Web Player.
// Reads visible player state instead of private React registries and posts the
// existing {type:"state"} contract to MyspotiHost. Ad mitigation is best-effort.
(function () {
  if (window.__mspSurface && window.__mspSurface.version) return;

  var AD_TEXT = /pelajari lebih lanjut|jelajahi premium|learn more|explore premium|advertisement|sponsored|iklan/i;
  var PLAY_LABEL = /putar|play/i;
  var PAUSE_LABEL = /jeda|pause/i;
  var NEXT_LABEL = /berikutnya|selanjutnya|next/i;
  var PREV_LABEL = /sebelumnya|previous/i;
  var CLOSE_LABEL = /tutup|close/i;
  var adMitigated = false;

  function post(msg) {
    try {
      if (window.MyspotiHost && typeof window.MyspotiHost.postMessage === 'function') {
        window.MyspotiHost.postMessage(JSON.stringify(msg));
      }
    } catch (e) {}
  }

  function text(el) {
    return el ? String(el.innerText || el.textContent || '').trim() : '';
  }

  function root() {
    return document.querySelector('[data-testid="npv-container"]') ||
      document.querySelector('[data-testid="floating-now-playing-bar"]');
  }

  function parseTime(value) {
    var m = /(\d+):(\d{2})(?::(\d{2}))?/.exec(value || '');
    if (!m) return 0;
    if (m[3]) return ((+m[1]) * 3600 + (+m[2]) * 60 + (+m[3])) * 1000;
    return ((+m[1]) * 60 + (+m[2])) * 1000;
  }

  function buttons() {
    return Array.prototype.slice.call(document.querySelectorAll('button[aria-label]'));
  }

  function findButton(re, scope) {
    var list = scope ? Array.prototype.slice.call(scope.querySelectorAll('button[aria-label]')) : buttons();
    return list.find(function (b) {
      return re.test(b.getAttribute('aria-label') || '') && !b.disabled;
    }) || null;
  }

  function playButton() {
    return document.querySelector('[data-testid="npv-play-button"]') ||
      document.querySelector('[data-testid="play-button"]') ||
      findButton(PLAY_LABEL) || findButton(PAUSE_LABEL);
  }

  function progressBar() {
    return document.querySelector('[data-testid="progress-bar"]') ||
      document.querySelector('[data-testid="npv-progress-bar-wrapper"] [role="slider"]');
  }

  function snapshot() {
    var r = root();
    var rootText = text(r);
    var titleEl = r && r.querySelector('a[href*="/track/"]');
    var artistEl = r && r.querySelector('a[href*="/artist/"]');
    var img = r && r.querySelector('img[src]');
    var title = text(titleEl);
    var artist = text(artistEl);
    var image = img ? img.getAttribute('src') : null;
    if (!image && r) {
      // Spotify renders the NPV cover as a CSS background div, not an <img>.
      var cover = r.querySelector('[data-testid="npv-cover-art"]');
      var bg = cover ? getComputedStyle(cover).backgroundImage : '';
      var m = /url\(["']?([^"')]+)["']?\)/.exec(bg);
      if (m) image = m[1];
    }
    var ctaPresent = false;
    if (r) {
      var ctaEls = r.querySelectorAll('button,a');
      for (var i = 0; i < ctaEls.length; i++) {
        if (AD_TEXT.test(text(ctaEls[i]))) { ctaPresent = true; break; }
      }
    }
    var lines = rootText.split(/\n+/).map(function (s) { return s.trim(); }).filter(function (s) {
      return s && !/^\d+:\d{2}/.test(s);
    });
    if (!ctaPresent) {
      if (!title && lines.length) title = lines[0];
      if (!artist && lines.length > 1) artist = lines[1];
    }

    var positionMs = 0;
    var durationMs = 0;
    var labels = r && r.querySelector('[data-testid="npv-progress-bar-time-labels"]');
    var parts = text(labels).split(/\s+/).filter(Boolean);
    if (parts.length >= 2) {
      positionMs = parseTime(parts[0]);
      durationMs = parseTime(parts[parts.length - 1]);
    }
    var bar = progressBar();
    if (bar) {
      var now = Number(bar.getAttribute('aria-valuenow'));
      var max = Number(bar.getAttribute('aria-valuemax'));
      if (!Number.isNaN(now) && now > 0) positionMs = now;
      if (!Number.isNaN(max) && max > 0) durationMs = max;
    }
    var styleEls = r ? Array.prototype.slice.call(r.querySelectorAll('[style]')) : [];
    var progressEl = styleEls.find(function (e) {
      return (e.getAttribute('style') || '').indexOf('--finalAnimationDuration') >= 0;
    });
    if (progressEl) {
      var durMs = null;
      var delayMs = null;
      (progressEl.getAttribute('style') || '').split(';').forEach(function (part) {
        var kv = part.split(':');
        if (kv.length < 2) return;
        var key = kv[0].trim();
        var value = kv[1].trim();
        if (key === '--finalAnimationDuration' && value.indexOf('ms') === value.length - 2) durMs = parseFloat(value);
        if (key === '--animationDelay' && value.indexOf('ms') === value.length - 2) delayMs = parseFloat(value);
      });
      if (durMs !== null && isFinite(durMs)) durationMs = Math.max(0, Math.round(durMs));
      if (delayMs !== null && isFinite(delayMs)) positionMs = Math.max(0, Math.round(-delayMs));
      if (durationMs > 0 && positionMs > durationMs) positionMs = durationMs;
    }

    var pb = playButton();
    var label = pb ? (pb.getAttribute('aria-label') || '') : '';
    var isPaused = PAUSE_LABEL.test(label) ? false : PLAY_LABEL.test(label);
    if (!pb) {
      var media = document.querySelector('audio,video');
      isPaused = media ? !!media.paused : true;
    }

    var close = r ? findButton(CLOSE_LABEL, r) : null;
    var hasTrackArt = !!image || !!titleEl;
    var isAd = !!r && (ctaPresent || (!hasTrackArt && !!close) || (!hasTrackArt && durationMs === 0 && positionMs > 0));
    if (!isAd) adMitigated = false;

    var next = findButton(NEXT_LABEL);
    var prev = findButton(PREV_LABEL);
    return {
      version: 1,
      isAd: isAd,
      adReason: isAd ? (ctaPresent ? 'premium-cta' : 'no-track-ad-player') : null,
      state: {
        track: {
          kind: isAd ? 'ad' : 'track',
          name: isAd ? 'Iklan' : title,
          artists: [{ name: artist || '' }],
          image: image,
          durationMs: durationMs
        },
        isPaused: isPaused,
        positionMs: positionMs,
        durationMs: durationMs,
        speed: 1
      },
      controls: {
        play: !!pb,
        pause: !!pb,
        next: !!next,
        previous: !!prev,
        seek: !!bar
      }
    };
  }

  var skipPending = null;
  var skipTimer = null;

  // When minimized to the floating bar, Spotify removes the skip buttons from
  // the DOM. Expand the NPV via the cover, then click the button once rendered.
  function skip(re) {
    var btn = findButton(re);
    if (btn) { btn.click(); return true; }
    var bar = document.querySelector('[data-testid="floating-now-playing-bar"]');
    var cover = bar && bar.querySelector('img');
    if (!cover) return false;
    skipPending = re;
    if (!skipTimer) {
      cover.click();
      var tries = 0;
      skipTimer = setInterval(function () {
        var b = skipPending && findButton(skipPending);
        if (b) { b.click(); skipPending = null; clearInterval(skipTimer); skipTimer = null; }
        else if (++tries >= 20) { skipPending = null; clearInterval(skipTimer); skipTimer = null; }
      }, 100);
    }
    return true;
  }

  function control(action, args) {
    var target = null;
    if (action === 'play' || action === 'pause') {
      target = playButton();
      if (target) {
        var label = target.getAttribute('aria-label') || '';
        var wantsPause = action === 'pause';
        var currentlyPaused = PLAY_LABEL.test(label) && !PAUSE_LABEL.test(label);
        if (wantsPause === currentlyPaused) return false;
      }
    } else if (action === 'next') {
      return skip(NEXT_LABEL);
    } else if (action === 'previous' || action === 'prev') {
      return skip(PREV_LABEL);
    } else if (action === 'seek') {
      var bar = progressBar();
      if (!bar || !args || !Number.isFinite(args.positionMs)) return false;
      var rect = bar.getBoundingClientRect();
      var snap = snapshot();
      if (!rect.width || !snap.state.durationMs) return false;
      var ratio = Math.min(1, Math.max(0, args.positionMs / snap.state.durationMs));
      bar.dispatchEvent(new MouseEvent('click', {
        bubbles: true,
        clientX: rect.left + rect.width * ratio,
        clientY: rect.top + rect.height / 2
      }));
      return true;
    }
    if (!target) return false;
    target.click();
    return true;
  }

  function mitigateAd(snap) {
    if (window.__mspKeepAds || adMitigated) return;
    adMitigated = true;
    try {
      document.querySelectorAll('audio,video').forEach(function (el) { el.muted = true; });
      var r = root();
      var close = r ? findButton(CLOSE_LABEL, r) : null;
      if (close && close.offsetParent !== null) close.click();
      var next = findButton(NEXT_LABEL);
      if (next) next.click();
    } catch (e) {}
  }

  var lastSignature = null;

  function tick() {
    var snap = snapshot();
    if (!snap.isAd && !snap.state.track.name) return;
    var track = snap.state.track;
    var signature = [
      track.name,
      track.artists[0] && track.artists[0].name,
      track.image,
      snap.state.isPaused,
      snap.isAd,
      Math.floor(snap.state.positionMs / 1000)
    ].join('|');
    if (signature === lastSignature) return;
    lastSignature = signature;
    post({ type: 'state', state: snap.state });
    if (snap.isAd) mitigateAd(snap);
  }

  window.__mspSurface = { version: 1, snapshot: snapshot, control: control, tick: tick };
  window.MyspotiBridge = {
    play: function () { return control('play'); },
    pause: function () { return control('pause'); },
    next: function () { return control('next'); },
    previous: function () { return control('previous'); },
    seek: function (args) { return control('seek', args || {}); }
  };

  setTimeout(function () {
    post({ type: 'ready', version: 1 });
    tick();
    setInterval(tick, 1000);
    try {
      var observer = new MutationObserver(function () { tick(); });
      observer.observe(document.body, { childList: true, subtree: true, characterData: true });
    } catch (e) {}
  }, 1200);
})();