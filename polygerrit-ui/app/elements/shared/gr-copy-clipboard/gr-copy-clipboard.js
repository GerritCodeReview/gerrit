/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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

import '@polymer/iron-input/iron-input.js';
import '../../../styles/shared-styles.js';
import '../gr-button/gr-button.js';
import '../gr-icons/gr-icons.js';
import {dom} from '@polymer/polymer/lib/legacy/polymer.dom.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-copy-clipboard_html.js';

const COPY_TIMEOUT_MS = 1000;

/** @extends Polymer.Element */
class GrCopyClipboard extends GestureEventListeners(
    LegacyElementMixin(
        PolymerElement)) {
  static get template() { return htmlTemplate; }

  static get is() { return 'gr-copy-clipboard'; }

  static get properties() {
    return {
      text: String,
      buttonTitle: String,
      hasTooltip: {
        type: Boolean,
        value: false,
      },
      hideInput: {
        type: Boolean,
        value: false,
      },
    };
  }

  focusOnCopy() {
    this.$.button.focus();
  }

  _computeInputClass(hideInput) {
    return hideInput ? 'hideInput' : '';
  }

  _handleInputClick(e) {
    e.preventDefault();
    dom(e).rootTarget.select();
  }

  _copyToClipboard(e) {
    e.preventDefault();
    e.stopPropagation();

    if (this.hideInput) {
      this.$.input.style.display = 'block';
    }
    this.$.input.focus();
    this.$.input.select();
    document.execCommand('copy');
    if (this.hideInput) {
      this.$.input.style.display = 'none';
    }
    this.$.icon.icon = 'gr-icons:check';
    this.async(
        () => this.$.icon.icon = 'gr-icons:content-copy',
        COPY_TIMEOUT_MS);
  }
}

customElements.define(GrCopyClipboard.is, GrCopyClipboard);
