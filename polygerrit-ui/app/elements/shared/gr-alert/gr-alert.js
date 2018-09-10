/**
@license
Copyright (C) 2016 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
import '../../../../@polymer/polymer/polymer-legacy.js';

import '../gr-button/gr-button.js';
import '../../../styles/shared-styles.js';
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

(function(window) {
  window.Gerrit = window.Gerrit || {};
  if (window.Gerrit.hasOwnProperty('getRootElement')) { return; }

  window.Gerrit.getRootElement = () => document.body;
})(window);

Polymer({
  _template: Polymer.html`
    <style include="shared-styles">
      /**
       * ALERT: DO NOT ADD TRANSITION PROPERTIES WITHOUT PROPERLY UNDERSTANDING
       * HOW THEY ARE USED IN THE CODE.
       */
      :host([toast]) {
        background-color: var(--tooltip-background-color);
        bottom: 1.25rem;
        border-radius: 3px;
        box-shadow: 0 1px 3px rgba(0, 0, 0, .3);
        color: var(--view-background-color);
        left: 1.25rem;
        padding: 1em 1.5em;
        position: fixed;
        transform: translateY(5rem);
        transition: transform var(--gr-alert-transition-duration, 80ms) ease-out;
        z-index: 1000;
      }
      :host([shown]) {
        transform: translateY(0);
      }
      .text {
        color: var(--tooltip-text-color);
        display: inline-block;
        max-height: 10rem;
        max-width: 80vw;
        vertical-align: bottom;
        word-break: break-all;
      }
      .action {
        color: var(--link-color);
        font-family: var(--font-family-bold);
        margin-left: 1em;
        text-decoration: none;
        --gr-button: {
          padding: 0;
        }
      }
    </style>
    <span class="text">[[text]]</span>
    <gr-button link="" class="action" hidden\$="[[_hideActionButton]]" on-tap="_handleActionTap">[[actionText]]</gr-button>
`,

  is: 'gr-alert',

  /**
   * Fired when the action button is pressed.
   *
   * @event action
   */

  properties: {
    text: String,
    actionText: String,
    shown: {
      type: Boolean,
      value: true,
      readOnly: true,
      reflectToAttribute: true,
    },
    toast: {
      type: Boolean,
      value: true,
      reflectToAttribute: true,
    },

    _hideActionButton: Boolean,
    _boundTransitionEndHandler: {
      type: Function,
      value() { return this._handleTransitionEnd.bind(this); },
    },
    _actionCallback: Function,
  },

  attached() {
    this.addEventListener('transitionend', this._boundTransitionEndHandler);
  },

  detached() {
    this.removeEventListener('transitionend',
        this._boundTransitionEndHandler);
  },

  show(text, opt_actionText, opt_actionCallback) {
    this.text = text;
    this.actionText = opt_actionText;
    this._hideActionButton = !opt_actionText;
    this._actionCallback = opt_actionCallback;
    Gerrit.getRootElement().appendChild(this);
    this._setShown(true);
  },

  hide() {
    this._setShown(false);
    if (this._hasZeroTransitionDuration()) {
      Gerrit.getRootElement().removeChild(this);
    }
  },

  _hasZeroTransitionDuration() {
    const style = window.getComputedStyle(this);
    // transitionDuration is always given in seconds.
    const duration = Math.round(parseFloat(style.transitionDuration) * 100);
    return duration === 0;
  },

  _handleTransitionEnd(e) {
    if (this.shown) { return; }

    Gerrit.getRootElement().removeChild(this);
  },

  _handleActionTap(e) {
    e.preventDefault();
    if (this._actionCallback) { this._actionCallback(); }
  }
});
