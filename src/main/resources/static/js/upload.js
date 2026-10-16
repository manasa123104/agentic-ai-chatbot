/*
 * BotUpload - shared file / folder pickers and the hand-off of attached files between BOT pages.
 *
 *  - mount(): shows "3 files" / "my-app (42 files)" next to every .upload-pick
 *  - collect(): turns <input type=file> lists into [{ path, file }] (folder paths kept), minus build folders
 *  - stash() / take(): carry the files from the home page to /convert or /stages through IndexedDB
 *  - readText(): reads small text / code files so the home page can add them to the prompt
 * Browser uploads work everywhere, including public mode: nothing here touches a folder on the server.
 */
(function (global) {
  'use strict';

  var DB = 'bot-handoff';
  var STORE = 'pending';
  var MAX_AGE_MS = 30 * 60 * 1000;
  var IGNORED = /(^|\/)(node_modules|\.git|target|build|dist|out|__pycache__|\.idea|\.vscode|\.gradle|venv|\.venv)\//;
  var TEXT_EXT = new Set(('txt md markdown rst csv tsv json yaml yml xml toml ini cfg conf properties env gradle ' +
    'java kt kts scala groovy py rb php js jsx ts tsx mjs cjs go rs c h cpp cc cxx hpp cs swift m dart lua r pl sh bash ps1 bat ' +
    'sql pls plsql cbl cob cpy html htm css scss less vue svelte feature story http dockerfile makefile').split(' '));

  function ext(name) {
    var base = String(name || '').split('/').pop();
    if (/^(dockerfile|makefile)$/i.test(base)) return base.toLowerCase();
    var i = base.lastIndexOf('.');
    return i < 0 ? '' : base.slice(i + 1).toLowerCase();
  }

  function isText(name) {
    return TEXT_EXT.has(ext(name));
  }

  function isIgnored(path) {
    return IGNORED.test(String(path || ''));
  }

  function pathOf(file) {
    return file.webkitRelativePath || file.name;
  }

  /** [{ path, file }] from one or more <input type=file>, FileLists or arrays; skips node_modules, .git, target ... */
  function collect() {
    var out = [];
    for (var i = 0; i < arguments.length; i++) {
      var src = arguments[i];
      var list = src && src.files ? src.files : src;
      if (!list) continue;
      Array.prototype.forEach.call(list, function (f) {
        var p = pathOf(f);
        if (!isIgnored(p)) out.push({ path: p, file: f });
      });
    }
    return out;
  }

  function size(bytes) {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return Math.round(bytes / 1024) + ' KB';
    return (bytes / 1024 / 1024).toFixed(1) + ' MB';
  }

  function describe(entries) {
    if (!entries.length) return 'Nothing chosen yet';
    var first = entries[0].path;
    var folder = first.indexOf('/') > 0 ? first.split('/')[0] : null;
    var total = entries.reduce(function (n, e) { return n + e.file.size; }, 0);
    if (folder) return folder + '/ \u00b7 ' + entries.length + ' file' + (entries.length === 1 ? '' : 's') + ' \u00b7 ' + size(total);
    if (entries.length === 1) return entries[0].path + ' \u00b7 ' + size(total);
    return entries.length + ' files \u00b7 ' + size(total);
  }

  /** Wire every .upload-pick under root: the file name / count is shown next to the button. */
  function mount(root) {
    (root || document).querySelectorAll('.upload-pick').forEach(function (pick) {
      var input = pick.querySelector('input[type=file]');
      var count = pick.querySelector('.u-count');
      if (!input || !count || pick.dataset.mounted) return;
      pick.dataset.mounted = '1';
      var idle = count.textContent;
      input.addEventListener('change', function () {
        var entries = collect(input);
        count.textContent = entries.length ? describe(entries) : idle;
        pick.classList.toggle('has-files', entries.length > 0);
      });
    });
  }

  // ---------------------------------------------------------------- hand-off

  function openDb() {
    return new Promise(function (resolve, reject) {
      if (!global.indexedDB) { reject(new Error('IndexedDB unavailable')); return; }
      var req = global.indexedDB.open(DB, 1);
      req.onupgradeneeded = function () { req.result.createObjectStore(STORE); };
      req.onsuccess = function () { resolve(req.result); };
      req.onerror = function () { reject(req.error); };
    });
  }

  /** Keep the files for the next page: target is 'convert' or 'stages'. */
  function stash(target, entries) {
    return openDb().then(function (db) {
      return new Promise(function (resolve, reject) {
        var tx = db.transaction(STORE, 'readwrite');
        tx.objectStore(STORE).put({
          target: target,
          at: Date.now(),
          entries: entries.map(function (e) { return { path: e.path, file: e.file }; })
        }, 'job');
        tx.oncomplete = function () { db.close(); resolve(); };
        tx.onerror = function () { db.close(); reject(tx.error); };
      });
    });
  }

  /** The files stashed for this target (and removes them), or null. */
  function take(target) {
    return openDb().then(function (db) {
      return new Promise(function (resolve) {
        var tx = db.transaction(STORE, 'readwrite');
        var store = tx.objectStore(STORE);
        var get = store.get('job');
        get.onsuccess = function () {
          var job = get.result;
          if (job && job.target === target) {
            store.delete('job');
            resolve(Date.now() - job.at < MAX_AGE_MS ? job.entries : null);
          } else {
            resolve(null);
          }
        };
        get.onerror = function () { resolve(null); };
        tx.oncomplete = function () { db.close(); };
      });
    }).catch(function () { return null; });
  }

  /** Reads text / code files for the prompt. Returns { blocks: [{path, text, cut}], skipped: [names] }. */
  function readText(entries, limits) {
    var lim = limits || {};
    var maxFiles = lim.maxFiles || 20;
    var perFile = lim.perFile || 12000;
    var total = lim.total || 40000;
    var used = 0;
    var skipped = [];
    var wanted = [];
    entries.forEach(function (e) {
      if (!isText(e.path)) { skipped.push(e.path); return; }
      if (wanted.length >= maxFiles) { skipped.push(e.path); return; }
      wanted.push(e);
    });
    return Promise.all(wanted.map(function (e) {
      return e.file.text().then(function (t) { return { path: e.path, text: t }; }, function () { return null; });
    })).then(function (all) {
      var blocks = [];
      all.forEach(function (b) {
        if (!b) return;
        if (used >= total) { skipped.push(b.path); return; }
        var room = Math.min(perFile, total - used);
        var cut = b.text.length > room;
        var text = cut ? b.text.slice(0, room) : b.text;
        used += text.length;
        blocks.push({ path: b.path, text: text, cut: cut });
      });
      return { blocks: blocks, skipped: skipped };
    });
  }

  global.BotUpload = {
    mount: mount,
    collect: collect,
    describe: describe,
    isText: isText,
    stash: stash,
    take: take,
    readText: readText,
    size: size
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', function () { mount(); });
  } else {
    mount();
  }
})(window);
