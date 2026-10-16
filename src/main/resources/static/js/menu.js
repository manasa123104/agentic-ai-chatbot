/*
 * BotMenu - the Menu (hamburger) button for narrow windows (900px and below), on every page.
 *  - home (/): the left sidebar (#chatRail) opens as a drawer, toggled by #railToggle
 *  - /convert /stages /code: a Menu button is added to the header and shows the header links (.code-actions)
 * Closes with the button, the dark overlay, the Esc key, the X in the drawer, a tap on a link, or by widening the window.
 * Wider than 900px the button is hidden by CSS and nothing here has any effect.
 */
(function () {
  'use strict';

  var MQ = window.matchMedia('(max-width: 900px)');
  var OPEN_HTML = '&#9776; Menu';
  var CLOSE_HTML = '&#10005; Close';
  var menus = [];

  var overlay = document.createElement('div');
  overlay.className = 'nav-overlay';
  overlay.setAttribute('aria-hidden', 'true');
  document.body.appendChild(overlay);

  function isOpen(m) {
    return document.body.classList.contains(m.cls);
  }

  function setOpen(m, open, restoreFocus) {
    document.body.classList.toggle(m.cls, open);
    m.toggle.setAttribute('aria-expanded', open ? 'true' : 'false');
    m.toggle.innerHTML = open ? CLOSE_HTML : OPEN_HTML;
    if (open && m.focus) {
      var target = m.panel.querySelector(m.focus);
      if (target) { setTimeout(function () { target.focus(); }, 30); }
    } else if (!open && restoreFocus) {
      m.toggle.focus();
    }
  }

  function closeAll(restoreFocus) {
    menus.forEach(function (m) { if (isOpen(m)) setOpen(m, false, restoreFocus); });
  }

  function register(toggle, panel, cls, focusSelector) {
    if (!toggle || !panel) return;
    var m = { toggle: toggle, panel: panel, cls: cls, focus: focusSelector };
    menus.push(m);
    toggle.setAttribute('aria-expanded', 'false');
    toggle.addEventListener('click', function () {
      var open = !isOpen(m);
      closeAll(false);
      setOpen(m, open, !open);
    });
    panel.addEventListener('click', function (e) {
      if (!MQ.matches) return;
      var hit = e.target.closest('a, .new-chat, .history-item, [data-menu-close]');
      if (hit && !e.target.closest('.history-del, .history-edit, .history-clear')) setOpen(m, false, false);
    });
  }

  // home: the sidebar drawer
  register(document.getElementById('railToggle'), document.getElementById('chatRail'), 'rail-open', '.rail-close');

  // other pages: a Menu button in the shared header
  var header = document.querySelector('.code-top');
  var nav = header && header.querySelector('.code-actions');
  if (header && nav) {
    nav.id = nav.id || 'siteNav';
    var button = document.createElement('button');
    button.type = 'button';
    button.className = 'rail-toggle menu-toggle';
    button.id = 'menuToggle';
    button.setAttribute('aria-controls', nav.id);
    button.innerHTML = OPEN_HTML;
    header.insertBefore(button, nav);
    register(button, nav, 'nav-open', 'a');
  }

  overlay.addEventListener('click', function () { closeAll(true); });
  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape') closeAll(true);
  });
  var close = document.getElementById('railClose');
  if (close) close.addEventListener('click', function () { closeAll(true); });

  function onResize() { if (!MQ.matches) closeAll(false); }
  if (MQ.addEventListener) MQ.addEventListener('change', onResize); else MQ.addListener(onResize);
  window.addEventListener('pageshow', function () { closeAll(false); });

  window.BotMenu = { close: function () { closeAll(false); } };
})();
