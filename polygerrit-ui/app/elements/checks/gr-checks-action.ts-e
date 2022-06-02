/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {LitElement, css, html} from 'lit';
import {customElement, property} from 'lit/decorators';
import {Action} from '../../api/checks';
import {checkRequiredProperty} from '../../utils/common-util';
import {resolve} from '../../models/dependency';
import {checksModelToken} from '../../models/checks/checks-model';
@customElement('gr-checks-action')
export class GrChecksAction extends LitElement {
  @property({type: Object})
  action!: Action;

  @property({type: Object})
  eventTarget: HTMLElement | null = null;

  /** In what context is <gr-checks-action> rendered? Just for reporting. */
  @property({type: String})
  context = 'unknown';

  private getChecksModel = resolve(this, checksModelToken);

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
        ?disabled=${this.action.disabled}
        class="action"
        @click=${(e: Event) => this.handleClick(e)}
      >
        ${this.action.name}
      </gr-button>
      ${this.renderTooltip()}
    `;
  }

  private renderTooltip() {
    if (!this.action.tooltip) return;
    return html`
      <paper-tooltip offset="5" ?fitToVisibleBounds=${true}>
        ${this.action.tooltip}
      </paper-tooltip>
    `;
  }

  handleClick(e: Event) {
    e.stopPropagation();
    this.getChecksModel().triggerAction(this.action, undefined, this.context);
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-checks-action': GrChecksAction;
  }
}
