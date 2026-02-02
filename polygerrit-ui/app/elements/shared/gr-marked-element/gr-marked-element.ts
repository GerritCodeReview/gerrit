/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {css, html, LitElement, PropertyValues} from 'lit';
import {
  customElement,
  property,
  queryAssignedElements,
} from 'lit/decorators.js';
import {Marked, Renderer} from 'marked';
import {markedHighlight} from 'marked-highlight';

import {
  sanitizeHtml,
  setElementInnerHtml,
} from '../../../utils/inner-html-util';

declare global {
  interface HTMLElementTagNameMap {
    'gr-marked-element': GrMarkedElement;
  }
}

/**
 * This is based on [marked-element](https://github.com/PolymerElements/marked-element) by Polymer
 * but converted to use Lit. It uses the [marked](https://github.com/markedjs/marked) library.
 */
@customElement('gr-marked-element')
export class GrMarkedElement extends LitElement {
  @property({type: String}) markdown: string | null = null;

  @property({type: Boolean}) breaks = false;

  @property({type: Boolean}) pedantic = false;

  @property({type: Function}) renderer: Function | null = null;

  @queryAssignedElements({
    flatten: true,
    slot: 'markdown-html',
  })
  private outputElement!: Array<HTMLElement>;

  static override styles = css`
    :host {
      display: block;
    }
  `;

  override render() {
    return html`
      <slot name="markdown-html">
        <div id="content" slot="markdown-html"></div>
      </slot>
    `;
  }

  override connectedCallback() {
    super.connectedCallback();
    this.renderMarkdown();
  }

  protected override updated(changedProps: PropertyValues) {
    const propsToWatch = ['markdown', 'breaks', 'pedantic', 'renderer'];
    if (propsToWatch.some(prop => changedProps.has(prop))) {
      this.renderMarkdown();
    }
  }

  override firstUpdated() {
    this.renderMarkdown();
  }

  private renderMarkdown() {
    if (!this.isConnected || !this.outputElement.length) {
      return;
    }

    if (!this.markdown) {
      this.outputElement[0].textContent = '';
      return;
    }

    const renderer = new Renderer();
    if (this.renderer) {
      this.renderer(renderer);
    }

    const marked = new Marked().use(
      {tokenizer: {
        // Return undefined to skip the default autolink/url tokenizers.
        url() { return undefined; },
        autolink() { return undefined; },
      }},
      markedHighlight({
        highlight: (code, lang, _) => {
          return this.highlight(code, lang);
        }
      }),
    );

    const unsafeHtml = marked.parse(this.markdown, {
      async: false,
      breaks: this.breaks,
      pedantic: this.pedantic,
      renderer,
    }) || '';
    const safeHtml = sanitizeHtml(unsafeHtml);

    setElementInnerHtml(this.outputElement[0], safeHtml);
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
}
