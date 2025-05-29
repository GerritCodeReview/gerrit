/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../gr-icon/gr-icon';
import '../gr-tooltip-content/gr-tooltip-content';
import '../../../styles/shared-styles';
import {ChangeInfo, ChangeStates, WebLinkInfo} from '../../../types/common';
import {sharedStyles} from '../../../styles/shared-styles';
import {css, html, LitElement, PropertyValues} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {createSearchUrl} from '../../../models/views/search';

export const WIP_TOOLTIP =
  "This change isn't ready to be reviewed or submitted. " +
  'It will not appear on dashboards unless you are in the attention set, ' +
  'and email notifications will be silenced until the review is started.';

export const MERGE_CONFLICT_TOOLTIP =
  'This change has merge conflicts. ' +
  'Rebase on the upstream branch (e.g. "git pull --rebase"). ' +
  'Upload a new patchset after resolving all merge conflicts.';

export const GIT_CONFLICT_TOOLTIP =
  'A file contents of the change contain git conflict markers' +
  'to indicate the conflicts.';

const PRIVATE_TOOLTIP =
  'This change is only visible to its owner and ' +
  'current reviewers (or anyone with "View Private Changes" permission).';

@customElement('gr-change-status')
export class GrChangeStatus extends LitElement {
  @property({type: Boolean, reflect: true})
  flat = false;

  @property({type: String})
  status?: ChangeStates;

  // Private but used in tests.
  @state()
  tooltipText = '';

  @property({type: Object})
  revertedChange?: ChangeInfo;

  @property({type: Object})
  resolveWeblinks?: WebLinkInfo[] = [];

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
          text-decoration: line-through;
        }
        :host(.wip) .chip {
          background-color: var(--status-wip);
          color: var(--status-wip);
        }
        :host(.private) .chip {
          background-color: var(--status-private);
          color: var(--status-private);
        }
        :host(.merge-conflict) .chip,
        :host(.git-conflict) .chip {
          background-color: var(--status-conflict);
          color: var(--status-conflict);
        }
        :host(.active) .chip {
          background-color: var(--status-active);
          --status-text-color: black;
          color: var(--status-active);
        }
        :host(.ready-to-submit) .chip {
          background-color: var(--status-ready);
          color: var(--status-ready);
        }
        :host(.revert) .chip {
          background-color: var(--status-revert);
          color: var(--status-revert);
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
        :host(:not([flat])) .chip gr-icon {
          color: var(--status-text-color);
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
            ? html`<gr-icon icon="edit" filled small></gr-icon>`
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
      return createSearchUrl({query: `${this.revertedChange._number}`});
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
      case ChangeStates.GIT_CONFLICT:
        this.tooltipText = GIT_CONFLICT_TOOLTIP;
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
