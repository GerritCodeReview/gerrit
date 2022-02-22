/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import { customElement, state } from "lit/decorators";
import { LitElement, html } from "lit";
import { resolve } from "../../../models/dependency";
import { bulkActionsModelToken } from "../../../models/bulk-actions/bulk-actions-model";
import { ChangeInfoId } from "../../../api/rest-api";

@customElement('gr-bulk-abandon-dialog')
export class GrBulkAbandonDialog extends LitElement {

  private readonly getBulkActionsModel = resolve(this, bulkActionsModelToken);

  @state() changeIds: ChangeInfoId[] = [];

  constructor() {
    super();
    this.getBulkActionsModel().selectedChangeIds$.subscribe(selectedChangeIds =>
      this.changeIds = selectedChangeIds);
  }

  override render() {
    return html `
      <section>
        <h3 class="heading-3">
          ${this.changeIds.length} changes to abandon
        </h3>
      </section>
      <section>
        ${this.changeIds.map(changeId => html `
          <span> ${changeId} </span>
        `)}
      </section>
      `
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-bulk-abandon-dialog': GrBulkAbandonDialog;
  }
}
