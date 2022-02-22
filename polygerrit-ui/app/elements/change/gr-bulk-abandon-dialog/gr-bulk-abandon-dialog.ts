/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {customElement, state} from 'lit/decorators';
import {LitElement, html, css} from 'lit';
import {resolve} from '../../../models/dependency';
import {bulkActionsModelToken} from '../../../models/bulk-actions/bulk-actions-model';
import {ChangeInfoId} from '../../../api/rest-api';
import {fireEvent} from '../../../utils/event-util';

@customElement('gr-bulk-abandon-dialog')
export class GrBulkAbandonDialog extends LitElement {
  private readonly getBulkActionsModel = resolve(this, bulkActionsModelToken);

  @state() changeIds: ChangeInfoId[] = [];

  static override get styles() {
    return [
      css`
        :host {
          display: flex;
        }
        section {
          padding: var(--spacing-m);
        }
      `,
    ];
  }

  override connectedCallback() {
    super.connectedCallback();
    this.getBulkActionsModel().selectedChangeIds$.subscribe(
      selectedChangeIds => (this.changeIds = selectedChangeIds)
    );
  }

  override render() {
    return html`
      <section>
        <h3 class="heading-3">${this.changeIds.length} changes to abandon</h3>
      </section>
      <section>
        ${this.changeIds.map(
          changeId => html`
            <div>
              <div>Change: ${changeId}</div>
              <div>Status: ${this.getStatus(changeId)}</div>
            </div>
          `
        )}
      </section>
      <section>
        <gr-button @click=${() => fireEvent(this, 'cancel')}>
          Cancel
        </gr-button>
        <gr-button @click=${() => this.handleConfirm()}> Confirm </gr-button>
      </section>
    `;
  }

  private getStatus(changeId: ChangeInfoId) {
    return changeId;
  }

  private async handleConfirm() {
    await this.getBulkActionsModel().abandonChanges();
    fireEvent(this, 'reload');
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-bulk-abandon-dialog': GrBulkAbandonDialog;
  }
}
