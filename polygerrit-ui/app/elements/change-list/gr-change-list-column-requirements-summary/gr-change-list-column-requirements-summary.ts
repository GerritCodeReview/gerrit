/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../change/gr-submit-requirement-dashboard-hovercard/gr-submit-requirement-dashboard-hovercard';
import '../../shared/gr-change-status/gr-change-status';
import {LitElement, css, html, TemplateResult} from 'lit';
import {customElement, property} from 'lit/decorators';
import {ChangeInfo, SubmitRequirementStatus} from '../../../api/rest-api';
import {changeStatuses} from '../../../utils/change-util';
import {
  getRequirements,
  iconForStatus,
  SubmitRequirementsIcon,
} from '../../../utils/label-util';
import {submitRequirementsStyles} from '../../../styles/gr-submit-requirements-styles';
import {pluralize} from '../../../utils/string-util';
import {iconStyles} from '../../../styles/gr-icon-styles';

@customElement('gr-change-list-column-requirements-summary')
export class GrChangeListColumnRequirementsSummary extends LitElement {
  @property({type: Object})
  change?: ChangeInfo;

  static override get styles() {
    return [
      iconStyles,
      submitRequirementsStyles,
      css`
        .material-icon {
          font-size: var(--line-height-normal, 20px);
        }
        .material-icon.block,
        .material-icon.check_circle {
          margin-right: var(--spacing-xs);
        }
        .material-icon.commentIcon {
          color: var(--deemphasized-text-color);
          margin-left: var(--spacing-s);
        }
        span {
          line-height: var(--line-height-normal);
        }
        .unsatisfied {
          color: var(--primary-text-color);
        }
        .total {
          margin-left: var(--spacing-xs);
          color: var(--deemphasized-text-color);
        }
        :host {
          align-items: center;
          display: inline-flex;
        }
        .comma {
          padding-right: var(--spacing-xs);
        }
        /* Used to hide the leading separator comma for statuses. */
        .comma:first-of-type {
          display: none;
        }
      `,
    ];
  }

  override render() {
    const commentIcon = this.renderCommentIcon();
    return html`${this.renderChangeStatus()} ${commentIcon}`;
  }

  renderChangeStatus() {
    if (!this.change) return;
    const statuses = changeStatuses(this.change);
    if (statuses.length > 0) {
      return statuses.map(
        status => html`
          <div class="comma">,</div>
          <gr-change-status flat .status=${status}></gr-change-status>
        `
      );
    }
    return this.renderActiveStatus();
  }

  renderActiveStatus() {
    const submitRequirements = getRequirements(this.change);
    if (!submitRequirements.length) return html`n/a`;
    const numRequirements = submitRequirements.length;
    const numSatisfied = submitRequirements.filter(
      req =>
        req.status === SubmitRequirementStatus.SATISFIED ||
        req.status === SubmitRequirementStatus.OVERRIDDEN
    ).length;

    if (numSatisfied === numRequirements) {
      return this.renderState(
        iconForStatus(SubmitRequirementStatus.SATISFIED),
        'Ready'
      );
    }

    const numUnsatisfied = submitRequirements.filter(
      req => req.status === SubmitRequirementStatus.UNSATISFIED
    ).length;

    return this.renderState(
      iconForStatus(SubmitRequirementStatus.UNSATISFIED),
      this.renderSummary(numUnsatisfied)
    );
  }

  renderState(
    icon: SubmitRequirementsIcon,
    aggregation: string | TemplateResult
  ) {
    return html`<span class=${icon.icon} role="button" tabindex="0">
      <gr-submit-requirement-dashboard-hovercard .change=${this.change}>
      </gr-submit-requirement-dashboard-hovercard>
      <span
        class="material-icon ${icon.icon} ${icon.filled ? 'filled' : ''}"
        role="img"
        >${icon.icon}</span
      >${aggregation}</span
    >`;
  }

  renderSummary(numUnsatisfied: number) {
    return html`<span class="unsatisfied">${numUnsatisfied} missing</span>`;
  }

  renderCommentIcon() {
    if (!this.change?.unresolved_comment_count) return;
    return html`<span
      class="commentIcon material-icon filled"
      .title=${pluralize(
        this.change?.unresolved_comment_count,
        'unresolved comment'
      )}
      >mode_comment</span
    >`;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-change-list-column-requirements-summary': GrChangeListColumnRequirementsSummary;
  }
}
