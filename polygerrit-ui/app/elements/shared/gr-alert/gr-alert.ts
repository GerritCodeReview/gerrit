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
import {getRootElement} from '../../../scripts/rootElement';
import {ErrorType} from '../../../types/types';
import {LitElement, css, html} from 'lit';
import {customElement, property} from 'lit/decorators';
import {sharedStyles} from '../../../styles/shared-styles';

declare global {
  interface HTMLElementTagNameMap {
    'gr-alert': GrAlert;
  }
}

@customElement('gr-alert')
export class GrAlert extends LitElement {
  static override get styles() {
    return [
      sharedStyles,
      css`
        /**
         * ALERT: DO NOT ADD TRANSITION PROPERTIES WITHOUT PROPERLY UNDERSTANDING
         * HOW THEY ARE USED IN THE CODE.
         */
        :host([toast]) {
          background-color: var(--tooltip-background-color);
          bottom: 1.25rem;
          border-radius: var(--border-radius);
          box-shadow: var(--elevation-level-2);
          color: var(--tooltip-text-color);
          left: 1.25rem;
          position: fixed;
          transform: translateY(5rem);
          transition: transform var(--gr-alert-transition-duration, 80ms)
            ease-out;
          z-index: 1000;
        }
        :host([shown]) {
          transform: translateY(0);
        }
        /**
         * NOTE: To avoid style being overwritten by outside of the shadow DOM
         * (as outside styles always win), .content-wrapper is introduced as a
         * wrapper around main content to have better encapsulation, styles that
         * may be affected by outside should be defined on it.
         * In this case, \`padding:0px\` is defined in main.css for all elements
         * with the universal selector: *.
         */
        .content-wrapper {
          padding: var(--spacing-l) var(--spacing-xl);
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
          font-weight: var(--font-weight-bold);
          margin-left: var(--spacing-l);
          text-decoration: none;
          --gr-button-padding: 0;
        }
      `,
    ];
  }

  renderDismissButton() {
    if (!this.showDismiss) return '';
    return html`<gr-button
      link=""
      class="action"
      @click=${this._handleDismissTap}
      >Dismiss</gr-button
    >`;
  }

  override render() {
    const {text, actionText} = this;
    return html`
      <div class="content-wrapper">
        <span class="text">${text}</span>
        <gr-button
          link=""
          class="action"
          ?hidden="${this._hideActionButton}"
          @click=${this._handleActionTap}
          >${actionText}
        </gr-button>
        ${this.renderDismissButton()}
      </div>
    `;
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

  @property({type: Boolean, reflect: true})
  shown = true;

  @property({type: Boolean, reflect: true})
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

  override connectedCallback() {
    super.connectedCallback();
    this._boundTransitionEndHandler = () => this._handleTransitionEnd();
    this.addEventListener('transitionend', this._boundTransitionEndHandler);
  }

  override disconnectedCallback() {
    if (this._boundTransitionEndHandler) {
      this.removeEventListener(
        'transitionend',
        this._boundTransitionEndHandler
      );
    }
    super.disconnectedCallback();
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
    this.hide();
  }
}
