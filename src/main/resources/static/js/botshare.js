/* Shared download helpers: a code download is always accompanied by its business-logic file. */
(function (global) {
  'use strict';

  var NOT_REQUESTED = 'A description was not requested for this conversion. Tick "Describe what the code does in everyday language" ' +
    'before converting to get a description of the code.';

  /** Saves text as a file in the browser (no server round trip). */
  function saveText(name, text, type) {
    var link = document.createElement('a');
    link.href = URL.createObjectURL(new Blob([text], { type: type || 'text/plain;charset=utf-8' }));
    link.download = name;
    document.body.appendChild(link);
    link.click();
    link.remove();
    setTimeout(function () { URL.revokeObjectURL(link.href); }, 1500);
  }

  /** Starts a download of a server URL (Content-Disposition names the file) after an optional delay in ms. */
  function saveUrl(url, delay) {
    setTimeout(function () {
      var link = document.createElement('a');
      link.href = url;
      link.download = '';
      document.body.appendChild(link);
      link.click();
      link.remove();
    }, delay || 0);
  }

  /** Name of the business-logic companion of a code file name: Foo.py becomes Foo.business-logic.md. */
  function companionName(fileName) {
    var dot = fileName.lastIndexOf('.');
    return (dot > 0 ? fileName.substring(0, dot) : fileName) + '.business-logic.md';
  }

  /** Markdown for pasted/chat conversions; a short note when the logic was not generated. */
  function logicMarkdown(fileName, logic) {
    var body = logic && String(logic).trim() ? String(logic).trim() : NOT_REQUESTED;
    return '# Business logic: ' + fileName + '\n\n' + body + '\n';
  }

  /** Saves a code file and, right after it, its business-logic companion (the logic is skipped when not generated). */
  function saveCodeWithLogic(fileName, code, logic) {
    saveText(fileName, code);
    if (logic && String(logic).trim()) {
      setTimeout(function () { saveText(companionName(fileName), logicMarkdown(fileName, logic), 'text/markdown;charset=utf-8'); }, 350);
    }
  }

  global.BotShare = {
    saveText: saveText,
    saveUrl: saveUrl,
    companionName: companionName,
    logicMarkdown: logicMarkdown,
    saveCodeWithLogic: saveCodeWithLogic
  };
})(window);
