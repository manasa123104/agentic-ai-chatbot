/*
 * BotRun - the Stop and Resume buttons, the same on every page (/, /convert, /stages, /code).
 *
 * A page that starts long work registers a handler once:
 *     BotRun.register({ key: 'home', label: 'Chat pipeline', url: '/', onStop: fn, onResume: fn });
 * and reports what it is doing:
 *     BotRun.running('4 of 5 bots'), BotRun.stopped('Stopped after 2 of 5 steps'), BotRun.idle().
 *
 *  - Stop is enabled only while something runs; Resume only while something is stopped and can be resumed.
 *  - The state is kept in sessionStorage, so a refresh or a visit to another page does not lose it. On a page that did
 *    not start the work, Resume opens the page that did (it picks the work up from its saved progress).
 *  - A run that was "running" when the page was closed or refreshed comes back as "stopped" (interrupted): its progress
 *    is saved on the server, so Resume continues from the last finished step.
 */
(function (global) {
  'use strict';

  var KEY = 'bot.run.state';
  var handler = null;
  var state = load();
  var bar;
  var stopBtn;
  var resumeBtn;
  var text;

  function load() {
    try {
      var s = JSON.parse(sessionStorage.getItem(KEY) || 'null');
      if (s && s.state === 'running') {
        s.state = 'stopped'; // the page that was running it is gone
        s.info = 'Interrupted by a page change. Press Resume to continue from the last finished step.';
        s.resumable = true;
      }
      return s && s.state ? s : { state: 'idle' };
    } catch (e) {
      return { state: 'idle' };
    }
  }

  function save() {
    try {
      if (state.state === 'idle') sessionStorage.removeItem(KEY);
      else sessionStorage.setItem(KEY, JSON.stringify(state));
    } catch (e) { /* storage blocked: the buttons still work for this page view */ }
  }

  function build() {
    bar = document.createElement('div');
    bar.className = 'run-bar';
    bar.id = 'runBar';
    bar.setAttribute('role', 'region');
    bar.setAttribute('aria-label', 'Stop and resume');
    bar.innerHTML =
      '<span class="run-bar-text" id="runBarText" aria-live="polite"></span>' +
      '<button type="button" class="rc-btn rc-stop" id="runStop">&#9632; Stop</button>' +
      '<button type="button" class="rc-btn rc-resume" id="runResume">&#9654; Resume</button>';
    document.body.appendChild(bar);
    stopBtn = bar.querySelector('#runStop');
    resumeBtn = bar.querySelector('#runResume');
    text = bar.querySelector('#runBarText');
    stopBtn.addEventListener('click', function () {
      if (state.state !== 'running') return;
      stopBtn.disabled = true;
      text.textContent = 'Stopping after the current step\u2026';
      if (handler && handler.onStop) handler.onStop();
      else postStop();
    });
    resumeBtn.addEventListener('click', function () {
      if (state.state !== 'stopped' || !state.resumable) return;
      if (handler && handler.onResume && handler.key === state.key) {
        handler.onResume(state);
      } else if (state.url) {
        global.location.href = state.url; // that page resumes from its saved progress
      }
    });
    render();
  }

  function postStop() {
    if (!state.runId) return;
    fetch('/api/pipeline/stop', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ runId: state.runId })
    }).catch(function () { /* ignore */ });
  }

  function render() {
    if (!bar) return;
    var s = state.state;
    bar.classList.toggle('is-running', s === 'running');
    bar.classList.toggle('is-stopped', s === 'stopped');
    bar.classList.toggle('is-idle', s === 'idle');
    stopBtn.disabled = s !== 'running';
    resumeBtn.disabled = !(s === 'stopped' && state.resumable);
    stopBtn.title = s === 'running' ? 'Stop after the current step. Progress is kept.' : 'Nothing is running';
    resumeBtn.title = resumeBtn.disabled ? 'Nothing to resume' : 'Continue from the last finished step';
    if (s === 'running') {
      text.textContent = (state.label || 'Working') + ' \u00b7 running' + (state.info ? ' \u00b7 ' + state.info : '');
    } else if (s === 'stopped') {
      text.textContent = 'Stopped \u00b7 ' + (state.label || '') + (state.info ? ' \u00b7 ' + state.info : '');
    } else {
      text.textContent = 'Nothing is running';
    }
  }

  function set(next) {
    state = next;
    save();
    render();
  }

  function base(extra) {
    var s = { state: 'idle', key: handler && handler.key, label: handler && handler.label,
      url: handler && handler.url, runId: state.runId };
    for (var k in extra) s[k] = extra[k];
    return s;
  }

  global.BotRun = {
    /** { key, label, url, onStop, onResume } - the page that does the work. */
    register: function (h) {
      handler = h;
      render();
    },
    running: function (info, runId) {
      set(base({ state: 'running', info: info || '', runId: runId || state.runId, resumable: false }));
    },
    stopped: function (info, runId, resumable) {
      set(base({ state: 'stopped', info: info || '', runId: runId || state.runId, resumable: resumable !== false }));
    },
    idle: function () {
      set({ state: 'idle' });
    },
    /** Same as pressing Resume (for a Resume button inside the page). */
    resumeNow: function () {
      if (resumeBtn && !resumeBtn.disabled) resumeBtn.click();
    },
    /** The saved state, e.g. so a page can resume its own work after a refresh. */
    current: function () {
      return state;
    },
    isStopped: function (key) {
      return state.state === 'stopped' && state.resumable && (!key || state.key === key);
    }
  };

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', build);
  else build();
})(window);
