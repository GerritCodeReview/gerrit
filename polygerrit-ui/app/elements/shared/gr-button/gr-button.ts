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
import '@polymer/paper-button/paper-button';
import '../../../styles/shared-styles';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {customElement, property, computed, observe} from '@polymer/decorators';
import {htmlTemplate} from './gr-button_html';
import {TooltipMixin} from '../../../mixins/gr-tooltip-mixin/gr-tooltip-mixin';
import {
  KeyboardShortcutMixin,
  CustomKeyboardEvent,
} from '../../../mixins/keyboard-shortcut-mixin/keyboard-shortcut-mixin';
import {PolymerEvent, getEventPath} from '../../../utils/dom-util';
import {appContext} from '../../../services/app-context';
import {ReportingService} from '../../../services/gr-reporting/gr-reporting';

declare global {
  interface HTMLElementTagNameMap {
    'gr-button': GrButton;
  }
}

@customElement('gr-button')
export class GrButton extends LegacyElementMixin(
  KeyboardShortcutMixin(TooltipMixin(GestureEventListeners(PolymerElement)))
) {
  static get template() {
    return htmlTemplate;
  }

  @property({type: Boolean, reflectToAttribute: true})
  downArrow = false;

  @property({type: Boolean, reflectToAttribute: true})
  link = false;

  @property({type: Boolean})
  noUppercase = false;

  @property({type: Boolean, reflectToAttribute: true})
  loading = false;

  @property({type: Boolean, reflectToAttribute: true})
  disabled: boolean | null = null;

  @property({type: String})
  tooltip = '';

  // Note: don't assign a value to this, since constructor is called
  // after created, the initial value maybe overriden by this
  @property({type: String})
  _initialTabindex?: string;

  @computed('disabled', 'loading')
  get _disabled() {
    return this.disabled || this.loading;
  }

  @property({
    computed: 'computeAriaDisabled(disabled, loading)',
    reflectToAttribute: true,
    type: Boolean,
  })
  ariaDisabled!: boolean;

  computeAriaDisabled() {
    return this._disabled;
  }

  private readonly reporting: ReportingService = appContext.reportingService;

  /** @override */
  created() {
    super.created();
    this._initialTabindex = this.getAttribute('tabindex') || '0';
    // TODO(TS): try avoid using unknown
    this.addEventListener('click', e =>
      this._handleAction((e as unknown) as PolymerEvent)
    );
    this.addEventListener('keydown', e =>
      this._handleKeydown((e as unknown) as CustomKeyboardEvent)
    );
  }

  /** @override */
  ready() {
    super.ready();
    this._ensureAttribute('role', 'button');
    this._ensureAttribute('tabindex', '0');
  }

  _handleAction(e: PolymerEvent) {
    if (this._disabled) {
      e.preventDefault();
      e.stopPropagation();
      e.stopImmediatePropagation();
      return;
    }

    this.reporting.reportInteraction('button-click', {path: getEventPath(e)});
  }

  @observe('disabled')
  _disabledChanged(disabled: boolean) {
    this.setAttribute(
      'tabindex',
      disabled ? '-1' : this._initialTabindex || '0'
    );
    this.updateStyles();
  }

  _handleKeydown(e: CustomKeyboardEvent) {
    if (this.modifierPressed(e)) {
      return;
    }
    e = this.getKeyboardEvent(e);
    // Handle `enter`, `space`.
    if (e.keyCode === 13 || e.keyCode === 32) {
      e.preventDefault();
      e.stopPropagation();
      this.click();
    }
  }
}
