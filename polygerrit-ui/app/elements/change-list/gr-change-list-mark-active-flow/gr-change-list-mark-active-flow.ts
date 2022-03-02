/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {html, LitElement} from 'lit';
import {customElement, query, state} from 'lit/decorators';
import {bulkActionsModelToken} from '../../../models/bulk-actions/bulk-actions-model';
import {resolve} from '../../../models/dependency';
import {NumericChangeId} from '../../../types/common';
import {subscribe} from '../../lit/subscription-controller';
import '../../shared/gr-overlay/gr-overlay';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';

@customElement('gr-change-list-mark-active-flow')
export class GrChangeListMarkActiveFlow extends LitElement {
  @state()
  private selectedChangeNums: NumericChangeId[] = [];

  @query('gr-overlay')
  private overlay?: GrOverlay;

  private readonly getBulkActionsModel = resolve(this, bulkActionsModelToken);

  override connectedCallback(): void {
    super.connectedCallback();
    subscribe(
      this,
      this.getBulkActionsModel().selectedChangeNums$,
      selectedChangeNums => (this.selectedChangeNums = selectedChangeNums)
    );
  }

  private isEnabled() {
    // sample enable logic. Normally we would use permissions from the model
    // data somehow.
    return this.selectedChangeNums.length > 1;
  }

  override render() {
    return html`
      <gr-button
        .disabled=${!this.isEnabled()}
        flatten
        @click=${() => this.overlay?.open()}
        >mark as active</gr-button
      >
      <gr-overlay>
        <gr-dialog @cancel=${() => this.overlay?.close()}>
          <div slot="header">Mark Changes as Active</div>
          <div slot="main">
            <div>Selected changes: ${this.selectedChangeNums.join(', ')}</div>
          </div>
        </gr-dialog>
      </gr-overlay>
    `;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-change-list-mark-active-flow': GrChangeListMarkActiveFlow;
  }
}
