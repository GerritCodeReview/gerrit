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
  interface Window {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    marked: any;
  }

  interface HTMLElementTagNameMap {
    'gr-marked-element': GrMarkedElement;
  }
}

// Perform a check to see if marked is loaded on the window, if not, load it.
// This is to support existing usages that might rely on window.marked,
// although we prefer local usage.
if (!window.marked) {
  // We expose the Marked class and Renderer constructor for compatibility
  // with consumers that might look for them, though v17 API is different.
  window.marked = Marked;
  // @ts-ignore
  window.marked.Renderer = Renderer;
  // @ts-ignore
  window.marked.parse = (text, options) => new Marked().parse(text, options);
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

    const renderer = new Renderer();
    // Consumers like gr-formatted-text expect to manipulate the renderer
    // directly before it is used.
    // In marked v17, we can pass this renderer to markedInstance.use({ renderer }).
    if (this.renderer) {
      // Logic for old consumers: they expected a function that takes a renderer.
      // We need to check if this.renderer is a function or an object/callback.
      // The original type was Function | null.
      // gr-formatted-text passes a function: (renderer) => { renderer.link = ... }
      if (typeof this.renderer === 'function') {
        (this.renderer as Function)(renderer);
      }
    }

    markedInstance.use({renderer});

    const options = {
      breaks: this.breaks,
    };

    try {
      // marked.parse can be string | Promise<string>
      const output = markedInstance.parse(this.markdown, options);

      if (output instanceof Promise) {
        output.then(out => {
          this.outputElement[0].innerHTML = out;
          this.dispatchEvent(
            new CustomEvent('marked-render-complete', {
              bubbles: true,
              composed: true,
            })
          );
          if (this.callback) this.callback(null, out);
        });
      } else {
        this.outputElement[0].innerHTML = output;
        this.dispatchEvent(
          new CustomEvent('marked-render-complete', {
            bubbles: true,
            composed: true,
          })
        );
        if (this.callback) this.callback(null, output);
      }
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
