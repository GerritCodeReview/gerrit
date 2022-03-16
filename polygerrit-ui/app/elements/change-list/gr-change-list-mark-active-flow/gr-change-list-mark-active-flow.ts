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

@customElement('gr-change-list-mark-active-flow')
export class GrChangeListMarkActiveFlow extends LitElement {
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
      this.selectedChanges.every(change => change.actions?.ready);
    const confirmLabel = this.isReady()
      ? 'Apply'
      : this.isDone()
      ? 'Close'
      : 'Running';
    return html`
      <gr-button
        .disabled=${!enabled}
        flatten
        @click=${() => this.overlay.open()}
        >mark as active</gr-button
      >
      <gr-overlay>
        <gr-dialog
          @cancel=${() => this.overlay.close()}
          @confirm=${() => this.onConfirm()}
          .confirmLabel=${confirmLabel}
          .disabled=${!this.isReady() && !this.isDone()}
        >
          <div slot="header">
            Mark ${pluralize(this.selectedChanges.length, 'change')} as active
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
    if (this.isReady()) {
      this.markChangesActive();
    } else if (this.isDone()) {
      this.overlay.close();
    }
  }

  private markChangesActive() {
    this.progressByChange = new Map(
      this.selectedChanges.map(change => [change, ProgressStatus.RUNNING])
    );
    const inFlightActions = this.getBulkActionsModel().markChangesActive();
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

  private isReady() {
    return Array.from(this.progressByChange.values()).every(
      status => status === ProgressStatus.NOT_STARTED
    );
  }

  private isDone() {
    return Array.from(this.progressByChange.values()).every(status =>
      [ProgressStatus.FAILED, ProgressStatus.SUCCESSFUL].includes(status)
    );
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-change-list-mark-active-flow': GrChangeListMarkActiveFlow;
  }
}
