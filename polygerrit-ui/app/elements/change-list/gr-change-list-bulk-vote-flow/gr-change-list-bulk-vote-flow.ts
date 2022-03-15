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
import {
  ChangeInfo,
  AccountInfo,
  LabelNameToValueMap,
} from '../../../api/rest-api';
import {
  getTriggerVotes,
  computeLabels,
  computeColumns,
} from '../../../utils/label-util';
import {changeModelToken} from '../../../models/change/change-model';
import {getAppContext} from '../../../services/app-context';
import {ParsedChangeInfo} from '../../../types/types';
import {hasOwnProperty} from '../../../utils/common-util';
import {fontStyles} from '../../../styles/gr-font-styles';

@customElement('gr-change-list-bulk-vote-flow')
export class GrChangeListBulkVoteFlow extends LitElement {
  private readonly getBulkActionsModel = resolve(this, bulkActionsModelToken);

  private readonly getChangeModel = resolve(this, changeModelToken);

  private readonly userModel = getAppContext().userModel;

  @state() selectedChanges: ChangeInfo[] = [];

  @query('#actionOverlay') actionOverlay!: GrOverlay;

  @state() change?: ParsedChangeInfo;

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
      this.getChangeModel().change$,
      change => (this.change = change)
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
    const labelValues = computeColumns(permittedLabels);
    return html`
      <gr-button id="vote" flatten @click=${() => this.actionOverlay.open()}
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
                  .labelValues="${labelValues}"
                ></gr-label-score-row>`
              )}
            </div>
          </div>
        </gr-dialog>
      </gr-overlay>
    `;
  }

  private computeLabelAccessClass(label?: string) {
    if (!this.change?.permitted_labels || !label) return '';

    return hasOwnProperty(this.change?.permitted_labels, label) &&
      this.change?.permitted_labels[label].length
      ? 'access'
      : 'no-access';
  }

  // private but used in tests
  computePermittedLabels() {
    // Reduce method for empty array throws error if no initial value specified
    if (this.selectedChanges.length === 0) return {};
    return this.selectedChanges
      .map(changes => changes.permitted_labels)
      .reduce((previous, current) => {
        if (!current || !previous) return current;
        const result: LabelNameToValueMap = {};
        for (const label of Object.keys(previous)) {
          if (!current[label]) continue;
          const range1 = new Set(previous[label].map(v => Number(v)));
          const range2 = new Set(current[label].map(v => Number(v)));
          // compute intersection of ranges for the label
          result[label] = Array.from(range1)
            .filter(value => range2.has(value))
            .map(v => (v <= 0 ? `${v}` : `+${v}`));
        }
        return result;
      });
  }

  // private but used in tests
  computeLabels(change: ChangeInfo) {
    const triggerVotes = getTriggerVotes(change);
    const labels = computeLabels(this.account, change).filter(
      label => !triggerVotes.includes(label.name)
    );
    return labels;
  }

  // private but used in tests
  computeCommonLabels() {
    // Reduce method for empty array throws error if no initial value specified
    if (this.selectedChanges.length === 0) return [];
    return this.selectedChanges
      .map(change => this.computeLabels(change))
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
