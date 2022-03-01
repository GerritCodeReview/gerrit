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
      .actionButtons {
        display: inline-flex;
        justify-content: flex-end;
      }
    `;
  }

  @state()
  private numSelected = 0;

  private readonly getBulkActionsModel = resolve(this, bulkActionsModelToken);

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
      <!--
        500 chosen to be more than the actual number of columns but less than
        1000 where the browser apparently decides it is an error and reverts
        back to colspan="1"
      -->
      <td colspan="500">
        ${this.numSelected ? html`<span>${numSelectedLabel}</span>` : nothing}
        <div class="actionButtons">
          <gr-button>submit</gr-button>
          <gr-button>create topic</gr-button>
          <gr-button>add to topic</gr-button>
          <gr-button>add changes</gr-button>
          <gr-button>remove changes</gr-button>
          <gr-button>abandon</gr-button>
          <gr-button>add reviewer/cc</gr-button>
          <gr-button>vote</gr-button>
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
