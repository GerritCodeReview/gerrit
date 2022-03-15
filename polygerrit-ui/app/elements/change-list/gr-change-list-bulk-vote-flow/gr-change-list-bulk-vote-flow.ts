/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {customElement, query, state} from 'lit/decorators';
import {LitElement, html, css} from 'lit';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';
import {resolve} from '../../../models/dependency';
import {bulkActionsModelToken} from '../../../models/bulk-actions/bulk-actions-model';
import {subscribe} from '../../lit/subscription-controller';
import {ChangeInfo, AccountInfo} from '../../../api/rest-api';
import {
  getTriggerVotes,
  computeLabels,
  mergeLabelMaps,
  computeOrderedLabelValues,
} from '../../../utils/label-util';
import {getAppContext} from '../../../services/app-context';
import {fontStyles} from '../../../styles/gr-font-styles';
import '../../shared/gr-dialog/gr-dialog';
import '../../change/gr-label-score-row/gr-label-score-row';

@customElement('gr-change-list-bulk-vote-flow')
export class GrChangeListBulkVoteFlow extends LitElement {
  private readonly getBulkActionsModel = resolve(this, bulkActionsModelToken);

  private readonly userModel = getAppContext().userModel;

  @state() selectedChanges: ChangeInfo[] = [];

  @query('#actionOverlay') actionOverlay!: GrOverlay;

  @state() account?: AccountInfo;

  static override get styles() {
    return [
      fontStyles,
      css`
        .scoresTable {
          display: table;
        }
        .scoresTable.newSubmitRequirements {
          table-layout: fixed;
        }
        gr-label-score-row:hover {
          background-color: var(--hover-background-color);
        }
        gr-label-score-row {
          display: table-row;
        }
        gr-label-score-row.no-access {
          display: none;
        }
        .heading-3 {
          padding-left: var(--spacing-xl);
          margin-bottom: var(--spacing-m);
          margin-top: var(--spacing-l);
          display: table-caption;
        }
        .heading-3:first-of-type {
          margin-top: 0;
        }
      `,
    ];
  }

  override connectedCallback() {
    super.connectedCallback();
    subscribe(
      this,
      this.getBulkActionsModel().selectedChanges$,
      selectedChanges => (this.selectedChanges = selectedChanges)
    );
    subscribe(
      this,
      this.userModel.account$,
      account => (this.account = account)
    );
  }

  override render() {
    const labels = this.computeCommonLabels();
    const permittedLabels = this.computePermittedLabels();
    // TODO: disable button if no label can be voted upon
    return html`
      <gr-button flatten @click=${() => this.actionOverlay.open()}
        >Vote</gr-button
      >
      <gr-overlay id="actionOverlay" with-backdrop="">
        <gr-dialog
          @cancel=${() => this.actionOverlay.close()}
          .cancelLabel=${'Close'}
        >
          <div slot="main">
            <div class="scoresTable newSubmitRequirements">
              <h3 class="heading-3">Submit requirements votes</h3>
              ${labels.map(
                label => html`<gr-label-score-row
                  class="${this.computeLabelAccessClass(label.name)}"
                  .label="${label}"
                  .name="${label.name}"
                  .labels="${labels}"
                  .permittedLabels="${permittedLabels}"
                  .orderedLabelValues="${computeOrderedLabelValues(
                    permittedLabels
                  )}"
                ></gr-label-score-row>`
              )}
            </div>
            <!-- TODO: Add section for trigger votes -->
          </div>
        </gr-dialog>
      </gr-overlay>
    `;
  }

  private computeLabelAccessClass(_label?: string) {
    // TODO: Add logic for this, removing labels that are not part of
    // permittedLabels by setting no-access class that hides them.
    return 'access';
  }

  // private but used in tests
  computePermittedLabels() {
    // Reduce method for empty array throws error if no initial value specified
    if (this.selectedChanges.length === 0) return {};

    return this.selectedChanges
      .filter(changes => !!changes.permitted_labels)
      .map(changes => changes.permitted_labels)
      .reduce(mergeLabelMaps);
  }

  // private but used in tests
  computeNonTriggerLabels(change: ChangeInfo) {
    const triggerVotes = getTriggerVotes(change);
    const labels = computeLabels(this.account, change).filter(
      label => !triggerVotes.includes(label.name)
    );
    return labels;
  }

  // private but used in tests
  // TODO: Remove Code Review label explicitly
  computeCommonLabels() {
    // Reduce method for empty array throws error if no initial value specified
    if (this.selectedChanges.length === 0) return [];
    return this.selectedChanges
      .map(change => this.computeNonTriggerLabels(change))
      .reduce((prev, current) =>
        current.filter(label => prev.some(l => l.name === label.name))
      );
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-change-list-bulk-vote-flow': GrChangeListBulkVoteFlow;
  }
}
