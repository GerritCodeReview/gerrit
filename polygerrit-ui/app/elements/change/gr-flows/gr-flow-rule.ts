/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {customElement, property} from 'lit/decorators.js';
import {css, html, LitElement, nothing} from 'lit';
import {sharedStyles} from '../../../styles/shared-styles';
import {FlowStageState} from '../../../api/rest-api';
import {formatActionName} from '../../../utils/flows-util';
import '../../shared/gr-icon/gr-icon';
import '../../shared/gr-tooltip-content/gr-tooltip-content';

@customElement('gr-flow-rule')
export class GrFlowRule extends LitElement {
  @property({type: String})
  state?: FlowStageState;

  @property({type: String})
  errorMessage?: string;

  @property({type: String})
  condition = '';

  @property({type: String})
  action?: string;

  @property({type: Array})
  parameters?: string[];

  @property({type: String})
  parameterStr?: string;

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
        .arrow {
          color: var(--deemphasized-text-color);
          margin: 0 var(--spacing-xs);
          font-size: 16px;
        }
        .condition {
          color: var(--deemphasized-text-color);
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

  private getParameters(): string[] {
    if (this.parameters && this.parameters.length > 0) return this.parameters;
    if (this.parameterStr?.trim()) {
      return this.parameterStr.trim().split(/\s+/);
    }
    return [];
  }

  private renderParameters() {
    const params = this.getParameters();
    if (params.length === 0) return nothing;
    return html`
      ${params.map(p => html`<span class="parameter"><code>${p}</code></span>`)}
    `;
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
              has-tooltip=${this.errorMessage ? 'true' : 'false'}
              title=${this.errorMessage || ''}
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
      </div>
    `;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-flow-rule': GrFlowRule;
  }
}
