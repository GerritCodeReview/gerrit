/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../gr-tooltip-content/gr-tooltip-content';
import '../gr-icons/gr-icons';
import '../../../styles/shared-styles';
import {
  GeneratedWebLink,
  GerritNav,
} from '../../core/gr-navigation/gr-navigation';
import {ChangeInfo} from '../../../types/common';
import {ParsedChangeInfo} from '../../../types/types';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, PropertyValues, html, css} from 'lit';
import {customElement, property} from 'lit/decorators';

export enum ChangeStates {
  ABANDONED = 'Abandoned',
  ACTIVE = 'Active',
  MERGE_CONFLICT = 'Merge Conflict',
  MERGED = 'Merged',
  PRIVATE = 'Private',
  READY_TO_SUBMIT = 'Ready to submit',
  REVERT_CREATED = 'Revert Created',
  REVERT_SUBMITTED = 'Revert Submitted',
  WIP = 'WIP',
}

export const WIP_TOOLTIP =
  "This change isn't ready to be reviewed or submitted. " +
  "It will not appear on dashboards unless you are CC'ed, " +
  'and email notifications will be silenced until the review is started.';

export const MERGE_CONFLICT_TOOLTIP =
  'This change has merge conflicts. ' +
  'Download the patch and run "git rebase". ' +
  'Upload a new patchset after resolving all merge conflicts.';

const PRIVATE_TOOLTIP =
  'This change is only visible to its owner and ' +
  'current reviewers (or anyone with "View Private Changes" permission).';

@customElement('gr-change-status')
export class GrChangeStatus extends LitElement {
  @property({type: Boolean, reflect: true})
  flat = false;

  @property({type: Object})
  change?: ChangeInfo | ParsedChangeInfo;

  @property({type: String})
  status?: ChangeStates;

  @property({type: String})
  tooltipText = '';

  @property({type: Object})
  revertedChange?: ChangeInfo;

  @property({type: Object})
  resolveWeblinks?: GeneratedWebLink[] = [];

  static override get styles() {
    return [
      sharedStyles,
      css`
        .chip {
          border-radius: var(--border-radius);
          background-color: var(--chip-background-color);
          padding: 0 var(--spacing-m);
          white-space: nowrap;
        }
        :host(.merged) .chip {
          background-color: var(--status-merged);
          color: var(--status-merged);
        }
        :host(.abandoned) .chip {
          background-color: var(--status-abandoned);
          color: var(--status-abandoned);
        }
        :host(.wip) .chip {
          background-color: var(--status-wip);
          color: var(--status-wip);
        }
        :host(.private) .chip {
          background-color: var(--status-private);
          color: var(--status-private);
        }
        :host(.merge-conflict) .chip {
          background-color: var(--status-conflict);
          color: var(--status-conflict);
        }
        :host(.active) .chip {
          background-color: var(--status-active);
          color: var(--status-active);
        }
        :host(.ready-to-submit) .chip {
          background-color: var(--status-ready);
          color: var(--status-ready);
        }
        :host(.revert-created) .chip {
          background-color: var(--status-revert-created);
          color: var(--status-revert-created);
        }
        :host(.revert-submitted) .chip {
          background-color: var(--status-revert-created);
          color: var(--status-revert-created);
        }
        .status-link {
          text-decoration: none;
        }
        :host(.custom) .chip {
          background-color: var(--status-custom);
          color: var(--status-custom);
        }
        :host([flat]) .chip {
          background-color: transparent;
          padding: 0;
        }
        :host(:not([flat])) .chip,
        .icon {
          color: var(--status-text-color);
        }
        .icon {
          --iron-icon-height: 18px;
          --iron-icon-width: 18px;
        }
      `,
    ];
  }

  override render() {
    return html`
      <gr-tooltip-content
        has-tooltip
        position-below
        .title=${this.tooltipText}
        .maxWidth=${'40em'}
      >
        ${this.renderStatusLink()}
      </gr-tooltip-content>
    `;
  }

  private renderStatusLink() {
    if (!this.hasStatusLink()) {
      return html`
        <div class="chip" aria-label="Label: ${this.status}">
          ${this.computeStatusString()}
        </div>
      `;
    }

    return html`
      <a class="status-link" href=${this.getStatusLink()}>
        <div class="chip" aria-label="Label: ${this.status}">
          ${this.computeStatusString()}
          ${this.showResolveIcon()
            ? html`<iron-icon class="icon" icon="gr-icons:edit"></iron-icon>`
            : ''}
        </div>
      </a>
    `;
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('status')) {
      this.updateChipDetails(changedProperties.get('status') as ChangeStates);
    }
  }

  private computeStatusString() {
    if (this.status === ChangeStates.WIP && !this.flat) {
      return 'Work in Progress';
    }
    return this.status ?? '';
  }

  private toClassName(str?: ChangeStates) {
    return str ? str.toLowerCase().replace(/\s/g, '-') : '';
  }

  // private but used in test
  hasStatusLink(): boolean {
    const isRevertCreatedOrSubmitted =
      (this.status === ChangeStates.REVERT_SUBMITTED ||
        this.status === ChangeStates.REVERT_CREATED) &&
      this.revertedChange !== undefined;
    return (
      isRevertCreatedOrSubmitted ||
      !!(
        this.status === ChangeStates.MERGE_CONFLICT &&
        this.resolveWeblinks?.length
      )
    );
  }

  // private but used in test
  getStatusLink(): string {
    if (this.revertedChange) {
      return GerritNav.getUrlForSearchQuery(`${this.revertedChange._number}`);
    }
    if (
      this.status === ChangeStates.MERGE_CONFLICT &&
      this.resolveWeblinks?.length
    ) {
      return this.resolveWeblinks[0].url ?? '';
    }
    return '';
  }

  // private but used in test
  showResolveIcon(): boolean {
    return (
      this.status === ChangeStates.MERGE_CONFLICT &&
      !!this.resolveWeblinks?.length
    );
  }

  private updateChipDetails(previousStatus?: ChangeStates) {
    if (previousStatus) {
      this.classList.remove(this.toClassName(previousStatus));
    }
    this.classList.add(this.toClassName(this.status));

    switch (this.status) {
      case ChangeStates.WIP:
        this.tooltipText = WIP_TOOLTIP;
        break;
      case ChangeStates.PRIVATE:
        this.tooltipText = PRIVATE_TOOLTIP;
        break;
      case ChangeStates.MERGE_CONFLICT:
        this.tooltipText = MERGE_CONFLICT_TOOLTIP;
        break;
      default:
        this.tooltipText = '';
        break;
    }
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-change-status': GrChangeStatus;
  }
}
