/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css, html, LitElement, nothing} from 'lit';
import {customElement, state} from 'lit/decorators.js';
import {bulkActionsModelToken} from '../../../models/bulk-actions/bulk-actions-model';
import {resolve} from '../../../models/dependency';
import {pluralize} from '../../../utils/string-util';
import {subscribe} from '../../lit/subscription-controller';
import '../../shared/gr-button/gr-button';
import '../gr-change-list-reviewer-flow/gr-change-list-reviewer-flow';
import '../gr-change-list-bulk-vote-flow/gr-change-list-bulk-vote-flow';
import '../gr-change-list-topic-flow/gr-change-list-topic-flow';
import '../gr-change-list-hashtag-flow/gr-change-list-hashtag-flow';
import '../gr-change-list-bulk-abandon-flow/gr-change-list-bulk-abandon-flow';
/**
 * An action bar for the top of a <gr-change-list-section> element. Assumes it
 * will be used inside a <tr> element.
 */
@customElement('gr-change-list-action-bar')
export class GrChangeListActionBar extends LitElement {
  static override get styles() {
    return css`
      :host {
        display: contents;
      }
      td {
        padding: 0;
      }
      .container {
        display: flex;
        justify-content: space-between;
        align-items: center;
      }
      .actionButtons {
        margin-right: var(--spacing-l);
      }
    `;
  }

  @state()
  private numSelected = 0;

  private readonly getBulkActionsModel = resolve(this, bulkActionsModelToken);

  constructor() {
    super();
    subscribe(
      this,
      () => this.getBulkActionsModel().selectedChangeNums$,
      selectedChangeNums => (this.numSelected = selectedChangeNums.length)
    );
  }

  override render() {
    const numSelectedLabel = `${pluralize(
      this.numSelected,
      'change'
    )} selected`;
    return html`
      <!--
        500 chosen to be more than the actual number of columns but less than
        1000 where the browser apparently decides it is an error and reverts
        back to colspan="1"
      -->
      <td colspan="500">
        <div class="container">
          <div class="selectionInfo">
            ${this.numSelected
              ? html`<span>${numSelectedLabel}</span>`
              : nothing}
          </div>
          <div class="actionButtons">
            <gr-change-list-bulk-vote-flow></gr-change-list-bulk-vote-flow>
            <gr-change-list-topic-flow></gr-change-list-topic-flow>
            <gr-change-list-hashtag-flow></gr-change-list-hashtag-flow>
            <gr-change-list-bulk-abandon-flow></gr-change-list-bulk-abandon-flow>
          </div>
        </div>
      </td>
    `;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-change-list-action-bar': GrChangeListActionBar;
  }
}
