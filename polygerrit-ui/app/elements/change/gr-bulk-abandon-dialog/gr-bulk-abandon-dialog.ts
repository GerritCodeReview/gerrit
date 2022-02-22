/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {customElement, state} from 'lit/decorators';
import {LitElement, html, css} from 'lit';
import {resolve} from '../../../models/dependency';
import {bulkActionsModelToken} from '../../../models/bulk-actions/bulk-actions-model';
import {NumericChangeId} from '../../../api/rest-api';
import {fireEvent} from '../../../utils/event-util';
import {ProgressStatus} from '../gr-confirm-cherrypick-dialog/gr-confirm-cherrypick-dialog';

@customElement('gr-bulk-abandon-dialog')
export class GrBulkAbandonDialog extends LitElement {
  private readonly getBulkActionsModel = resolve(this, bulkActionsModelToken);

  @state() changeNums: NumericChangeId[] = [];

  @state() progress: Map<NumericChangeId, ProgressStatus> = new Map();

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
    this.getBulkActionsModel().selectedChangeNums$.subscribe(
      selectedChangeNums => (this.changeNums = selectedChangeNums)
    );
  }

  override render() {
    return html`
      <section>
        <h3 class="heading-3">${this.changeNums.length} changes to abandon</h3>
      </section>
      <section>
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
                    ${this.getBulkActionsModel().getChange(changeId).subject}
                  </td>
                  <td>Status: ${this.getStatus(changeId)}</td>
                </tr>
              `
            )}
          </tbody>
        </table>
      </section>
      <section>
        <gr-button @click=${() => fireEvent(this, 'cancel')}>
          Cancel
        </gr-button>
        <gr-button @click=${() => this.handleConfirm()}> Confirm </gr-button>
      </section>
    `;
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
    const errFn = (changeNum: NumericChangeId) => {
      this.progress.set(changeNum, ProgressStatus.FAILED);
    };
    this.getBulkActionsModel()
      .abandonChanges('', errFn)
      .then(results => {
        for (let i = 0; i < results.length; i++) {
          const changeNum = this.changeNums[i];
          if (!results[i]) this.progress.set(changeNum, ProgressStatus.FAILED);
          else this.progress.set(changeNum, ProgressStatus.FAILED);
        }
      });
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-bulk-abandon-dialog': GrBulkAbandonDialog;
  }
}
