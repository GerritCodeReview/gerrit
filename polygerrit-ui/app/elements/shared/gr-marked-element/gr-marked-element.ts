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
// @ts-ignore
import * as marked from 'marked/lib/marked';

if (!window.marked) {
  window.marked = marked;
}

declare global {
  interface Window {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    marked: any;
  }

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

  @property({type: Boolean}) sanitize = false;

  @property({type: Function}) sanitizer:
    | ((html: string) => string)
    | undefined = undefined;

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
        <div id="content"></div>
      </slot>
    `;
  }

  private isAttached = false;

  override connectedCallback() {
    super.connectedCallback();
    this.isAttached = true;
    this.renderMarkdown();
  }

  override disconnectedCallback() {
    super.disconnectedCallback();
    this.isAttached = false;
  }

  protected override updated(changedProps: PropertyValues) {
    const propsToWatch = [
      'markdown',
      'breaks',
      'pedantic',
      'renderer',
      'sanitize',
      'sanitizer',
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
    if (!this.isAttached || !this.outputElement.length) {
      return;
    }

    if (!this.markdown) {
      this.outputElement[0].innerHTML = '';
      return;
    }

    const renderer = new window.marked.Renderer();
    if (this.renderer) this.renderer(renderer);

    const options = {
      renderer,
      highlight: this.highlight.bind(this),
      breaks: this.breaks,
      sanitize: this.sanitize,
      sanitizer: this.sanitizer,
      pedantic: this.pedantic,
      smartypants: this.smartypants,
    };

    let output = window.marked(this.markdown, options, this.callback);

    this.outputElement[0].innerHTML = output;
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
