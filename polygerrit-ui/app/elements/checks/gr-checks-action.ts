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
import {LitElement, css, html} from 'lit';
import {customElement, property} from 'lit/decorators';
import {Action} from '../../api/checks';
import {checkRequiredProperty} from '../../utils/common-util';
import {appContext} from '../../services/app-context';

@customElement('gr-checks-action')
export class GrChecksAction extends LitElement {
  @property({type: Object})
  action!: Action;

  @property({type: Object})
  eventTarget: HTMLElement | null = null;

  private checksService = appContext.checksService;

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
          --gr-button-padding: var(--spacing-s) var(--spacing-m);
        }
        paper-tooltip {
          text-transform: none;
          text-align: center;
          white-space: normal;
          max-width: 200px;
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
      </gr-button>
      ${this.renderTooltip()}
    `;
  }

  private renderTooltip() {
    if (!this.action.tooltip) return;
    return html`
      <paper-tooltip offset="5" ?fitToVisibleBounds="${true}">
        ${this.action.tooltip}
      </paper-tooltip>
    `;
  }

  handleClick(e: Event) {
    e.stopPropagation();
    this.checksService.triggerAction(this.action);
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-checks-action': GrChecksAction;
  }
}
