/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {css, html, LitElement, nothing} from 'lit';
import {customElement, state} from 'lit/decorators';
import {bulkActionsModelToken} from '../../../models/bulk-actions/bulk-actions-model';
import {resolve} from '../../../models/dependency';
import {pluralize} from '../../../utils/string-util';
import {subscribe} from '../../lit/subscription-controller';
import '../../shared/gr-button/gr-button';
import '../gr-change-list-reviewer-flow/gr-change-list-reviewer-flow';
import '../gr-change-list-bulk-vote-flow/gr-change-list-bulk-vote-flow';
import '../gr-change-list-topic-flow/gr-change-list-topic-flow';

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
      .container {
        display: flex;
        justify-content: space-between;
        align-items: center;
      }
      /*
       * checkbox styles match checkboxes in <gr-change-list-item> rows to
       * vertically align with them.
       */
      input {
        background-color: var(--background-color-primary);
        border: 1px solid var(--border-color);
        border-radius: var(--border-radius);
        box-sizing: border-box;
        color: var(--primary-text-color);
        margin: 0px;
        padding: var(--spacing-s);
        vertical-align: middle;
      }
    `;
  }

  @state()
  private numSelected = 0;

  @state()
  private totalChangeCount = 0;

  private readonly getBulkActionsModel = resolve(this, bulkActionsModelToken);

  constructor() {
    super();
    subscribe(
      this,
      () => this.getBulkActionsModel().selectedChangeNums$,
      selectedChangeNums => (this.numSelected = selectedChangeNums.length)
    );
    subscribe(
      this,
      () => this.getBulkActionsModel().totalChangeCount$,
      totalChangeCount => (this.totalChangeCount = totalChangeCount)
    );
  }

  override render() {
    const numSelectedLabel = `${pluralize(
      this.numSelected,
      'change'
    )} selected`;
    const checked =
      this.numSelected > 0 && this.numSelected === this.totalChangeCount;
    const indeterminate =
      this.numSelected > 0 && this.numSelected !== this.totalChangeCount;
    return html`
      <!-- Empty cell added for spacing just like gr-change-list-item rows -->
      <td></td>
      <td>
        <!--
          The .checked property must be used rather than the attribute because
          the attribute only controls the default checked state and does not
          update the current checked state.
          See: https://developer.mozilla.org/en-US/docs/Web/HTML/Element/input/checkbox#attr-checked
        -->
        <input
          type="checkbox"
          .checked=${checked}
          .indeterminate=${indeterminate}
          @click=${() => this.getBulkActionsModel().clearSelectedChangeNums()}
        />
      </td>
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
            <gr-change-list-reviewer-flow></gr-change-list-reviewer-flow>
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
