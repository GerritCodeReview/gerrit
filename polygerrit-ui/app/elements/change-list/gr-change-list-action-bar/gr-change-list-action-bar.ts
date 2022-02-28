/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {css, html, LitElement} from 'lit';
import {customElement} from 'lit/decorators';
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
        justify-content: flex-end;
      }
    `;
  }

  private actionButtons: ActionButton[] = [
    {name: 'abandon', onClick: () => this.onAbandonClicked()},
  ];

  override render() {
    return html`
      <!--
        500 chosen to be more than the actual number of columns but less than
        1000 where the browser apparently decides it is an error and reverts
        back to colspan="1"
      -->
      <td colspan="500">
        <div class="container">
          ${this.actionButtons.map(
            ({name, onClick}) =>
              html`<gr-button @click=${onClick}>${name}</gr-button>`
          )}
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
