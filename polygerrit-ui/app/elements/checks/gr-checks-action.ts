/**
 * @license
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import {html} from 'lit-html';
import {css, customElement, property} from 'lit-element';
import {GrLitElement} from '../lit/gr-lit-element';
import {Action} from '../../api/checks';
import {checkRequiredProperty} from '../../utils/common-util';
import {fireActionTriggered} from '../../services/checks/checks-util';

@customElement('gr-checks-action')
export class GrChecksAction extends GrLitElement {
  @property()
  action!: Action;

  @property()
  eventTarget?: EventTarget;

  override connectedCallback() {
    super.connectedCallback();
    checkRequiredProperty(this.action, 'action');
  }

  static override get styles() {
    return [
      css`
        :host {
          display: inline-block;
          white-space: nowrap;
        }
        gr-button {
          /* It is not fully understood why this is needed, but otherwise the
             paper-tooltip may render under some iron-icons of the content
             below. Maybe this has to do with a z-index:0 setting for
             paper-button, such that a stacking context is created. And the high
             z-index of the paper-tooltip will then only be interpreted within
             that stacking context. */
          z-index: 1;
          --padding: var(--spacing-s) var(--spacing-m);
        }
        gr-button paper-tooltip {
          text-transform: none;
          text-align: center;
          white-space: normal;
          width: 200px;
        }
      `,
    ];
  }

  override render() {
    return html`
      <gr-button
        link
        ?disabled="${this.action.disabled}"
        class="action"
        @click="${(e: Event) => this.handleClick(e)}"
      >
        ${this.action.name}
        <paper-tooltip
          ?hidden="${!this.action.tooltip}"
          offset="5"
          fit-to-visible-bounds="true"
          >${this.action.tooltip}</paper-tooltip
        >
      </gr-button>
    `;
  }

  handleClick(e: Event) {
    e.stopPropagation();
    fireActionTriggered(this.eventTarget ?? this, this.action);
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-checks-action': GrChecksAction;
  }
}
