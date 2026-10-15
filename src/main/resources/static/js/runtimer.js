/* Shared run-time helper: live "Running… 23s" timers and "Done in 1m 42s" results. */
(function (global) {
  'use strict';

  /** 23s under a minute, 1m 42s afterwards. */
  function format(ms) {
    var s = Math.max(0, Math.round(ms / 1000));
    if (s < 60) return s + 's';
    var m = Math.floor(s / 60);
    var r = s % 60;
    return m + 'm ' + (r < 10 ? '0' : '') + r + 's';
  }

  /** Live clock: "23s" until 60s, then "01:05". */
  function clock(ms) {
    var s = Math.max(0, Math.floor(ms / 1000));
    if (s < 60) return s + 's';
    var m = Math.floor(s / 60);
    var r = s % 60;
    return (m < 10 ? '0' : '') + m + ':' + (r < 10 ? '0' : '') + r;
  }

  function parse(v) {
    if (v == null || v === '') return NaN;
    if (typeof v === 'number') return v;
    return new Date(v).getTime();
  }

  /** Duration in ms between two ISO/epoch values (end defaults to now). */
  function between(start, end) {
    var a = parse(start);
    var b = end == null || end === '' ? Date.now() : parse(end);
    return isNaN(a) || isNaN(b) ? NaN : Math.max(0, b - a);
  }

  /**
   * Start a live timer inside element `el` (an element or id).
   * Returns { stop(label), elapsed(), cancel() }.
   */
  function start(el, opts) {
    opts = opts || {};
    var node = typeof el === 'string' ? document.getElementById(el) : el;
    var t0 = opts.startedAt != null ? parse(opts.startedAt) : Date.now();
    var running = opts.running || 'Running…';
    var timer = null;
    if (node) node.classList.remove('done');
    if (node) node.classList.add('run-timer', 'running');

    function paint() {
      if (node) node.textContent = running + ' ' + clock(Date.now() - t0);
    }
    paint();
    timer = setInterval(paint, 1000);

    function halt() {
      if (timer) { clearInterval(timer); timer = null; }
    }
    return {
      elapsed: function () { return Date.now() - t0; },
      stop: function (label, failed) {
        halt();
        var ms = Date.now() - t0;
        if (node) {
          node.classList.remove('running');
          node.classList.add('done');
          node.classList.toggle('failed', !!failed);
          node.textContent = (label || 'Done in') + ' ' + format(ms);
        }
        return ms;
      },
      cancel: function () {
        halt();
        if (node) node.textContent = '';
      }
    };
  }

  /** HTML snippet for a static badge, e.g. badge(102000) -> "Done in 1m 42s". */
  function badge(ms, label) {
    if (ms == null || isNaN(ms)) return '';
    return '<span class="run-timer done">' + (label || 'Done in') + ' ' + format(ms) + '</span>';
  }

  /** Make every timer in an element tree tick: <span data-rt-start="ISO"> updates live. */
  var tickers = null;
  function tickAll() {
    document.querySelectorAll('[data-rt-start]').forEach(function (n) {
      var end = n.getAttribute('data-rt-end');
      var ms = between(n.getAttribute('data-rt-start'), end);
      if (isNaN(ms)) { n.textContent = ''; return; }
      n.classList.add('run-timer');
      if (end) {
        n.classList.remove('running'); n.classList.add('done');
        n.textContent = 'Done in ' + format(ms);
      } else {
        n.classList.add('running'); n.classList.remove('done');
        n.textContent = 'Running… ' + clock(ms);
      }
    });
  }
  function autoTick() {
    if (tickers) return;
    tickers = setInterval(tickAll, 1000);
    tickAll();
  }

  global.RunTimer = {
    format: format, clock: clock, between: between,
    start: start, badge: badge, tickAll: tickAll, autoTick: autoTick
  };
})(window);
