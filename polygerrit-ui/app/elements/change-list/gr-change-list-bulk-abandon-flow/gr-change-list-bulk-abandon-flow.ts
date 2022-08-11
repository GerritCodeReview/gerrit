/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {customElement, state, query} from 'lit/decorators.js';
import {LitElement, html, css} from 'lit';
import {resolve} from '../../../models/dependency';
import {bulkActionsModelToken} from '../../../models/bulk-actions/bulk-actions-model';
import {NumericChangeId, ChangeInfo, ChangeStatus} from '../../../api/rest-api';
import {subscribe} from '../../lit/subscription-controller';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';
import {ProgressStatus} from '../../../constants/constants';
import '../../shared/gr-dialog/gr-dialog';
import {fireAlert, fireReload} from '../../../utils/event-util';

@customElement('gr-change-list-bulk-abandon-flow')
export class GrChangeListBulkAbandonFlow extends LitElement {
  private readonly getBulkActionsModel = resolve(this, bulkActionsModelToken);

  @state() selectedChanges: ChangeInfo[] = [];

  @state() progress: Map<NumericChangeId, ProgressStatus> = new Map();

  @query('#actionOverlay') actionOverlay!: GrOverlay;

  static override get styles() {
    return [
      css`
        section {
          padding: var(--spacing-l);
        }
      `,
    ];
  }

  constructor() {
    super();
    subscribe(
      this,
      () => this.getBulkActionsModel().selectedChanges$,
      selectedChanges => (this.selectedChanges = selectedChanges)
    );
  }

  override render() {
    return html`
      <gr-button
        id="abandon"
        flatten
        .disabled=${!this.isEnabled()}
        @click=${() => this.actionOverlay.open()}
        >Abandon</gr-button
      >
      <gr-overlay id="actionOverlay" with-backdrop="">
        <gr-dialog
          .disableCancel=${!this.isCancelEnabled()}
          .disabled=${!this.isConfirmEnabled()}
          @confirm=${() => this.handleConfirm()}
          @cancel=${() => this.handleClose()}
          .cancelLabel=${'Close'}
        >
          <div slot="header">
            ${this.selectedChanges.length} changes to abandon
          </div>
          <div slot="main">
            <table>
              <thead>
                <tr>
                  <th>Subject</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                ${this.selectedChanges.map(
                  change => html`
                    <tr>
                      <td>Change: ${change.subject}</td>
                      <td id="status">
                        Status: ${this.getStatus(change._number)}
                      </td>
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

  private getStatus(changeNum: NumericChangeId) {
    return this.progress.has(changeNum)
      ? this.progress.get(changeNum)
      : ProgressStatus.NOT_STARTED;
  }

  private isEnabled() {
    return this.selectedChanges.every(
      change =>
        !!change.actions?.abandon || change.status === ChangeStatus.ABANDONED
    );
  }

  private isConfirmEnabled() {
    // Action is allowed if none of the changes have any bulk action performed
    // on them. In case an error happens then we keep the button disabled.
    for (const status of this.progress.values()) {
      if (status !== ProgressStatus.NOT_STARTED) return false;
    }
    return true;
  }

  private isCancelEnabled() {
    for (const status of this.progress.values()) {
      if (status === ProgressStatus.RUNNING) return false;
    }
    return true;
  }

  private handleConfirm() {
    this.progress.clear();
    for (const change of this.selectedChanges) {
      this.progress.set(change._number, ProgressStatus.RUNNING);
    }
    this.requestUpdate();
    const errFn = (changeNum: NumericChangeId) => {
      throw new Error(`request for ${changeNum} failed`);
    };
    const promises = this.getBulkActionsModel().abandonChanges('', errFn);
    for (let index = 0; index < promises.length; index++) {
      const changeNum = this.selectedChanges[index]._number;
      promises[index]
        .then(() => {
          this.progress.set(changeNum, ProgressStatus.SUCCESSFUL);
          this.requestUpdate();
        })
        .catch(() => {
          this.progress.set(changeNum, ProgressStatus.FAILED);
          this.requestUpdate();
        });
    }
  }

  private handleClose() {
    this.actionOverlay.close();
    fireAlert(this, 'Reloading page..');
    fireReload(this, true);
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-change-list-bulk-abandon-flow': GrChangeListBulkAbandonFlow;
  }
}
