/**
 * @license
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import {GrLinkTextParser, LinkTextParserConfig} from './link-text-parser';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {GrLitElement} from '../../lit/gr-lit-element';
import {css, customElement, html, property, query} from 'lit-element';

declare global {
  interface HTMLElementTagNameMap {
    'gr-linked-text': GrLinkedText;
  }
}

@customElement('gr-linked-text')
export class GrLinkedText extends GrLitElement {
  @query('#output')
  outputElement?: HTMLSpanElement;

  @property({type: Boolean})
  removeZeroWidthSpace?: boolean;

  // content default is null, because this.$.output.textContent is string|null
  @property({type: String})
  content: string | null = null;

  @property({type: Boolean, reflect: true})
  pre = false;

  @property({type: Boolean, reflect: true})
  disabled = false;

  @property({type: Object})
  config?: LinkTextParserConfig;

  static override get styles() {
    return [
      css`
        :host {
          display: block;
        }
        :host([pre]) span {
          white-space: var(--linked-text-white-space, pre-wrap);
          word-wrap: var(--linked-text-word-wrap, break-word);
        }
        :host([disabled]) a {
          color: inherit;
          text-decoration: none;
          pointer-events: none;
        }
        a {
          color: var(--link-color);
        }
      `,
    ];
  }

  override render() {
    if (!this.config) {
      return;
    }
    return html`<span id="output">${this.content}</span>`;
  }

  override updated() {
    if (!this.outputElement || !this.config) return;
    this.outputElement.textContent = '';
    // TODO(TS): mapCommentlinks always has value, remove
    if (!GerritNav.mapCommentlinks) return;
    const config = GerritNav.mapCommentlinks(this.config);
    const parser = new GrLinkTextParser(
      config,
      (text: string | null, href: string | null, fragment?: DocumentFragment) =>
        this._handleParseResult(text, href, fragment),
      this.removeZeroWidthSpace
    );
    parser.parse(this.content);
    // Ensure that external links originating from HTML commentlink configs
    // open in a new tab. @see Issue 5567
    // Ensure links to the same host originating from commentlink configs
    // open in the same tab. When target is not set - default is _self
    // @see Issue 4616
    this.outputElement.querySelectorAll('a').forEach(anchor => {
      if (anchor.hostname === window.location.hostname) {
        anchor.removeAttribute('target');
      } else {
        anchor.setAttribute('target', '_blank');
      }
      anchor.setAttribute('rel', 'noopener');
    });
  }

  /**
   * This method is called when the GrLikTextParser emits a partial result
   * (used as the "callback" parameter). It will be called in either of two
   * ways:
   * - To create a link: when called with `text` and `href` arguments, a link
   *   element should be created and attached to the resulting DOM.
   * - To attach an arbitrary fragment: when called with only the `fragment`
   *   argument, the fragment should be attached to the resulting DOM as is.
   */
  private _handleParseResult(
    text: string | null,
    href: string | null,
    fragment?: DocumentFragment
  ) {
    const output = this.outputElement;
    if (!output) return;
    if (href) {
      const a = document.createElement('a');
      a.setAttribute('href', href);
      // GrLinkTextParser either pass text and href together or
      // only DocumentFragment - see LinkTextParserCallback
      a.textContent = text!;
      a.target = '_blank';
      a.setAttribute('rel', 'noopener');
      output.appendChild(a);
    } else if (fragment) {
      output.appendChild(fragment);
    }
  }
}
