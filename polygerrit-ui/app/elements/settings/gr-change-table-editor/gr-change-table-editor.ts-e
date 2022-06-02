/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../shared/gr-button/gr-button';
import {ServerInfo} from '../../../types/common';
import {getAppContext} from '../../../services/app-context';
import {LitElement, css, html} from 'lit';
import {customElement, property} from 'lit/decorators';
import {sharedStyles} from '../../../styles/shared-styles';
import {formStyles} from '../../../styles/gr-form-styles';
import {PropertyValues} from 'lit';
import {fire} from '../../../utils/event-util';
import {ValueChangedEvent} from '../../../types/events';
import {ColumnNames} from '../../../constants/constants';

@customElement('gr-change-table-editor')
export class GrChangeTableEditor extends LitElement {
  @property({type: Array})
  displayedColumns: string[] = [];

  @property({type: Boolean})
  showNumber?: boolean;

  @property({type: Object})
  serverConfig?: ServerInfo;

  @property({type: Array})
  defaultColumns: string[] = [];

  private readonly flagsService = getAppContext().flagsService;

  static override styles = [
    sharedStyles,
    formStyles,
    css`
      #changeCols {
        width: auto;
      }
      #changeCols .visibleHeader {
        text-align: center;
      }
      .checkboxContainer {
        cursor: pointer;
        text-align: center;
      }
      .checkboxContainer input {
        cursor: pointer;
      }
      .checkboxContainer:hover {
        outline: 1px solid var(--border-color);
      }
    `,
  ];

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
            <td
              class="checkboxContainer"
              @click=${this.handleCheckboxContainerClick}
            >
              <input
                id="numberCheckbox"
                type="checkbox"
                name="number"
                @click=${this.handleNumberCheckboxClick}
                ?checked=${this.showNumber}
              />
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
      <td class="checkboxContainer" @click=${this.handleCheckboxContainerClick}>
        <input
          id=${column}
          type="checkbox"
          name=${column}
          @click=${this.handleTargetClick}
          ?checked=${!this.computeIsColumnHidden(column)}
        />
      </td>
    </tr>`;
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('serverConfig')) {
      this.configChanged();
    }
  }

  private configChanged() {
    this.defaultColumns = Object.values(ColumnNames).filter(column =>
      this.isColumnEnabled(column)
    );
    if (!this.displayedColumns) return;
    this.displayedColumns = this.displayedColumns.filter(column =>
      this.isColumnEnabled(column)
    );
  }

  /**
   * Is the column disabled by a server config or experiment?
   * private but used in test
   */
  isColumnEnabled(column: string) {
    if (!this.serverConfig?.change) return true;
    if (column === ColumnNames.COMMENTS)
      return this.flagsService.isEnabled('comments-column');
    if (column === ColumnNames.STATUS) return false;
    return true;
  }

  /**
   * Get the list of enabled column names from whichever checkboxes are
   * checked (excluding the number checkbox).
   * private but used in test
   */
  getDisplayedColumns() {
    if (this.shadowRoot === null) return [];
    return Array.from(
      this.shadowRoot.querySelectorAll<HTMLInputElement>(
        '.checkboxContainer input:not([name=number])'
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
   * Handle a click on a checkbox container and relay the click to the checkbox it
   * contains.
   */
  private handleCheckboxContainerClick(e: MouseEvent) {
    if (e.target === null) return;
    const checkbox = (e.target as HTMLElement).querySelector('input');
    if (!checkbox) {
      return;
    }
    checkbox.click();
  }

  /**
   * Handle a click on the number checkbox and update the showNumber property
   * accordingly.
   */
  private handleNumberCheckboxClick(e: MouseEvent) {
    this.showNumber = (e.target as HTMLInputElement).checked;
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
