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
import {gfmHeadingId} from 'marked-gfm-heading-id';
import {mangle} from 'marked-mangle';
import {markedSmartypants} from 'marked-smartypants';

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

  @property({type: Object}) renderer: Renderer | null | undefined;

  @property({type: Boolean}) smartypants = false;

  @property({type: Function}) callback: Function | null = null;

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
    const propsToWatch = [
      'markdown',
      'breaks',
      'renderer',
      'smartypants',
      'callback',
    ];

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
      this.outputElement[0].innerHTML = '';
      return;
    }

    const markedInstance = new Marked(
      markedHighlight({
        highlight: (code, lang) => this.highlight(code, lang),
      }),
      gfmHeadingId(),
      mangle()
    );

    if (this.smartypants) {
      markedInstance.use(markedSmartypants());
    }

    if (this.renderer) {
      markedInstance.use({renderer: this.renderer});
    }

    const options = {
      breaks: this.breaks,
    };

    try {
      const output = markedInstance.parse(this.markdown, options);
      if (typeof output !== 'string') {
        throw new Error(
          'marked.parse returned a Promise, but sync execution was expected.'
        );
      }
      this.outputElement[0].innerHTML = output;
      this.dispatchEvent(
        new CustomEvent('marked-render-complete', {
          bubbles: true,
          composed: true,
        })
      );
      if (this.callback) this.callback(null, output);
    } catch (e) {
      if (this.callback) this.callback(e, null);
      throw e;
    }
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
