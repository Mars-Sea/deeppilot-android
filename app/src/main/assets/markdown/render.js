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
    document.getElementById('content').innerHTML = toHtml(payload.markdown, payload.math);
    reportHeight();
    if (document.fonts && document.fonts.ready) document.fonts.ready.then(reportHeight);
  }

  if (typeof ResizeObserver === 'function') {
    new ResizeObserver(reportHeight).observe(document.getElementById('content'));
  }

  window.DeepPilot = { render: render, toHtml: toHtml };
})();
