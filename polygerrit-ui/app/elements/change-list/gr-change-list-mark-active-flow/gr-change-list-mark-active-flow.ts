/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {html, LitElement} from 'lit';
import {customElement, query} from 'lit/decorators';
import {bulkActionsModelToken} from '../../../models/bulk-actions/bulk-actions-model';
import {resolve} from '../../../models/dependency';
import {ChangeInfo} from '../../../types/common';
import {subscribe} from '../../lit/subscription-controller';
import '../../shared/gr-overlay/gr-overlay';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';

@customElement('gr-change-list-mark-active-flow')
export class GrChangeListMarkActiveFlow extends LitElement {
  @query('gr-overlay')
  private overlay!: GrOverlay;

  private readonly getBulkActionsModel = resolve(this, bulkActionsModelToken);

  override connectedCallback(): void {
    super.connectedCallback();
    subscribe(
      this,
      this.getBulkActionsModel().selectedChangeNums$,
      _selectedChangeNums => {
        this.requestUpdate();
      }
    );
    subscribe(this, this.getBulkActionsModel().loadingState$, _loadingState => {
      this.requestUpdate();
    });
  }

  override render() {
    const changes = this.getBulkActionsModel().getSelectedChanges();
    return html`
      <gr-button
        .disabled=${!this.isEnabled(changes)}
        flatten
        @click=${() => this.overlay.open()}
        >mark as active</gr-button
      >
      <gr-overlay>
        <gr-dialog @cancel=${() => this.overlay.close()}>
          <div slot="header">Mark Changes as Active</div>
          <div slot="main">
            <div>
              Selected changes: ${changes.map(c => c._number).join(', ')}
            </div>
          </div>
        </gr-dialog>
      </gr-overlay>
    `;
  }

  private isEnabled(changes: ChangeInfo[]): boolean {
    if (changes.length === 0) return false;
    return true;
  }
}
declare global {
  interface HTMLElementTagNameMap {
    'gr-change-list-mark-active-flow': GrChangeListMarkActiveFlow;
  }
}
