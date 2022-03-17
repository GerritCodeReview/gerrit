/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {html, LitElement} from 'lit';
import {customElement, query, state} from 'lit/decorators';
import {ProgressStatus} from '../../../constants/constants';
import {bulkActionsModelToken} from '../../../models/bulk-actions/bulk-actions-model';
import {resolve} from '../../../models/dependency';
import {ChangeInfo} from '../../../types/common';
import {pluralize} from '../../../utils/string-util';
import {subscribe} from '../../lit/subscription-controller';
import '../../shared/gr-overlay/gr-overlay';
import '../../shared/gr-dialog/gr-dialog';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';

@customElement('gr-change-list-submit-flow')
export class GrChangeListSubmitFlow extends LitElement {
  @state() private selectedChanges: ChangeInfo[] = [];

  @state()
  private progressByChange = new Map<ChangeInfo, ProgressStatus>();

  @query('gr-overlay') private overlay!: GrOverlay;

  private readonly getBulkActionsModel = resolve(this, bulkActionsModelToken);

  override connectedCallback(): void {
    super.connectedCallback();
    subscribe(
      this,
      this.getBulkActionsModel().selectedChanges$,
      selectedChanges => {
        this.selectedChanges = selectedChanges;
        this.progressByChange = new Map(
          this.selectedChanges.map(change => [
            change,
            ProgressStatus.NOT_STARTED,
          ])
        );
      }
    );
  }

  override render() {
    const enabled =
      this.selectedChanges.length > 0 &&
      this.selectedChanges.every(change => change.actions?.submit);
    const status = this.getOverallStatus();
    const confirmLabel =
      status === ProgressStatus.NOT_STARTED
        ? 'Apply'
        : status !== ProgressStatus.RUNNING
        ? 'Close'
        : 'Running';
    return html`
      <gr-button
        .disabled=${!enabled}
        flatten
        @click=${() => this.overlay.open()}
        >submit</gr-button
      >
      <gr-overlay with-backdrop>
        <gr-dialog
          @cancel=${() => this.overlay.close()}
          @confirm=${() => this.onConfirm()}
          .confirmLabel=${confirmLabel}
          .disabled=${status === ProgressStatus.RUNNING}
        >
          <div slot="header">
            Submit ${pluralize(this.selectedChanges.length, 'change')}
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
                      <td>${change.subject}</td>
                      <td>${this.progressByChange.get(change)}</td>
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

  private onConfirm() {
    const status = this.getOverallStatus();
    if (status === ProgressStatus.NOT_STARTED) {
      this.submitChanges();
    } else if (status !== ProgressStatus.RUNNING) {
      this.overlay.close();
    }
  }

  private submitChanges() {
    this.progressByChange = new Map(
      this.selectedChanges.map(change => [change, ProgressStatus.RUNNING])
    );
    const inFlightActions = this.getBulkActionsModel().submitChanges();
    for (let index = 0; index < this.selectedChanges.length; index++) {
      const change = this.selectedChanges[index];
      inFlightActions[index]
        .then(() => {
          this.progressByChange.set(change, ProgressStatus.SUCCESSFUL);
          this.requestUpdate();
        })
        .catch(() => {
          this.progressByChange.set(change, ProgressStatus.FAILED);
          this.requestUpdate();
        });
    }
  }

  private getOverallStatus() {
    const statuses = Array.from(this.progressByChange.values());
    if (statuses.every(s => s === ProgressStatus.NOT_STARTED)) {
      return ProgressStatus.NOT_STARTED;
    }
    if (statuses.some(s => s === ProgressStatus.RUNNING)) {
      return ProgressStatus.RUNNING;
    }
    return ProgressStatus.SUCCESSFUL;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-change-list-submit-flow': GrChangeListSubmitFlow;
  }
}
