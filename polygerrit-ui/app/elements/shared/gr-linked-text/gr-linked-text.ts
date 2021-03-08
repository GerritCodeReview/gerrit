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
import '../../../styles/shared-styles';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-linked-text_html';
import {GrLinkTextParser, LinkTextParserConfig} from './link-text-parser';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {customElement, property, observe} from '@polymer/decorators';

declare global {
  interface HTMLElementTagNameMap {
    'gr-linked-text': GrLinkedText;
  }
}

export interface GrLinkedText {
  $: {
    output: HTMLSpanElement;
  };
}

@customElement('gr-linked-text')
export class GrLinkedText extends LegacyElementMixin(PolymerElement) {
  static get template() {
    return htmlTemplate;
  }

  @property({type: Boolean})
  removeZeroWidthSpace?: boolean;

  // content default is null, because this.$.output.textContent is string|null
  @property({type: String})
  content: string | null = null;

  @property({type: Boolean, reflectToAttribute: true})
  pre = false;

  @property({type: Boolean, reflectToAttribute: true})
  disabled = false;

  @property({type: Object})
  config?: LinkTextParserConfig;

  @observe('content')
  _contentChanged(content: string | null) {
    // In the case where the config may not be set (perhaps due to the
    // request for it still being in flight), set the content anyway to
    // prevent waiting on the config to display the text.
    if (!this.config) {
      return;
    }
    this.$.output.textContent = content;
  }

  /**
   * Because either the source text or the linkification config has changed,
   * the content should be re-parsed.
   *
   * @param content The raw, un-linkified source string to parse.
   * @param config The server config specifying commentLink patterns
   */
  @observe('content', 'config')
  _contentOrConfigChanged(
    content: string | null,
    config?: LinkTextParserConfig
  ) {
    if (!config) {
      return;
    }

    // TODO(TS): mapCommentlinks always has value, remove
    if (!GerritNav.mapCommentlinks) return;
    config = GerritNav.mapCommentlinks(config);
    const output = this.$.output;
    output.textContent = '';
    const parser = new GrLinkTextParser(
      config,
      (text: string | null, href: string | null, fragment?: DocumentFragment) =>
        this._handleParseResult(text, href, fragment),
      this.removeZeroWidthSpace
    );
    parser.parse(content);

    // Ensure that external links originating from HTML commentlink configs
    // open in a new tab. @see Issue 5567
    // Ensure links to the same host originating from commentlink configs
    // open in the same tab. When target is not set - default is _self
    // @see Issue 4616
    output.querySelectorAll('a').forEach(anchor => {
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
    const output = this.$.output;
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
