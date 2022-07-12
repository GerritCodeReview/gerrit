/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../gr-tooltip-content/gr-tooltip-content';
import {LitElement, css, html, nothing} from 'lit';
import {customElement, property} from 'lit/decorators';
import {
  ApprovalInfo,
  isDetailedLabelInfo,
  isQuickLabelInfo,
  LabelInfo,
} from '../../../api/rest-api';
import {
  classForLabelStatus,
  getLabelStatus,
  valueString,
} from '../../../utils/label-util';

declare global {
  interface HTMLElementTagNameMap {
    'gr-vote-chip': GrVoteChip;
  }
}
/**
 * @attr {Boolean} circle-shape - element has shape as circle
 */
@customElement('gr-vote-chip')
export class GrVoteChip extends LitElement {
  @property({type: Object})
  vote?: ApprovalInfo;

  @property({type: Object})
  label?: LabelInfo;

  /** Show vote as a stack of same votes. */
  @property({type: Boolean})
  more = false;

  /**
   * If defined, vote-chip is shown with this value instead of the latest vote.
   * This is useful for change log.
   */
  @property()
  displayValue?: string;

  @property({type: Boolean, attribute: 'tooltip-with-who-voted'})
  tooltipWithWhoVoted = false;

  static override get styles() {
    return [
      css`
        :host([circle-shape]) .vote-chip {
          border-radius: 50%;
          border: none;
          padding: 2px;
        }
        .vote-chip.max {
          background-color: var(--vote-color-approved);
          padding: 2px;
        }
        .more > .vote-chip.max {
          padding: 1px;
          border: 1px solid var(--vote-outline-recommended);
        }
        .vote-chip.min {
          background-color: var(--vote-color-rejected);
          padding: 2px;
        }
        .more > .vote-chip.min {
          padding: 1px;
          border: 1px solid var(--vote-outline-disliked);
        }
        .vote-chip.positive,
        .chip-angle.max,
        .chip-angle.positive {
          background-color: var(--vote-color-recommended);
          border: 1px solid var(--vote-outline-recommended);
          color: var(--chip-color);
        }
        .vote-chip.negative,
        .chip-angle.min,
        .chip-angle.negative {
          background-color: var(--vote-color-disliked);
          border: 1px solid var(--vote-outline-disliked);
          color: var(--chip-color);
        }
        .vote-chip,
        .chip-angle {
          display: flex;
          width: var(--gr-vote-chip-width, 16px);
          height: var(--gr-vote-chip-height, 16px);
          font-size: var(--font-size-small);
          justify-content: center;
          padding: 1px;
          border-radius: var(--border-radius);
          line-height: var(--gr-vote-chip-width, 16px);
          color: var(--vote-text-color);
        }
        .more > .vote-chip {
          position: relative;
          z-index: 2;
        }
        .more > .chip-angle {
          position: absolute;
          top: 2px;
          left: 2px;
          z-index: 1;
        }
        .container {
          position: relative;
        }
      `,
    ];
  }

  override render() {
    const renderValue = this.renderValue();
    if (!renderValue) return;

    return html`<gr-tooltip-content
      class="container ${this.more ? 'more' : ''}"
      title=${this.computeTooltip()}
      has-tooltip
    >
      <div class="vote-chip ${this.computeClass()}">${renderValue}</div>
      ${this.more
        ? html`<div class="chip-angle ${this.computeClass()}">
            ${renderValue}
          </div>`
        : ''}
    </gr-tooltip-content>`;
  }

  private renderValue() {
    if (this.displayValue) {
      return this.displayValue;
    }
    if (!this.label) {
      return nothing;
    } else if (isDetailedLabelInfo(this.label)) {
      if (this.vote?.value) {
        return valueString(this.vote.value);
      }
    } else if (isQuickLabelInfo(this.label)) {
      if (this.label.approved) {
        return html`&#x2713;`; // check mark
      } else if (this.label.rejected) {
        return html`&#x2717;`; // x mark
      } else if (this.label.disliked || this.label.recommended) {
        return valueString(this.label.value);
      }
    }
    return nothing;
  }

  private computeClass() {
    if (!this.label) {
      return '';
    } else if (this.displayValue) {
      const status = getLabelStatus(this.label, Number(this.displayValue));
      return classForLabelStatus(status);
    } else {
      const status = getLabelStatus(this.label, this.vote?.value);
      return classForLabelStatus(status);
    }
  }

  private computeTooltip() {
    if (!this.label || !isDetailedLabelInfo(this.label)) {
      return '';
    }
    const voteDescription =
      this.label.values?.[valueString(this.vote?.value)] ?? '';

    if (this.tooltipWithWhoVoted && this.vote) {
      return `${this.vote?.name}: ${voteDescription}`;
    } else {
      return voteDescription;
    }
  }
}
