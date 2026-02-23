/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {customElement, property, state} from 'lit/decorators.js';
import {css, html, LitElement, nothing, PropertyValues} from 'lit';
import {sharedStyles} from '../../../styles/shared-styles';
import {AccountDetailInfo, FlowStageState} from '../../../api/rest-api';
import {formatActionName} from '../../../utils/flows-util';
import '../../shared/gr-icon/gr-icon';
import '../../shared/gr-tooltip-content/gr-tooltip-content';
import {ifDefined} from 'lit/directives/if-defined.js';
import {getAppContext} from '../../../services/app-context';
import '../../shared/gr-avatar/gr-avatar';
import '../../shared/gr-account-label/gr-account-label';
import {UserId} from '../../../types/common';

@customElement('gr-flow-rule')
export class GrFlowRule extends LitElement {
  @property({type: String})
  state?: FlowStageState;

  @property({type: String})
  message?: string;

  @property({type: String})
  condition = '';

  @property({type: String})
  action?: string;

  @property({type: Array})
  parameters?: string[];

  @property({type: String})
  parameterStr?: string;

  @state()
  private accounts = new Map<string, AccountDetailInfo | null>();

  private readonly restApiService = getAppContext().restApiService;

  static override get styles() {
    return [
      sharedStyles,
      css`
        :host {
          display: block;
        }
        .stage {
          display: flex;
          align-items: center;
          gap: var(--spacing-m);
        }
        .stage-action {
          display: flex;
          align-items: center;
          flex-wrap: wrap;
          gap: var(--spacing-s);
        }
        .parameter {
          background-color: var(--background-color-secondary);
          padding: 2px 4px;
          border-radius: var(--border-radius);
          font-family: var(--monospace-font-family);
          font-size: var(--font-size-small);
        }
        .account-parameter {
          display: inline-flex;
          align-items: center;
          gap: var(--spacing-s);
          background-color: var(--background-color-secondary);
          padding: 2px 4px;
          border-radius: var(--border-radius);
        }
        .arrow {
          color: var(--deemphasized-text-color);
          margin: 0 var(--spacing-xs);
          font-size: 16px;
        }
        .condition {
          color: var(--deemphasized-text-color);
        }
        b {
          font-weight: bolder;
        }
        gr-icon {
          font-size: var(--line-height-normal, 20px);
          vertical-align: middle;
        }
        gr-icon.done {
          color: var(--success-foreground);
        }
        gr-icon.pending {
          color: var(--deemphasized-text-color);
        }
        gr-icon.failed {
          color: var(--error-foreground);
        }
        .error {
          color: var(--error-foreground);
        }
      `,
    ];
  }

  private iconForFlowStageState(status: FlowStageState) {
    switch (status) {
      case FlowStageState.DONE:
        return {icon: 'check_circle', filled: true, class: 'done'};
      case FlowStageState.PENDING:
        return {icon: 'timelapse', filled: false, class: 'pending'};
      case FlowStageState.FAILED:
        return {icon: 'error', filled: true, class: 'failed'};
      case FlowStageState.TERMINATED:
        return {icon: 'error', filled: true, class: 'failed'};
      default:
        return {icon: 'help', filled: false, class: 'other'};
    }
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('parameterStr')) {
      this.parameters = this.parameterStr?.trim()
        ? this.parameterStr.trim().split(/\s+/)
        : [];
    }
    if (changedProperties.has('parameters')) {
      this.updateAccounts();
    }
  }

  private updateAccounts() {
    if (!this.parameters) {
      if (this.accounts.size > 0) this.accounts = new Map();
      return;
    }

    const promises = this.parameters.map(async p => {
      // Simple email regex check
      if (/\S+@\S+\.\S+/.test(p)) {
        try {
          const account = await this.restApiService.getAccountDetails(
            p as UserId
          );
          return {key: p, value: account ?? null};
        } catch (e) {
          console.error(`Failed to fetch account for ${p}`, e);
          return {key: p, value: null};
        }
      }
      return {key: p, value: null};
    });

    Promise.all(promises).then(results => {
      const newAccounts = new Map<string, AccountDetailInfo | null>();
      for (const result of results) {
        newAccounts.set(result.key, result.value);
      }
      this.accounts = newAccounts;
    });
  }

  private renderParameters() {
    if (!this.parameters || this.parameters.length === 0) return nothing;
    return html`
      ${this.parameters.map(p => {
        const account = this.accounts.get(p);
        if (account) {
          return html`
            <span class="account-parameter">
              <gr-avatar .account=${account} .imageSize=${16}></gr-avatar>
              <gr-account-label .account=${account}></gr-account-label>
            </span>
          `;
        }
        return html`<span class="parameter"><code>${p}</code></span>`;
      })}
    `;
  }

  private isFailingState() {
    return (
      this.state === FlowStageState.FAILED ||
      this.state === FlowStageState.TERMINATED
    );
  }

  override render() {
    const actionText = formatActionName(this.action);
    const icon = this.state
      ? this.iconForFlowStageState(this.state)
      : undefined;

    return html`
      <div class="stage">
        ${icon
          ? html`<gr-tooltip-content
              ?has-tooltip=${!!this.message && !this.isFailingState()}
              title=${ifDefined(
                this.message && !this.isFailingState()
                  ? this.message
                  : undefined
              )}
            >
              <gr-icon
                class=${icon.class}
                icon=${icon.icon}
                ?filled=${icon.filled}
                aria-label=${this.state?.toLowerCase() ?? ''}
                role="img"
              ></gr-icon>
            </gr-tooltip-content>`
          : nothing}
        <span class="condition">${this.condition}</span>
        ${this.action
          ? html` <gr-icon icon="arrow_forward" class="arrow"></gr-icon>
              <div class="stage-action">
                <b>${actionText}</b>
                ${this.renderParameters()}
              </div>`
          : nothing}
        ${this.message && this.isFailingState()
          ? html`<span class="error">${this.message}</span>`
          : nothing}
      </div>
    `;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-flow-rule': GrFlowRule;
  }
}
