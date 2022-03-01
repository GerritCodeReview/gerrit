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

interface ActionButton {
  name: string;
  onClick: Function;
}

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
      .selectionInfo gr-button {
        margin-left: var(--spacing-xxl);
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

  private readonly getBulkActionsModel = resolve(this, bulkActionsModelToken);

  private readonly actionButtons: ActionButton[] = [
    {name: 'abandon', onClick: () => this.onAbandonClicked()},
  ];

  override connectedCallback(): void {
    super.connectedCallback();
    subscribe(
      this,
      this.getBulkActionsModel().selectedChangeNums$,
      selectedChangeNums => (this.numSelected = selectedChangeNums.length)
    );
  }

  override render() {
    const numSelectedLabel = `${pluralize(
      this.numSelected,
      'change'
    )} selected`;
    return html`
      <!-- Empty cell added for spacing just like gr-change-list-item rows -->
      <td></td>
      <!-- TODO: Make checkbox reflect the overall selection of the section -->
      <!-- TODO: Clear behavior -->
      <td><input type="checkbox" /></td>
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
            ${this.actionButtons.map(
              ({name, onClick}) =>
                html`<gr-button flatten @click=${onClick}>${name}</gr-button>`
            )}
          </div>
        </div>
      </td>
    `;
  }

  private onAbandonClicked() {
    console.info('abandon clicked');
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-change-list-action-bar': GrChangeListActionBar;
  }
}
