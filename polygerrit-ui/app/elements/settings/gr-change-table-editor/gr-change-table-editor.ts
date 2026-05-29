/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import '../../shared/gr-button/gr-button';
import {PreferencesInput, ServerInfo} from '../../../types/common';
import {css, html, LitElement} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {sharedStyles} from '../../../styles/shared-styles';
import {grFormStyles} from '../../../styles/gr-form-styles';
import {ColumnNames} from '../../../constants/constants';
import {subscribe} from '../../lit/subscription-controller';
import {resolve} from '../../../models/dependency';
import {configModelToken} from '../../../models/config/config-model';
import '@material/web/checkbox/checkbox';
import {materialStyles} from '../../../styles/gr-material-styles';
import {MdCheckbox} from '@material/web/checkbox/checkbox';
import {
  changeTablePrefs,
  userModelToken,
} from '../../../models/user/user-model';
import {fontStyles} from '../../../styles/gr-font-styles';
import {menuPageStyles} from '../../../styles/gr-menu-page-styles';
import {classMap} from 'lit/directives/class-map.js';

@customElement('gr-change-table-editor')
export class GrChangeTableEditor extends LitElement {
  @property({type: Array})
  defaultColumns: string[] = Object.values(ColumnNames);

  @state()
  serverConfig?: ServerInfo;

  @state() prefs: PreferencesInput = {};

  // private but used in test
  @state() localChangeTableColumns: string[] = [];

  // private but used in test
  @state() showNumber?: boolean;

  @state() labelFilterInput: string = ''; // comma-separated

  private readonly getConfigModel = resolve(this, configModelToken);

  private readonly getUserModel = resolve(this, userModelToken);

  static override get styles() {
    return [
      materialStyles,
      grFormStyles,
      sharedStyles,
      fontStyles,
      menuPageStyles,
      css`
        #changeCols {
          width: auto;
        }
        #changeCols .visibleHeader {
          text-align: left;
        }
        .checkboxContainer {
          text-align: left;
        }
        .labelsFilterInput {
          width: 20em;
          display: block;
        }
        .labelsFilterCell {
          width: auto;
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
    subscribe(
      this,
      () => this.getUserModel().preferences$,
      prefs => {
        if (!prefs) {
          throw new Error('getPreferences returned undefined');
        }
        this.prefs = prefs;
        this.showNumber = !!prefs.legacycid_in_change_table;
        this.localChangeTableColumns = changeTablePrefs(prefs);
        this.labelFilterInput = prefs.label_filter ?? '';
      }
    );
  }

  override render() {
    const classes = {
      'heading-2': true,
      edited: this.hasUnsavedChanges(),
    };
    return html`
      <div class="gr-form-styles">
        <h2 id="ChangeTableColumns" class=${classMap(classes)}>
          Change Table Columns
        </h2>
        <fieldset id="changeTableColumns">
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
                    .checked=${!!this.showNumber}
                    @change=${this.handleNumberCheckboxClick}
                  ></md-checkbox>
                </td>
              </tr>

              ${this.defaultColumns.map(col => this.renderRow(col))}
              <tr>
                <td><label for="labelsFilter">Shown Labels</label></td>
                <td class="labelsFilterCell">
                  <md-outlined-text-field
                    id="labelsFilter"
                    class="showBlueFocusBorder labelsFilterInput"
                    placeholder="CR,V (leave empty to see all labels)"
                    .value=${this.labelFilterInput}
                    @input=${this.handleLabelsFilterInput}
                  ></md-outlined-text-field>
                </td>
              </tr>
            </tbody>
          </table>
          <gr-button
            id="saveChangeTable"
            @click=${this.handleSaveChangeTable}
            ?disabled=${!this.hasUnsavedChanges()}
            >Save Changes</gr-button
          >
        </fieldset>
      </div>
    `;
  }

  renderRow(column: string) {
    return html`
      <tr>
        <td><label for=${column}>${column}</label></td>
        <td class="checkboxContainer">
          <md-checkbox
            id=${column}
            name=${column}
            .checked=${this.localChangeTableColumns.includes(column)}
            @change=${this.handleTargetClick}
          ></md-checkbox>
        </td>
      </tr>
    `;
  }

  /**
   * Handle a click on the number checkbox and update the showNumber property
   * accordingly.
   */
  private handleNumberCheckboxClick(e: Event) {
    const checkbox = e.target as MdCheckbox;

    const oldValue = this.showNumber ?? false;
    const newValue = checkbox.checked;

    if (oldValue === newValue) return;

    this.showNumber = newValue;
  }

  /**
   * Handle input in the labels filter text field and update the labelFilterInput property
   * accordingly.
   */
  private handleLabelsFilterInput(e: Event) {
    this.labelFilterInput = (e.target as HTMLInputElement).value;
  }

  /**
   * Handle a click on a displayed column checkboxes (excluding number) and
   * update the localChangeTableColumns property accordingly.
   */
  private handleTargetClick(e: Event) {
    const checkbox = e.target as MdCheckbox;

    const column = checkbox.name;
    const checked = checkbox.checked;

    const exists = this.localChangeTableColumns.includes(column);

    if (checked === exists) return;

    let updated: string[];

    if (checked) {
      updated = [...this.localChangeTableColumns, column];
    } else {
      updated = this.localChangeTableColumns.filter(c => c !== column);
    }

    // Normalize order based on defaultColumns
    this.localChangeTableColumns = this.defaultColumns.filter(c =>
      updated.includes(c)
    );
  }

  // private but used in test
  async handleSaveChangeTable() {
    await this.getUserModel().updatePreferences({
      ...this.prefs,
      change_table: this.localChangeTableColumns,
      legacycid_in_change_table: this.showNumber,
      label_filter: this.labelFilterInput.trim(),
    });
  }

  private hasUnsavedChanges(): boolean {
    const prefsColumns = changeTablePrefs(this.prefs);
    const columnsChanged =
      prefsColumns.length !== this.localChangeTableColumns.length ||
      prefsColumns.some(c => !this.localChangeTableColumns.includes(c));
    const numberChanged =
      !!this.prefs.legacycid_in_change_table !== !!this.showNumber;
    const savedFilter = this.prefs.label_filter ?? '';
    const labelsChanged = savedFilter !== this.labelFilterInput.trim();
    return columnsChanged || numberChanged || labelsChanged;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-change-table-editor': GrChangeTableEditor;
  }
}
