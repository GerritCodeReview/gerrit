/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {LitElement, css, html} from 'lit';
import {customElement, property} from 'lit/decorators.js';
import {sharedStyles} from '../../../styles/shared-styles';
import {
  ChangeInfo,
  RelatedChangeAndCommitInfo,
  CommitId,
} from '../../../types/common';
import {ChangeStatus} from '../../../constants/constants';
import {isChangeInfo} from '../../../utils/change-util';
import {ifDefined} from 'lit/directives/if-defined.js';

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
        a {
          display: block;
        }
        :host,
        .changeContainer,
        a {
          max-width: 100%;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }
        .changeContainer {
          display: flex;
        }
        .strikethrough {
          color: var(--deemphasized-text-color);
          text-decoration: line-through;
        }
        .status {
          color: var(--deemphasized-text-color);
          font-weight: var(--font-weight-bold);
          margin-left: var(--spacing-xs);
        }
        .notCurrent {
          color: var(--warning-foreground);
        }
        .indirectRelation {
          color: var(--indirect-relation-text-color);
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
        .submittableCheck {
          padding-left: var(--spacing-s);
          color: var(--positive-green-text-color);
          display: none;
        }
        .submittableCheck.submittable {
          display: inline;
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
          ><slot></slot
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
    } else if (change.status === ChangeStatus.NEW) {
      classes.push('hidden');
    }
    return classes.join(' ');
  }

  private computeChangeStatus(change: RelatedChangeAndCommitInfo | ChangeInfo) {
    switch (change.status) {
      case ChangeStatus.MERGED:
        return 'Merged';
      case ChangeStatus.ABANDONED:
        return 'Abandoned';
    }
    if (
      !isChangeInfo(change) &&
      change._revision_number !== change._current_revision_number
    ) {
      return 'Not current';
    } else if (!isChangeInfo(change) && this.isIndirectRelation(change)) {
      return 'Indirect relation';
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
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-related-change': GrRelatedChange;
  }
}
