/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: BSD-3-Clause
 */

import {css, html, LitElement, PropertyValues} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {marked, Marked} from 'marked';
import {markedHighlight} from 'marked-highlight';
import {markedSmartypants} from 'marked-smartypants';

declare global {
  interface HTMLElementTagNameMap {
    'gr-marked-element': GrMarkedElement;
  }
}

@customElement('gr-marked-element')
export class GrMarkedElement extends LitElement {
  @property({type: String}) markdown: string | null = null;

  @property({type: Boolean}) breaks = false;

  @property({type: Boolean}) pedantic = false;

  @property({type: Function}) renderer: Function | null = null;

  @property({type: Boolean}) smartypants = false;

  @property({type: Function}) callback: Function | null = null;

  @state() xhr: XMLHttpRequest | null = null;

  static override styles = css`
    :host {
      display: block;
    }
  `;

  override render() {
    return html`
      <slot name="markdown-html">
        <div id="content"></div>
      </slot>
    `;
  }

  private attached = false;

  override connectedCallback() {
    super.connectedCallback();
    this.attached = true;
  }

  override disconnectedCallback() {
    super.disconnectedCallback();
    this.attached = false;
  }

  get outputElement(): HTMLElement | null {
    const slot = this.renderRoot.querySelector<HTMLSlotElement>(
      'slot[name="markdown-html"]'
    );
    const assigned = slot?.assignedElements({flatten: true}) ?? [];
    return (
      (assigned[0] as HTMLElement) || this.renderRoot.querySelector('#content')
    );
  }

  override firstUpdated() {
    if (!this.markdown) {
      const markdownScript = this.querySelector<HTMLScriptElement>(
        '[type="text/markdown"]'
      );
      if (markdownScript) {
        if (markdownScript.src) {
          this.request(markdownScript.src);
          new MutationObserver(mutations => {
            for (const m of mutations) {
              if (m.attributeName === 'src') {
                this.request(markdownScript.src);
              }
            }
          }).observe(markdownScript, {attributes: true});
        }

        const content = markdownScript.textContent?.trim();
        if (content) {
          this.markdown = this.unindent(content);
        }
      }
    }
  }

  protected override updated(changedProps: PropertyValues) {
    const propsToWatch = [
      'markdown',
      'breaks',
      'pedantic',
      'renderer',
      'smartypants',
      'callback',
    ];

    if (propsToWatch.some(prop => changedProps.has(prop))) {
      this.renderMarkdown();
    }
  }

  private renderMarkdown() {
    if (!this.outputElement || !this.attached || !this.markdown) {
      if (this.outputElement) this.outputElement.innerHTML = '';
      return;
    }

    const markedHtml = new Marked(
      markedHighlight({
        highlight: this.highlight.bind(this),
      })
    );

    const renderer = new marked.Renderer();
    if (this.renderer) this.renderer(renderer);

    markedHtml.use({renderer});

    if (this.smartypants) {
      markedHtml.use(markedSmartypants());
    }

    markedHtml.setOptions({
      // renderer,
      breaks: this.breaks,
      pedantic: this.pedantic,
    });

    try {
      const html = markedHtml.parse(this.markdown, {async: false});

      if (this.callback) {
        this.callback(null, html);
      }

      this.outputElement.innerHTML = html;
    } catch (err) {
      console.error('Markdown rendering failed:', err);
      if (this.callback) {
        const html = this.callback(err, '');
        this.outputElement.innerHTML = html;
      }
    }

    this.dispatchEvent(
      new CustomEvent('marked-render-complete', {bubbles: true, composed: true})
    );
  }

  private highlight(code: string, lang: string): string {
    const event = new CustomEvent('syntax-highlight', {
      detail: {code, lang},
      bubbles: true,
      composed: true,
    });
    this.dispatchEvent(event);
    return event.detail.code || code;
  }

  private unindent(text: string): string {
    const lines = text.replace(/\t/g, '  ').split('\n');
    const indent = lines.reduce<number | null>((min, line) => {
      if (/^\s*$/.test(line)) return min;
      const currentIndent = line.match(/^(\s*)/)?.[0].length ?? 0;
      return min === null ? currentIndent : Math.min(min, currentIndent);
    }, null);
    return lines.map(line => line.substring(indent ?? 0)).join('\n');
  }

  private request(url: string) {
    this.xhr = new XMLHttpRequest();
    this.xhr.addEventListener('error', e => this.handleError(e));
    this.xhr.addEventListener('loadend', e => {
      const status = this.xhr?.status ?? 0;
      if (status === 0 || (status >= 200 && status < 300)) {
        this.markdown = this.xhr?.response ?? '';
      } else {
        this.handleError(e);
      }
      this.dispatchEvent(new CustomEvent('marked-loadend', {detail: e}));
    });
    this.xhr.open('GET', url);
    this.xhr.setRequestHeader('Accept', 'text/markdown');
    this.xhr.send();
  }

  private handleError(e: Event) {
    const evt = new CustomEvent('marked-request-error', {
      detail: e,
      cancelable: true,
    });
    this.dispatchEvent(evt);
    if (!evt.defaultPrevented) {
      this.markdown = 'Failed loading markdown source';
    }
  }
}
