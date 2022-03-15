/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {customElement, state, query} from 'lit/decorators';
import {LitElement, html, css} from 'lit';
import {resolve} from '../../../models/dependency';
import {bulkActionsModelToken} from '../../../models/bulk-actions/bulk-actions-model';
import {ChangeInfo, ChangeStatus} from '../../../api/rest-api';
import {subscribe} from '../../lit/subscription-controller';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';
import '../../shared/gr-dialog/gr-dialog';
import {fireAlert, fireReload} from '../../../utils/event-util';
import '../gr-change-list-bulk-action-table/gr-change-list-bulk-action-table';
import {GrChangeListBulkActionTable} from '../gr-change-list-bulk-action-table/gr-change-list-bulk-action-table';

@customElement('gr-change-list-bulk-abandon-flow')
export class GrChangeListBulkAbandonFlow extends LitElement {
  private readonly getBulkActionsModel = resolve(this, bulkActionsModelToken);

  @state() selectedChanges: ChangeInfo[] = [];

  @query('#actionOverlay') actionOverlay!: GrOverlay;

  @query('gr-change-list-bulk-action-table')
  bulkActionTable!: GrChangeListBulkActionTable;

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
      this.getBulkActionsModel().selectedChanges$,
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
            <gr-change-list-bulk-action-table
              .columnTitles=${['Subject']}
              .getColumnValues=${(change: ChangeInfo) => [
                `Change: ${change.subject}`,
              ]}
            ></gr-change-list-bulk-action-table>
          </div>
        </gr-dialog>
      </gr-overlay>
    `;
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
    return true;

    // for (const status of this.progress.values()) {
    //   if (status !== ProgressStatus.NOT_STARTED) return false;
    // }
    // return true;
  }

  private isCancelEnabled() {
    return true;
    // for (const status of this.progress.values()) {
    //   if (status === ProgressStatus.RUNNING) return false;
    // }
    // return true;
  }

  private handleConfirm() {
    this.bulkActionTable.trackBulkActionProgress(
      this.getBulkActionsModel().abandonChanges()
    );
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
