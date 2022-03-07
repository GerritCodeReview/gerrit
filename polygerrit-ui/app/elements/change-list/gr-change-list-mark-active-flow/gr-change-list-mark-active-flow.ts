/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {html, LitElement} from 'lit';
import {customElement, query, state} from 'lit/decorators';
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

  @state() selectedChanges: ChangeInfo[] = [];

  override connectedCallback(): void {
    super.connectedCallback();
    subscribe(
      this,
      this.getBulkActionsModel().selectedChanges$,
      selectedChanges => { this.selectedChanges = selectedChanges }
    );
  }

  override render() {
    return html`
      <gr-button
        ?disabled=${!this.isEnabled()}
        flatten
        @click=${() => this.overlay.open()}
        >mark as active</gr-button
      >
      <gr-overlay>
        <gr-dialog @cancel=${() => this.overlay.close()}>
          <div slot="header">Mark Changes as Active</div>
          <div slot="main">
            <div>
              Selected changes: ${
                this.selectedChanges.map(c => c._number).join(', ')}
            </div>
          </div>
        </gr-dialog>
      </gr-overlay>
    `;
  }

  private isEnabled(): boolean {
    // TODO: This is sample enable logic. Normally we would also use permissions
    // from the model data somehow.
    if (this.selectedChanges.length === 0) return false;
    return true;
  }
}
declare global {
  interface HTMLElementTagNameMap {
    'gr-change-list-mark-active-flow': GrChangeListMarkActiveFlow;
  }
}
