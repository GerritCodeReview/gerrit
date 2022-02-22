/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {customElement, state, query} from 'lit/decorators';
import {LitElement, html, css} from 'lit';
import {resolve} from '../../../models/dependency';
import {bulkActionsModelToken} from '../../../models/bulk-actions/bulk-actions-model';
import {NumericChangeId} from '../../../api/rest-api';
import {ProgressStatus} from '../../change/gr-confirm-cherrypick-dialog/gr-confirm-cherrypick-dialog';
import {subscribe} from '../../lit/subscription-controller';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';

@customElement('gr-change-list-bulk-abandon-flow')
export class GrChangeListBulkAbandonFlow extends LitElement {
  private readonly getBulkActionsModel = resolve(this, bulkActionsModelToken);

  @state() changeNums: NumericChangeId[] = [];

  @state() progress: Map<NumericChangeId, ProgressStatus> = new Map();

  @query('#actionOverlay') actionOverlay!: GrOverlay;

  @state() disableAbandonButton = true;

  static override get styles() {
    return [
      css`
        section {
          padding: var(--spacing-l);
        }
      `,
    ];
  }

  override connectedCallback() {
    super.connectedCallback();
    subscribe(
      this,
      this.getBulkActionsModel().selectedChangeNums$,
      selectedChangeNums => (this.changeNums = selectedChangeNums)
    );
    subscribe(
      this,
      this.getBulkActionsModel().abandonable$,
      abandonable => (this.disableAbandonButton = !abandonable)
    );
  }

  override render() {
    return html`
      <gr-button
        id="abandon"
        flatten
        ?disabled=${this.disableAbandonButton}
        @click=${() => this.openAbandonDialog()}
        >Abandon</gr-button
      >
      <gr-overlay id="actionOverlay" with-backdrop="">
        <gr-dialog
          @click=${() => this.handleConfirm()}
          @cancel=${() => this.closeAbandonDialog()}
        >
          <div slot="header">${this.changeNums.length} changes to abandon</div>
          <div slot="main">
            <table>
              <thead>
                <tr>
                  <th>Subject</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                ${this.changeNums.map(
                  changeId => html`
                    <tr>
                      <td>
                        Change:
                        ${this.getBulkActionsModel().getChange(changeId)
                          .subject}
                      </td>
                      <td id="status">Status: ${this.getStatus(changeId)}</td>
                    </tr>
                  `
                )}
              </tbody>
            </table>
          </div>
        </gr-dialog>
      </gr-overlay>
    `;
  }

  private openAbandonDialog() {
    this.actionOverlay.open();
  }

  private closeAbandonDialog() {
    this.actionOverlay.close();
  }

  private getStatus(changeNum: NumericChangeId) {
    return this.progress.has(changeNum)
      ? this.progress.get(changeNum)
      : ProgressStatus.NOT_STARTED;
  }

  private async handleConfirm() {
    this.progress.clear();
    this.changeNums.forEach(changeNum =>
      this.progress.set(changeNum, ProgressStatus.RUNNING)
    );
    // reflect updated progress in the UI
    this.progress = new Map(this.progress);
    const errFn = (changeNum: NumericChangeId) => {
      this.progress.set(changeNum, ProgressStatus.FAILED);
    };
    this.getBulkActionsModel()
      .abandonChanges('', errFn)
      .then(results => {
        for (let i = 0; i < results.length; i++) {
          const changeNum = this.changeNums[i];
          if (results[i]?.status !== 200)
            this.progress.set(changeNum, ProgressStatus.FAILED);
          else this.progress.set(changeNum, ProgressStatus.SUCCESSFUL);
          // reflect updated progress in the UI
          this.progress = new Map(this.progress);
        }
      });
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-change-list-bulk-abandon-flow': GrChangeListBulkAbandonFlow;
  }
}
