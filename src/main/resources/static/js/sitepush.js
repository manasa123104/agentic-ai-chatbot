/*
 * BotSitePush - the "Push to GitHub" box for the generated website.
 *
 * Any element with a data-site-push attribute opens it (data-host="true" on the host machine, where the saved Git login
 * may be used; everyone else pastes their own GitHub token). It posts to /api/site/push, which uploads the generated
 * website files to a NEW branch and returns the GitHub link. The token is used for that push only and is never stored.
 */
(function () {
  if (window.BotSitePush) return;

  var CSS = '' +
    '#bsp-back{position:fixed;inset:0;z-index:2147483000;background:rgba(15,23,42,.55);display:none;align-items:center;justify-content:center;padding:16px;font:14px/1.45 system-ui,Segoe UI,Arial,sans-serif}' +
    '#bsp-back.open{display:flex}' +
    '#bsp-box{background:#fff;color:#0f172a;border-radius:14px;max-width:480px;width:100%;max-height:92vh;overflow:auto;padding:18px 20px;box-shadow:0 24px 60px rgba(0,0,0,.35)}' +
    '#bsp-box h2{margin:0 0 4px;font-size:18px}' +
    '#bsp-box p.bsp-sub{margin:0 0 12px;color:#475569}' +
    '#bsp-box label{display:block;margin:10px 0 3px;font-weight:600;font-size:13px}' +
    '#bsp-box input{width:100%;box-sizing:border-box;padding:9px 11px;border:1px solid #cbd5e1;border-radius:8px;font:16px system-ui,Arial,sans-serif}' +
    '#bsp-box .bsp-row{display:flex;gap:8px;justify-content:flex-end;margin-top:14px;flex-wrap:wrap}' +
    '#bsp-box button{border:0;border-radius:8px;padding:9px 14px;font:600 14px system-ui,Arial,sans-serif;cursor:pointer}' +
    '#bsp-go{background:#0f6b4c;color:#fff}#bsp-go:disabled{opacity:.6;cursor:wait}' +
    '#bsp-close{background:#e2e8f0;color:#0f172a}' +
    '#bsp-note{font-size:12px;color:#64748b;margin:10px 0 0}' +
    '#bsp-out{margin-top:12px;font-size:14px;overflow-wrap:anywhere}' +
    '#bsp-out.err{color:#b91c1c}#bsp-out.ok{color:#14532d}' +
    '#bsp-link{display:none;margin-top:10px;padding:10px 12px;border:2px solid #0f6b4c;border-radius:10px;background:#f0fdf4}' +
    '#bsp-link strong{display:block;color:#0f6b4c;margin-bottom:2px}' +
    '#bsp-link a{color:#0f6b4c;font-weight:700;overflow-wrap:anywhere}';

  var back = null;
  var host = false;

  function el(tag, attrs, text) {
    var n = document.createElement(tag);
    Object.keys(attrs || {}).forEach(function (k) { n.setAttribute(k, attrs[k]); });
    if (text) n.textContent = text;
    return n;
  }

  function field(box, id, label, type, placeholder) {
    box.appendChild(el('label', { 'for': id }, label));
    box.appendChild(el('input', { id: id, type: type, placeholder: placeholder, autocomplete: 'off' }));
  }

  function build() {
    var style = el('style');
    style.textContent = CSS;
    document.head.appendChild(style);

    back = el('div', { id: 'bsp-back', role: 'dialog', 'aria-modal': 'true', 'aria-labelledby': 'bsp-title' });
    var box = el('div', { id: 'bsp-box' });
    box.appendChild(el('h2', { id: 'bsp-title' }, 'Push this website to GitHub'));
    box.appendChild(el('p', { 'class': 'bsp-sub' },
      'Uploads the generated website files (pages, styles, images, code) to a new branch of your repository.'));
    field(box, 'bsp-repo', 'Repository URL', 'text', 'https://github.com/owner/repo');
    field(box, 'bsp-branch', 'New branch (blank = bot/site-<name>-<time>)', 'text', 'e.g. bot/my-site');
    field(box, 'bsp-folder', 'Folder in the repo (blank = generated-site)', 'text', 'generated-site');
    box.appendChild(el('label', { 'for': 'bsp-token', id: 'bsp-token-label' }, ''));
    box.appendChild(el('input', { id: 'bsp-token', type: 'password', placeholder: 'ghp_... or github_pat_...', autocomplete: 'off' }));
    var row = el('div', { 'class': 'bsp-row' });
    row.appendChild(el('button', { type: 'button', id: 'bsp-close' }, 'Close'));
    row.appendChild(el('button', { type: 'button', id: 'bsp-go' }, '\u2B06 Push to GitHub'));
    box.appendChild(row);
    box.appendChild(el('p', { id: 'bsp-note' },
      'The token is used for this push only; it is never saved or logged. A new branch is created, so your main branch is not changed.'));
    box.appendChild(el('div', { id: 'bsp-out', role: 'status', 'aria-live': 'polite' }));
    var link = el('div', { id: 'bsp-link' });
    link.appendChild(el('strong', {}, 'Your new GitHub link'));
    link.appendChild(el('a', { id: 'bsp-link-url', target: '_blank', rel: 'noopener' }));
    link.appendChild(el('div', {}));
    box.appendChild(link);
    back.appendChild(box);
    document.body.appendChild(back);

    back.addEventListener('click', function (e) { if (e.target === back) close(); });
    document.getElementById('bsp-close').addEventListener('click', close);
    document.getElementById('bsp-go').addEventListener('click', push);
    document.addEventListener('keydown', function (e) { if (e.key === 'Escape') close(); });
  }

  function close() { if (back) back.classList.remove('open'); }

  function show(text, kind) {
    var out = document.getElementById('bsp-out');
    out.className = kind || '';
    out.textContent = text;
  }

  function open(isHost) {
    if (!back) build();
    host = !!isHost;
    document.getElementById('bsp-token-label').textContent = host
      ? 'GitHub token (optional here: the saved Git login of this computer is used if blank)'
      : 'Your GitHub token (required)';
    back.classList.add('open');
    show('', '');
    document.getElementById('bsp-link').style.display = 'none';
    document.getElementById('bsp-repo').focus();
  }

  function push() {
    var repo = document.getElementById('bsp-repo').value.trim();
    var go = document.getElementById('bsp-go');
    document.getElementById('bsp-link').style.display = 'none';
    if (!repo) { show('Enter the repository URL, e.g. https://github.com/owner/repo.', 'err'); return; }
    var token = document.getElementById('bsp-token').value.trim();
    if (!host && !token) { show('Paste your own GitHub token to push from this site.', 'err'); return; }
    go.disabled = true;
    show('Pushing to GitHub... (cloning, adding the website files, pushing)', '');
    fetch('/api/site/push', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        repoUrl: repo,
        branch: document.getElementById('bsp-branch').value.trim(),
        folder: document.getElementById('bsp-folder').value.trim(),
        token: token,
        pull: true
      })
    }).then(function (res) {
      return res.json().catch(function () { return {}; }).then(function (data) { return { ok: res.ok, status: res.status, data: data }; });
    }).then(function (r) {
      if (!r.ok) {
        show(r.data.error || (r.status === 403 ? 'Paste your own GitHub token to push from this site.' : 'Push failed (HTTP ' + r.status + ').'), 'err');
        return;
      }
      var d = r.data;
      show('Pushed ' + d.files + ' file(s) of ' + d.brand + ' to branch ' + d.branch + ' (commit ' + String(d.commit).slice(0, 7) + ').', 'ok');
      var primary = [d.branchUrl, d.compareUrl].filter(function (u) { return u && u.indexOf('https://github.com/') === 0; })[0];
      if (primary) {
        var a = document.getElementById('bsp-link-url');
        a.href = primary;
        a.textContent = primary;
        var box = document.getElementById('bsp-link');
        var extra = box.lastChild;
        extra.textContent = '';
        if (d.compareUrl && d.compareUrl.indexOf('https://github.com/') === 0 && d.compareUrl !== primary) {
          var pr = el('a', { href: d.compareUrl, target: '_blank', rel: 'noopener' }, 'Compare / open pull request');
          extra.appendChild(pr);
        }
        box.style.display = 'block';
      }
      document.getElementById('bsp-token').value = '';
    }).catch(function (e) {
      show('Could not reach the server: ' + e.message, 'err');
    }).then(function () { go.disabled = false; });
  }

  document.addEventListener('click', function (e) {
    var t = e.target.closest ? e.target.closest('[data-site-push]') : null;
    if (!t) return;
    e.preventDefault();
    open(t.getAttribute('data-host') === 'true');
  });

  window.BotSitePush = { open: open };
})();
