/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../shared/gr-tooltip-content/gr-tooltip-content';
import {css, html, LitElement, PropertyValues} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {Action, NOT_USEFUL, USEFUL} from '../../api/checks';
import {assertIsDefined} from '../../utils/common-util';
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

  @property({type: String, reflect: true})
  icon?: string;

  @state()
  clicked = false;

  private getChecksModel = resolve(this, checksModelToken);

  override connectedCallback() {
    super.connectedCallback();
    assertIsDefined(this.action, 'action');
  }

  static override get styles() {
    return [
      css`
        :host {
          display: inline-block;
          white-space: nowrap;
        }
        :host([icon*='thumb']) gr-button {
          display: block;
          --gr-button-padding: 0 var(--spacing-s);
        }
        gr-button {
          --gr-button-padding: var(--spacing-s) var(--spacing-m);
        }
        gr-tooltip-content {
          text-transform: none;
          text-align: center;
          white-space: normal;
        }
      `,
    ];
  }

  override willUpdate(_changedProperties: PropertyValues): void {
    if (this.action.name === USEFUL) {
      this.icon = 'thumb_up';
    } else if (this.action.name === NOT_USEFUL) {
      this.icon = 'thumb_down';
    } else {
      this.icon = undefined;
    }
  }

  override render() {
    if (!this.action.tooltip) {
      return this.renderButton();
    }

    return html`
      <gr-tooltip-content
        has-tooltip
        ?position-below=${true}
        .maxWidth=${'200px'}
        title=${this.action.tooltip}
      >
        ${this.renderButton()}
      </gr-tooltip-content>
    `;
  }

  private renderName() {
    if (!this.icon) return html`${this.action.name}`;
    return html`
      <gr-icon ?filled=${this.clicked} icon=${this.icon}></gr-icon>
    `;
  }

  private renderButton() {
    return html`
      <gr-button
        link
        ?disabled=${this.action.disabled}
        class="action"
        @click=${(e: Event) => this.handleClick(e)}
      >
        ${this.renderName()}
      </gr-button>
    `;
  }

  handleClick(e: Event) {
    if (this.action.name === USEFUL || this.action.name === NOT_USEFUL) {
      this.clicked = true;
    } else {
      // For useful clicks the parent wants to receive the click for changing
      // the "Was this helpful?" label.
      e.stopPropagation();
    }
    this.getChecksModel().triggerAction(this.action, undefined, this.context);
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-checks-action': GrChecksAction;
  }
}
