/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../shared/gr-button/gr-button';
import {ServerInfo} from '../../../types/common';
import {css, html, LitElement} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {sharedStyles} from '../../../styles/shared-styles';
import {grFormStyles} from '../../../styles/gr-form-styles';
import {fire} from '../../../utils/event-util';
import {ValueChangedEvent} from '../../../types/events';
import {ColumnNames} from '../../../constants/constants';
import {subscribe} from '../../lit/subscription-controller';
import {resolve} from '../../../models/dependency';
import {configModelToken} from '../../../models/config/config-model';
import '@material/web/checkbox/checkbox';
import {materialStyles} from '../../../styles/gr-material-styles';
import {MdCheckbox} from '@material/web/checkbox/checkbox';

@customElement('gr-change-table-editor')
export class GrChangeTableEditor extends LitElement {
  @property({type: Array})
  displayedColumns: string[] = [];

  @property({type: Boolean})
  showNumber?: boolean;

  @property({type: Array})
  defaultColumns: string[] = Object.values(ColumnNames);

  @state()
  serverConfig?: ServerInfo;

  private readonly getConfigModel = resolve(this, configModelToken);

  static override get styles() {
    return [
      sharedStyles,
      grFormStyles,
      materialStyles,
      css`
        #changeCols {
          width: auto;
        }
        #changeCols .visibleHeader {
          text-align: center;
        }
        .checkboxContainer {
          text-align: center;
        }
      `,
    ];
  }

  constructor() {
    super();
    subscribe(
      this,
      () => this.getConfigModel().serverConfig$,
      config => {
        this.serverConfig = config;
      }
    );
  }

  override render() {
    return html`<div class="gr-form-styles">
      <table id="changeCols">
        <thead>
          <tr>
            <th class="nameHeader">Column</th>
            <th class="visibleHeader">Visible</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><label for="numberCheckbox">Number</label></td>
            <td class="checkboxContainer">
              <md-checkbox
                id="numberCheckbox"
                name="number"
                ?checked=${!!this.showNumber}
                @click=${this.handleNumberCheckboxClick}
              ></md-checkbox>
            </td>
          </tr>
          ${this.defaultColumns.map(column => this.renderRow(column))}
        </tbody>
      </table>
    </div>`;
  }

  renderRow(column: string) {
    return html`<tr>
      <td><label for=${column}>${column}</label></td>
      <td class="checkboxContainer">
        <md-checkbox
          id=${column}
          name=${column}
          ?checked=${!this.computeIsColumnHidden(column)}
          @click=${this.handleTargetClick}
        ></md-checkbox>
      </td>
    </tr>`;
  }

  /**
   * Get the list of enabled column names from whichever checkboxes are
   * checked (excluding the number checkbox).
   * private but used in test
   */
  getDisplayedColumns() {
    if (this.shadowRoot === null) return [];
    return Array.from(
      this.shadowRoot.querySelectorAll<MdCheckbox>(
        '.checkboxContainer md-checkbox:not([name=number])'
      )
    )
      .filter(checkbox => checkbox.checked)
      .map(checkbox => checkbox.name);
  }

  private computeIsColumnHidden(columnToCheck?: string) {
    if (!this.displayedColumns || !columnToCheck) {
      return false;
    }
    return !this.displayedColumns.includes(columnToCheck);
  }

  /**
   * Handle a click on the number checkbox and update the showNumber property
   * accordingly.
   */
  private handleNumberCheckboxClick(e: MouseEvent) {
    this.showNumber = (e.target as MdCheckbox).checked;
    fire(this, 'show-number-changed', {value: this.showNumber});
  }

  /**
   * Handle a click on a displayed column checkboxes (excluding number) and
   * update the displayedColumns property accordingly.
   */
  private handleTargetClick() {
    this.displayedColumns = this.getDisplayedColumns();
    fire(this, 'displayed-columns-changed', {value: this.displayedColumns});
  }
}

declare global {
  interface HTMLElementEventMap {
    'show-number-changed': ValueChangedEvent<boolean>;
    'displayed-columns-changed': ValueChangedEvent<string[]>;
  }
  interface HTMLElementTagNameMap {
    'gr-change-table-editor': GrChangeTableEditor;
  }
}
