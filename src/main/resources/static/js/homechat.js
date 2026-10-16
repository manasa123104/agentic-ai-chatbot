/*
 * HomeChat - the ChatGPT-style plumbing of the home chat (/):
 *   HomeChat.stream(url, payload, { signal, onEvent })  reads newline-delimited JSON events as they arrive
 *   HomeChat.markdown(text)                              turns an answer into safe HTML (bold, lists, links, code)
 * The page (index.html) decides what to draw for each event.
 */
(function (global) {
  'use strict';

  function esc(s) {
    return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  }

  /** Inline markdown on text that is already escaped. Only relative and http(s) links are turned into anchors. */
  function inline(s) {
    var codes = [];
    s = s.replace(/`([^`]+)`/g, function (m, c) {
      codes.push('<code>' + c + '</code>');
      return '\u0000' + (codes.length - 1) + '\u0000';
    });
    s = s.replace(/\[([^\]]+)\]\(((?:https?:\/\/|\/)[^\s)]*)\)/g, function (m, t, u) {
      return '<a href="' + u + '">' + t + '</a>';
    });
    s = s.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
    s = s.replace(/(^|[^*\w])\*([^*\s][^*]*?)\*(?![*\w])/g, '$1<em>$2</em>');
    return s.replace(/\u0000(\d+)\u0000/g, function (m, i) { return codes[+i]; });
  }

  /** A small, safe markdown subset: paragraphs, - / 1. lists, ``` code blocks, **bold**, *italic*, `code`, [links](/x). */
  function markdown(text) {
    var lines = String(text || '').replace(/\r\n?/g, '\n').split('\n');
    var out = [];
    var para = [];
    var list = null; // { tag, items }
    var code = null; // lines of an open fence

    function flushPara() {
      if (para.length) { out.push('<p>' + inline(esc(para.join('\n'))).replace(/\n/g, '<br>') + '</p>'); para = []; }
    }
    function flushList() {
      if (list) {
        out.push('<' + list.tag + '>' + list.items.map(function (i) { return '<li>' + inline(esc(i)) + '</li>'; }).join('') + '</' + list.tag + '>');
        list = null;
      }
    }

    lines.forEach(function (line) {
      if (code) {
        if (/^\s*```/.test(line)) { out.push('<pre><code>' + esc(code.join('\n')) + '</code></pre>'); code = null; }
        else code.push(line);
        return;
      }
      if (/^\s*```/.test(line)) { flushPara(); flushList(); code = []; return; }
      var bullet = /^\s*[-*\u2022]\s+(.*)$/.exec(line);
      var numbered = /^\s*\d+[.)]\s+(.*)$/.exec(line);
      if (bullet || numbered) {
        flushPara();
        var tag = bullet ? 'ul' : 'ol';
        if (list && list.tag !== tag) flushList();
        if (!list) list = { tag: tag, items: [] };
        list.items.push((bullet || numbered)[1]);
        return;
      }
      if (!line.trim()) { flushPara(); flushList(); return; }
      flushList();
      para.push(line);
    });
    if (code) out.push('<pre><code>' + esc(code.join('\n')) + '</code></pre>'); // answer still streaming
    flushPara();
    flushList();
    return out.join('');
  }

  /**
   * POSTs the payload and calls opts.onEvent(event) for each JSON line as soon as it arrives.
   * Rejects with an error whose .unsupported is true when streaming is not available at all (old browser or server),
   * so the caller can fall back to a plain request.
   */
  async function stream(url, payload, opts) {
    opts = opts || {};
    var res = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
      signal: opts.signal
    });
    if (!res.ok || !res.body || !res.body.getReader || typeof TextDecoder === 'undefined') {
      var err = new Error('HTTP ' + res.status);
      err.unsupported = true;
      err.status = res.status;
      throw err;
    }
    var reader = res.body.getReader();
    var decoder = new TextDecoder('utf-8');
    var buffer = '';

    function dispatch(line) {
      line = line.trim();
      if (!line) return;
      var event;
      try { event = JSON.parse(line); } catch (e) { return; } // a damaged line is skipped, never fatal
      opts.onEvent(event);
    }

    for (;;) {
      var chunk = await reader.read();
      if (chunk.done) break;
      buffer += decoder.decode(chunk.value, { stream: true });
      var nl;
      while ((nl = buffer.indexOf('\n')) >= 0) {
        var line = buffer.slice(0, nl);
        buffer = buffer.slice(nl + 1);
        dispatch(line);
      }
    }
    buffer += decoder.decode();
    dispatch(buffer);
  }

  global.HomeChat = { stream: stream, markdown: markdown, escapeHtml: esc };
})(window);
