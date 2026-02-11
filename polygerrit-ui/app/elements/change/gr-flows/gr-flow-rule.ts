/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {customElement, property} from 'lit/decorators.js';
import {css, html, LitElement, nothing} from 'lit';
import {sharedStyles} from '../../../styles/shared-styles';
import {FlowStageState} from '../../../api/rest-api';
import {capitalizeFirstLetter} from '../../../utils/string-util';
import '../../shared/gr-icon/gr-icon';

export const iconForFlowStageState = (status: FlowStageState) => {
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
};

@customElement('gr-flow-rule')
export class GrFlowRule extends LitElement {
  @property({type: String})
  state?: FlowStageState;

  @property({type: String})
  condition = '';

  @property({type: String})
  action?: string;

  @property({type: Array})
  parameters?: string[];

  static override get styles() {
    return [
      sharedStyles,
      css`
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

  /**
   * Formats a flow action name for display.
   * Converts snake_case (e.g., 'add_reviewer') to Title Case (e.g., 'Add Reviewer').
   */
  private formatActionName(name?: string): string {
    if (!name) return '';
    return name
      .split('_')
      .map(word => capitalizeFirstLetter(word))
      .join(' ');
  }

  private renderParameters() {
    if (!this.parameters || this.parameters.length === 0) return nothing;
    return html`
      ${this.parameters.map(
        p => html`<span class="parameter"><code>${p}</code></span>`
      )}
    `;
  }

  override render() {
    const actionText = this.formatActionName(this.action);
    const icon = this.state ? iconForFlowStageState(this.state) : undefined;

    return html`
      <div class="stage">
        ${icon
          ? html`<gr-icon
              class=${icon.class}
              icon=${icon.icon}
              ?filled=${icon.filled}
              aria-label=${this.state?.toLowerCase() ?? ''}
              role="img"
            ></gr-icon>`
          : nothing}
        <span class="condition">${this.condition}</span>
        ${this.action ? html`
        <gr-icon icon="arrow_forward" class="arrow"></gr-icon>
        <div class="stage-action">
          <b>${actionText}</b>
          ${this.renderParameters()}
        </div>` : nothing}
      </div>
    `;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-flow-rule': GrFlowRule;
  }
}
