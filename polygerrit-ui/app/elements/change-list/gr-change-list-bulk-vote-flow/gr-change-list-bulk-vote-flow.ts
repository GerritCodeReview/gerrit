/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {customElement, query, state} from 'lit/decorators';
import {LitElement, html} from 'lit';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';
import {resolve} from '../../../models/dependency';
import {bulkActionsModelToken} from '../../../models/bulk-actions/bulk-actions-model';
import {subscribe} from '../../lit/subscription-controller';
import {ChangeInfo, AccountInfo} from '../../../api/rest-api';
import {
  getTriggerVotes,
  computeLabels,
  computeColumns,
} from '../../../utils/label-util';
import {changeModelToken} from '../../../models/change/change-model';
import {getAppContext} from '../../../services/app-context';
import {ParsedChangeInfo} from '../../../types/types';
import {hasOwnProperty} from '../../../utils/common-util';

@customElement('gr-change-list-bulk-vote-flow')
export class GrChangeListBulkVoteFlow extends LitElement {
  private readonly getBulkActionsModel = resolve(this, bulkActionsModelToken);

  private readonly getChangeModel = resolve(this, changeModelToken);

  private readonly userModel = getAppContext().userModel;

  @state() selectedChanges: ChangeInfo[] = [];

  @query('#actionOverlay') actionOverlay!: GrOverlay;

  @state() change?: ParsedChangeInfo;

  @state() account?: AccountInfo;

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
    const labels = this.computeLabels(this.selectedChanges);
    const labelValues = computeColumns(this.change?.permitted_labels);
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
              ${labels.map(
                label => html`<gr-label-score-row
                  class="${this.computeLabelAccessClass(label.name)}"
                  .label="${label}"
                  .name="${label.name}"
                  .labels="${this.change?.labels}"
                  .permittedLabels="${this.change?.permitted_labels}"
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

  private computeLabels(_changes: ChangeInfo[]) {
    const triggerVotes = getTriggerVotes(this.change);
    const labels = computeLabels(this.account, this.change).filter(
      label => !triggerVotes.includes(label.name)
    );
    return labels;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-change-list-bulk-vote-flow': GrChangeListBulkVoteFlow;
  }
}
