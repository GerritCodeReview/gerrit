/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import { customElement, query, state } from "lit/decorators";
import { LitElement, html } from "lit";
import { GrOverlay } from "../../shared/gr-overlay/gr-overlay";
import { resolve } from "../../../models/dependency";
import { bulkActionsModelToken } from "../../../models/bulk-actions/bulk-actions-model";
import { subscribe } from "../../lit/subscription-controller";
import { ChangeInfo } from "../../../api/rest-api";

@customElement('gr-change-list-bulk-vote-flow')
export class GrChangeListBulkVoteFlow extends LitElement {

  private readonly getBulkActionsModel = resolve(this, bulkActionsModelToken);

  @state() selectedChanges: ChangeInfo[] = [];

  @query('#actionOverlay') actionOverlay!: GrOverlay;

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
        id="vote"
        flatten
        >Vote</gr-button
      >
      <gr-overlay id="actionOverlay" with-backdrop="">
        <gr-dialog
          .cancelLabel=${'Close'}
        >
        </gr-dialog>
      </gr-overlay>
    `;
  }
}


declare global {
  interface HTMLElementTagNameMap {
    'gr-change-list-bulk-vote-flow': GrChangeListBulkVoteFlow;
  }
}
