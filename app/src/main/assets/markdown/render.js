// Renders one assistant message: Markdown by marked, formulas by KaTeX.
// TeX was already swapped for n placeholders on the Kotlin side
// (core MarkdownMath), so Markdown never sees it.
(function () {
  'use strict';

  function escapeHtml(text) {
    return String(text).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }

  function safeUrl(href) {
    return /^(https?:|mailto:)/i.test(href || '') ? href : null;
  }

  marked.use({
    gfm: true,
    renderer: {
      // Raw HTML in a reply is shown as text, never parsed.
      html: function (token) { return escapeHtml(token.text || token.raw || ''); },
      link: function (token) {
        var text = this.parser.parseInline(token.tokens);
        var href = safeUrl(token.href);
        return href ? '<a href="' + escapeHtml(href) + '">' + text + '</a>' : text;
      },
      // Images would need the network, which the page does not have: show a link.
      image: function (token) {
        var href = safeUrl(token.href);
        var label = escapeHtml(token.text || token.href || '');
        return href ? '<a href="' + escapeHtml(href) + '">' + label + '</a>' : label;
      },
    },
  });

  function typeset(html, math) {
    return html.replace(/(\d+)/g, function (match, index) {
      var segment = math[Number(index)];
      if (!segment) return match;
      try {
        return katex.renderToString(segment.tex, {
          displayMode: !!segment.display,
          throwOnError: false,
          strict: 'ignore',
          output: 'html',
        });
      } catch (e) {
        return '<code>' + escapeHtml(segment.tex) + '</code>';
      }
    });
  }

  function toHtml(markdown, math) {
    return typeset(marked.parse(markdown || ''), math || []);
  }

  var COPY_ICON = '<svg viewBox="0 0 16 16"><rect x="5.5" y="5.5" width="8" height="8" rx="1.8"/>' +
    '<path d="M10.5 3.5v-.2A1.8 1.8 0 0 0 8.7 1.5H4.3a1.8 1.8 0 0 0-1.8 1.8v4.4a1.8 1.8 0 0 0 1.8 1.8h.2"/></svg>';
  var CHECK_ICON = '<svg viewBox="0 0 16 16"><path d="M3 8.5l3 3 7-7"/></svg>';

  function copyButton(getText, label, copiedLabel) {
    var button = document.createElement('button');
    button.className = 'block-button';
    function show(copied) {
      button.innerHTML = (copied ? CHECK_ICON : COPY_ICON) +
        (label ? '<span>' + escapeHtml(copied ? copiedLabel : label) + '</span>' : '');
    }
    show(false);
    if (!label) button.setAttribute('aria-label', copiedLabel);
    button.addEventListener('click', function () {
      if (window.DeepPilotHost) window.DeepPilotHost.copy(getText());
      show(true);
      setTimeout(function () { show(false); }, 1400);
    });
    return button;
  }

  function head(left, button) {
    var row = document.createElement('div');
    row.className = 'block-head';
    row.appendChild(left);
    row.appendChild(button);
    return row;
  }

  function tableText(table) {
    return Array.prototype.map.call(table.rows, function (row, index) {
      var cells = Array.prototype.map.call(row.cells, function (cell) {
        return cell.textContent.replace(/\|/g, '\\|');
      });
      var line = '| ' + cells.join(' | ') + ' |';
      if (index === 0) line += '\n| ' + cells.map(function () { return '---'; }).join(' | ') + ' |';
      return line;
    }).join('\n');
  }

  // Wraps code blocks and tables in the iOS card chrome (header with copy).
  function decorate(root, labels) {
    Array.prototype.forEach.call(root.querySelectorAll('pre'), function (pre) {
      var code = pre.querySelector('code');
      var match = code && /language-([\w+#.-]+)/.exec(code.className || '');
      var name = document.createElement('span');
      name.className = 'code-lang';
      name.textContent = match ? match[1].toUpperCase() : (labels.code || 'Code');
      var box = document.createElement('div');
      box.className = 'code-block';
      pre.parentNode.insertBefore(box, pre);
      box.appendChild(head(name, copyButton(function () { return (code || pre).textContent; }, labels.copy || 'Copy', labels.copied || 'Copied')));
      box.appendChild(pre);
    });
    Array.prototype.forEach.call(root.querySelectorAll('table'), function (table) {
      var title = document.createElement('span');
      title.className = 'table-title';
      title.textContent = labels.table || 'Table';
      var box = document.createElement('div');
      box.className = 'table-block';
      var scroll = document.createElement('div');
      scroll.className = 'table-scroll';
      table.parentNode.insertBefore(box, table);
      box.appendChild(head(title, copyButton(function () { return tableText(table); }, null, labels.copyTable || 'Copy table')));
      box.appendChild(scroll);
      scroll.appendChild(table);
    });
  }

  var lastHeight = -1;
  function reportHeight() {
    var height = Math.ceil(document.getElementById('content').getBoundingClientRect().height);
    if (height !== lastHeight && window.DeepPilotHost) {
      lastHeight = height;
      window.DeepPilotHost.onHeight(height);
    }
  }

  function render(payload) {
    var root = document.documentElement.style;
    var theme = payload.theme || {};
    Object.keys(theme).forEach(function (key) { root.setProperty('--' + key, theme[key]); });
    var content = document.getElementById('content');
    content.innerHTML = toHtml(payload.markdown, payload.math);
    decorate(content, payload.labels || {});
    reportHeight();
    if (document.fonts && document.fonts.ready) document.fonts.ready.then(reportHeight);
  }

  if (typeof ResizeObserver === 'function') {
    new ResizeObserver(reportHeight).observe(document.getElementById('content'));
  }

  window.DeepPilot = { render: render, toHtml: toHtml };
})();
