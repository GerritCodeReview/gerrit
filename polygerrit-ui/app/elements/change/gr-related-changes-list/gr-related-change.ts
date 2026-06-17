/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css, html, LitElement, TemplateResult} from 'lit';
import {customElement, property} from 'lit/decorators.js';
import {sharedStyles} from '../../../styles/shared-styles';
import {
  ChangeInfo,
  CommitId,
  RelatedChangeAndCommitInfo,
  isDetailedLabelInfo,
  LabelInfo,
  QuickLabelInfo,
} from '../../../types/common';
import {ChangeStatus} from '../../../constants/constants';
import {isChangeInfo} from '../../../utils/change-util';
import {ifDefined} from 'lit/directives/if-defined.js';
import {isDefined} from '../../../types/types';
import '../../shared/gr-icon/gr-icon';

@customElement('gr-related-change')
export class GrRelatedChange extends LitElement {
  @property({type: Object})
  change?: ChangeInfo | RelatedChangeAndCommitInfo;

  @property()
  href?: string;

  @property()
  label?: string;

  @property({type: Boolean, attribute: 'show-submittable-check'})
  showSubmittableCheck = false;

  @property({type: Boolean, attribute: 'show-change-status'})
  showChangeStatus = false;

  /*
   * Needed for calculation if change is direct or indirect ancestor/descendant
   * to current change.
   */
  @property({type: Array})
  connectedRevisions?: CommitId[];

  static override get styles() {
    return [
      sharedStyles,
      css`
        :host,
        .changeContainer,
        a {
          max-width: 100%;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
          width: 100%;
          display: inline-flex;
        }
        .strikethrough {
          color: var(--deemphasized-text-color);
          text-decoration: line-through;
        }
        .status {
          color: var(--deemphasized-text-color);
          font-weight: var(--font-weight-medium);
          margin-left: var(--spacing-xs);
          margin-right: var(--spacing-m);
        }
        .notCurrent {
          color: var(--warning-foreground);
        }
        .indirectRelation {
          color: var(--indirect-relation-text-color);
        }
        .workInProgress {
          color: var(--status-wip);
        }
        .workInProgressLink {
          color: var(--deemphasized-text-color);
        }
        .submittableCheck {
          padding-left: var(--spacing-s);
          color: var(--positive-green-text-color);
          display: none;
        }
        .submittableCheck.submittable {
          display: inline;
        }
        .hidden,
        .mobile {
          display: none;
        }
        .badge {
          display: inline-flex;
          align-items: center;
          gap: 2px;
          border-radius: 4px;
          padding: 2px 4px;
          font-size: var(--font-size-small);
          font-weight: var(--font-weight-bold);
          margin-left: var(--spacing-s);
          height: 16px;
          line-height: 16px;
          align-self: center;
        }
        .badge.unresolved {
          background-color: var(--unresolved-comment-background-color);
          color: var(--primary-text-color);
        }
        .badge.approval.positive {
          background-color: var(--success-background);
          color: var(--success-foreground);
        }
        .badge.approval.negative {
          background-color: var(--error-background);
          color: var(--error-foreground);
        }
        .unresolved-icon {
          font-size: 14px;
          width: 14px;
          height: 14px;
        }
      `,
    ];
  }

  override render() {
    const change = this.change;
    if (!change) throw new Error('Missing change');
    const linkClass = this.computeLinkClass(change);
    return html`
      <div class="changeContainer">
        <a
          href=${ifDefined(this.href)}
          aria-label=${ifDefined(this.label)}
          class=${linkClass}
          ><slot name="name"></slot
        ></a>
        ${this.showSubmittableCheck
          ? html`<span
              tabindex="-1"
              title="Submittable"
              class="submittableCheck ${linkClass}"
              role="img"
              aria-label="Submittable"
              >✓</span
            >`
          : ''}
        ${this.showChangeStatus
          ? html`<span class=${this.computeChangeStatusClass(change)}>
              (${this.computeChangeStatus(change)})
            </span>`
          : ''}
        ${this.renderApprovals()}
        ${this.renderUnresolvedComments()}
        <slot name="extra"></slot>
      </div>
    `;
  }

  private computeLinkClass(change: ChangeInfo | RelatedChangeAndCommitInfo) {
    const statuses = [];
    if (change.status === ChangeStatus.ABANDONED) {
      statuses.push('strikethrough');
    }
    if (change.submittable) {
      statuses.push('submittable');
    }
    if (change.work_in_progress) {
      statuses.push('workInProgressLink');
    }
    return statuses.join(' ');
  }

  private computeChangeStatusClass(
    change: RelatedChangeAndCommitInfo | ChangeInfo
  ) {
    const classes = ['status'];
    if (
      !isChangeInfo(change) &&
      change._revision_number !== change._current_revision_number
    ) {
      classes.push('notCurrent');
    } else if (!isChangeInfo(change) && this.isIndirectRelation(change)) {
      classes.push('indirectRelation');
    } else if (change.submittable) {
      classes.push('submittable');
    } else if (change.work_in_progress) {
      classes.push('workInProgress');
    } else if (change.status === ChangeStatus.NEW) {
      classes.push('hidden');
    }
    return classes.join(' ');
  }

  private computeChangeStatus(change: RelatedChangeAndCommitInfo | ChangeInfo) {
    const isNotCurrent =
      !isChangeInfo(change) &&
      change._revision_number !== change._current_revision_number;
    switch (change.status) {
      case ChangeStatus.MERGED:
        return isNotCurrent ? 'Merged, not current' : 'Merged';
      case ChangeStatus.ABANDONED:
        return isNotCurrent ? 'Abandoned, not current' : 'Abandoned';
    }
    if (isNotCurrent) {
      return 'Not current';
    } else if (!isChangeInfo(change) && this.isIndirectRelation(change)) {
      return 'Indirect relation';
    } else if (change.work_in_progress) {
      return 'WIP';
    } else if (change.submittable) {
      return 'Submittable';
    }
    return '';
  }

  private isIndirectRelation(change: RelatedChangeAndCommitInfo) {
    return (
      this.connectedRevisions &&
      !this.connectedRevisions.includes(change.commit.commit)
    );
  }

  private getLabelScore(label: LabelInfo): number {
    if (isDetailedLabelInfo(label) && label.all) {
      const votes = label.all.map(a => a.value).filter(isDefined);
      if (votes.length === 0) return 0;
      const minVote = Math.min(...votes);
      const maxVote = Math.max(...votes);
      if (minVote < 0) {
        if (minVote === -2) return -2;
        if (maxVote === 2) return 2;
        return minVote;
      }
      return maxVote;
    }
    const quick = label as QuickLabelInfo;
    if (quick.rejected) return -2;
    if (quick.approved) return 2;
    if (quick.disliked) return -1;
    if (quick.recommended) return 1;
    return 0;
  }

  private renderApprovalBadge(labelAbbr: string, score: number) {
    const className = score > 0 ? 'positive' : 'negative';
    const sign = score > 0 ? '+' : '';
    return html`
      <span class="badge approval ${className}" title="${labelAbbr}: ${sign}${score}">
        ${labelAbbr} ${sign}${score}
      </span>
    `;
  }

  private renderApprovals() {
    if (!this.change || !this.change.labels) return '';
    const badges: TemplateResult[] = [];
    const cr = this.change.labels['Code-Review'];
    const v = this.change.labels['Verified'];

    if (cr) {
      const score = this.getLabelScore(cr);
      if (score !== 0) {
        badges.push(this.renderApprovalBadge('CR', score));
      }
    }
    if (v) {
      const score = this.getLabelScore(v);
      if (score !== 0) {
        badges.push(this.renderApprovalBadge('V', score));
      }
    }
    return badges;
  }

  private renderUnresolvedComments() {
    if (!this.change || !this.change.unresolved_comment_count) return '';
    return html`
      <span class="badge unresolved" title="Unresolved comments">
        <gr-icon icon="rate_review" class="unresolved-icon"></gr-icon>
        ${this.change.unresolved_comment_count}
      </span>
    `;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-related-change': GrRelatedChange;
  }
}
