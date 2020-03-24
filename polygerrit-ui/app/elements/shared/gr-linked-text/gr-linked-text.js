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
import '../../../scripts/bundled-polymer.js';

import '../../../styles/shared-styles.js';
import '../../core/gr-navigation/gr-navigation.js';
import './link-text-parser.js';
import {dom} from '@polymer/polymer/lib/legacy/polymer.dom.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import 'ba-linkify/ba-linkify.js';
import {htmlTemplate} from './gr-linked-text_html.js';

/** @extends Polymer.Element */
class GrLinkedText extends GestureEventListeners(
    LegacyElementMixin(
        PolymerElement)) {
  static get template() { return htmlTemplate; }

  static get is() { return 'gr-linked-text'; }

  static get properties() {
    return {
      removeZeroWidthSpace: Boolean,
      content: {
        type: String,
        observer: '_contentChanged',
      },
      pre: {
        type: Boolean,
        value: false,
        reflectToAttribute: true,
      },
      disabled: {
        type: Boolean,
        value: false,
        reflectToAttribute: true,
      },
      config: Object,
    };
  }

  static get observers() {
    return [
      '_contentOrConfigChanged(content, config)',
    ];
  }

  _contentChanged(content) {
    // In the case where the config may not be set (perhaps due to the
    // request for it still being in flight), set the content anyway to
    // prevent waiting on the config to display the text.
    if (this.config != null) { return; }
    this.$.output.textContent = content;
  }

  /**
   * Because either the source text or the linkification config has changed,
   * the content should be re-parsed.
   *
   * @param {string|null|undefined} content The raw, un-linkified source
   *     string to parse.
   * @param {Object|null|undefined} config The server config specifying
   *     commentLink patterns
   */
  _contentOrConfigChanged(content, config) {
    if (!Gerrit.Nav || !Gerrit.Nav.mapCommentlinks) return;
    config = Gerrit.Nav.mapCommentlinks(config);
    const output = dom(this.$.output);
    output.textContent = '';
    const parser = new GrLinkTextParser(config,
        this._handleParseResult.bind(this), this.removeZeroWidthSpace);
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
   *
   * @param {string|null} text
   * @param {string|null} href
   * @param  {DocumentFragment|undefined} fragment
   */
  _handleParseResult(text, href, fragment) {
    const output = dom(this.$.output);
    if (href) {
      const a = document.createElement('a');
      a.href = href;
      a.textContent = text;
      a.target = '_blank';
      a.rel = 'noopener';
      output.appendChild(a);
    } else if (fragment) {
      output.appendChild(fragment);
    }
  }
}

customElements.define(GrLinkedText.is, GrLinkedText);
