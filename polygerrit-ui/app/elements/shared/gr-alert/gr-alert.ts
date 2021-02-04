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
import '../gr-button/gr-button';
import '../../../styles/shared-styles';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-alert_html';
import {getRootElement} from '../../../scripts/rootElement';
import {customElement, property} from '@polymer/decorators';
import {ErrorType} from '../../../types/types';

declare global {
  interface HTMLElementTagNameMap {
    'gr-alert': GrAlert;
  }
}

@customElement('gr-alert')
export class GrAlert extends GestureEventListeners(
  LegacyElementMixin(PolymerElement)
) {
  static get template() {
    return htmlTemplate;
  }

  /**
   * Fired when the action button is pressed.
   *
   * @event action
   */

  @property({type: String})
  text?: string;

  @property({type: String})
  actionText?: string;

  @property({type: String})
  type?: ErrorType;

  @property({type: Boolean, reflectToAttribute: true})
  shown = true;

  @property({type: Boolean, reflectToAttribute: true})
  toast = true;

  @property({type: Boolean})
  _hideActionButton?: boolean;

  @property({type: Boolean})
  showDismiss = false;

  @property()
  _boundTransitionEndHandler?: (
    this: HTMLElement,
    ev: TransitionEvent
  ) => unknown;

  @property()
  _actionCallback?: () => void;

  /** @override */
  attached() {
    super.attached();
    this._boundTransitionEndHandler = () => this._handleTransitionEnd();
    this.addEventListener('transitionend', this._boundTransitionEndHandler);
  }

  /** @override */
  detached() {
    super.detached();
    if (this._boundTransitionEndHandler) {
      this.removeEventListener(
        'transitionend',
        this._boundTransitionEndHandler
      );
    }
  }

  show(text: string, actionText?: string, actionCallback?: () => void) {
    this.text = text;
    this.actionText = actionText;
    this._hideActionButton = !actionText;
    this._actionCallback = actionCallback;
    getRootElement().appendChild(this);
    this.shown = true;
  }

  hide() {
    this.shown = false;
    if (this._hasZeroTransitionDuration()) {
      getRootElement().removeChild(this);
    }
  }

  _handleDismissTap() {
    this.hide();
  }

  _hasZeroTransitionDuration() {
    const style = window.getComputedStyle(this);
    // transitionDuration is always given in seconds.
    const duration = Math.round(parseFloat(style.transitionDuration) * 100);
    return duration === 0;
  }

  _handleTransitionEnd() {
    if (this.shown) {
      return;
    }

    getRootElement().removeChild(this);
  }

  _handleActionTap(e: MouseEvent) {
    e.preventDefault();
    if (this._actionCallback) {
      this._actionCallback();
    }
  }
}
