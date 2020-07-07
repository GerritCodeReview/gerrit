/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
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
import '@polymer/paper-button/paper-button.js';
import '../../../styles/shared-styles.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-button_html.js';
import {TooltipMixin} from '../../../mixins/gr-tooltip-mixin/gr-tooltip-mixin.js';
import {KeyboardShortcutMixin} from '../../../mixins/keyboard-shortcut-mixin/keyboard-shortcut-mixin.js';
import {getEventPath} from '../../../utils/dom-util.js';
import {appContext} from '../../../services/app-context.js';

goog.declareModuleId('polygerrit.elements.shared.gr$2dbutton.gr$2dbutton');

/**
 * @extends PolymerElement
 */
class GrButton extends KeyboardShortcutMixin(TooltipMixin(GestureEventListeners(
    LegacyElementMixin(
        PolymerElement)))) {
  static get template() { return htmlTemplate; }

  static get is() { return 'gr-button'; }

  static get properties() {
    return {
      tooltip: String,
      downArrow: {
        type: Boolean,
        reflectToAttribute: true,
      },
      link: {
        type: Boolean,
        value: false,
        reflectToAttribute: true,
      },
      disabled: {
        type: Boolean,
        observer: '_disabledChanged',
        reflectToAttribute: true,
      },
      noUppercase: {
        type: Boolean,
        value: false,
      },
      loading: {
        type: Boolean,
        value: false,
        reflectToAttribute: true,
      },
      ariaDisabled: {
        type: Boolean,
        computed: '_computeDisabled(disabled, loading)',
        reflectToAttribute: true,
      },

      _disabled: {
        type: Boolean,
        computed: '_computeDisabled(disabled, loading)',
      },

      _initialTabindex: {
        type: String,
        value: '0',
      },
    };
  }

  constructor() {
    super();
    this.reporting = appContext.reportingService;
  }

  /** @override */
  created() {
    super.created();
    this._initialTabindex = this.getAttribute('tabindex') || '0';
    this.addEventListener('click', e => this._handleAction(e));
    this.addEventListener('keydown',
        e => this._handleKeydown(e));
  }

  /** @override */
  ready() {
    super.ready();
    this._ensureAttribute('role', 'button');
    this._ensureAttribute('tabindex', '0');
  }

  _handleAction(e) {
    if (this._disabled) {
      e.preventDefault();
      e.stopPropagation();
      e.stopImmediatePropagation();
      return;
    }

    this.reporting.reportInteraction('button-click',
        {path: getEventPath(e)});
  }

  _disabledChanged(disabled) {
    this.setAttribute('tabindex', disabled ? '-1' : this._initialTabindex);
    this.updateStyles();
  }

  _computeDisabled(disabled, loading) {
    return disabled || loading;
  }

  _handleKeydown(e) {
    if (this.modifierPressed(e)) { return; }
    e = this.getKeyboardEvent(e);
    // Handle `enter`, `space`.
    if (e.keyCode === 13 || e.keyCode === 32) {
      e.preventDefault();
      e.stopPropagation();
      this.click();
    }
  }
}

customElements.define(GrButton.is, GrButton);
