/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import '../../shared/gr-button/gr-button';
import {PreferencesInfo, TopMenuItemInfo} from '../../../types/common';
import {css, html, LitElement} from 'lit';
import {sharedStyles} from '../../../styles/shared-styles';
import {grFormStyles} from '../../../styles/gr-form-styles';
import {customElement, state} from 'lit/decorators.js';
import {subscribe} from '../../lit/subscription-controller';
import {deepEqual} from '../../../utils/deep-util';
import {createDefaultPreferences} from '../../../constants/constants';
import {fontStyles} from '../../../styles/gr-font-styles';
import {menuPageStyles} from '../../../styles/gr-menu-page-styles';
import {classMap} from 'lit/directives/class-map.js';
import {userModelToken} from '../../../models/user/user-model';
import {resolve} from '../../../models/dependency';
import '@material/web/textfield/outlined-text-field';
import {materialStyles} from '../../../styles/gr-material-styles';
import '@material/web/checkbox/checkbox';
import {when} from 'lit/directives/when.js';

@customElement('gr-menu-editor')
export class GrMenuEditor extends LitElement {
  @state() menuItems: TopMenuItemInfo[] = [];

  @state() originalPrefs: PreferencesInfo = createDefaultPreferences();

  @state() newName = '';

  @state() newUrl = '';

  @state() newTarget = false;

  @state() editingIndex: number | null = null;

  @state() editingField: 'name' | 'url' | null = null;

  @state() private editingBackup: TopMenuItemInfo | null = null;

  private readonly getUserModel = resolve(this, userModelToken);

  constructor() {
    super();
    subscribe(
      this,
      () => this.getUserModel().preferences$,
      prefs => {
        this.originalPrefs = prefs;
        this.menuItems = [...prefs.my];
      }
    );
  }

  static override get styles() {
    return [
      materialStyles,
      grFormStyles,
      sharedStyles,
      fontStyles,
      menuPageStyles,
      css`
        .buttonColumn {
          width: 2em;
        }
        .moveUpButton,
        .moveDownButton {
          width: 100%;
        }
        tbody tr:first-of-type td .moveUpButton,
        tbody tr:last-of-type td .moveDownButton {
          display: none;
        }
        .newUrlInput {
          min-width: 23em;
        }
        th {
          white-space: nowrap;
        }
        tfoot th:nth-child(1),
        tfoot th:nth-child(2),
        tfoot th:nth-child(3) {
          padding-right: 1em;
        }
        table {
          margin-bottom: 8px;
        }
        .editNameInput,
        .editUrlInput,
        #cancelBtn,
        #editBtn {
          vertical-align: middle;
        }
        .editRow {
          display: flex;
          align-items: center;
          gap: 4px;
        }
        .editNameInput,
        .editUrlInput {
          flex: 1;
        }
        td.nameCell .displayRow {
          min-width: 11em;
          width: auto;
        }
        td.nameCell,
        td.urlCell {
          padding-right: 1em;
        }
        .displayRow {
          display: flex;
          align-items: flex-start;
          gap: 4px;
        }
        .displayRow .text {
          flex: 1;
          overflow-wrap: anywhere;
        }
      `,
    ];
  }

  override render() {
    const unchanged = deepEqual(this.menuItems, this.originalPrefs.my);
    const classes = {
      'heading-2': true,
      edited: !unchanged,
    };
    return html`
      <div class="gr-form-styles">
        <h2 id="Menu" class=${classMap(classes)}>Menu</h2>
        <fieldset id="menu">
          <table>
            <thead>
              <tr>
                <th>Name</th>
                <th>URL</th>
                <th>New Tab</th>
                <th></th>
                <th></th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              ${this.menuItems.map((item, index) =>
                this.renderMenuItemRow(item, index)
              )}
            </tbody>
            <tfoot>
              ${this.renderFooterRow()}
            </tfoot>
          </table>
          <gr-button id="save" @click=${this.handleSave} ?disabled=${unchanged}>
            Save Changes
          </gr-button>
          <gr-button id="reset" link @click=${this.handleReset}>
            Reset
          </gr-button>
        </fieldset>
      </div>
    `;
  }

  private renderMenuItemRow(item: TopMenuItemInfo, index: number) {
    const isEditingName =
      this.editingIndex === index && this.editingField === 'name';
    const isEditingUrl =
      this.editingIndex === index && this.editingField === 'url';

    return html`
      <tr>
        <td class="nameCell">
          ${when(
            isEditingName,
            () => html`
              <div class="editRow">
                <md-outlined-text-field
                  class="editNameInput showBlueFocusBorder"
                  .value=${item.name}
                  @input=${(e: InputEvent) => this.handleNameChanged(e, index)}
                ></md-outlined-text-field>
                <gr-button id="cancelBtn" link @click=${this.cancelEdit}>
                  <gr-icon icon="cancel"></gr-icon>
                </gr-button>
              </div>
            `,
            () => html`
              <div class="displayRow">
                <span class="text">${item.name}</span>
                <gr-button
                  id="editBtn"
                  link
                  @click=${() => this.startEdit(index, 'name')}
                >
                  <gr-icon icon="edit" filled small></gr-icon>
                </gr-button>
              </div>
            `
          )}
        </td>

        <td class="urlCell">
          ${when(
            isEditingUrl,
            () => html`
              <div class="editRow">
                <md-outlined-text-field
                  class="editUrlInput showBlueFocusBorder"
                  .value=${item.url}
                  @input=${(e: InputEvent) => this.handleUrlChanged(e, index)}
                ></md-outlined-text-field>
                <gr-button id="cancelBtn" link @click=${this.cancelEdit}>
                  <gr-icon icon="cancel"></gr-icon>
                </gr-button>
              </div>
            `,
            () => html`
              <div class="displayRow">
                <span class="text">${item.url}</span>
                <gr-button
                  id="editBtn"
                  link
                  @click=${() => this.startEdit(index, 'url')}
                >
                  <gr-icon icon="edit" filled small></gr-icon>
                </gr-button>
              </div>
            `
          )}
        </td>
        <td>
          <md-checkbox
            ?checked=${item.target === '_blank'}
            @change=${(e: Event) => this.handleCheckboxChange(e, index)}
          ></md-checkbox>
        </td>
        <td class="buttonColumn">
          <gr-button
            link
            data-index=${index}
            @click=${() => this.swapItems(index, index - 1)}
            class="moveUpButton"
            >↑</gr-button
          >
        </td>
        <td class="buttonColumn">
          <gr-button
            link
            data-index=${index}
            @click=${() => this.swapItems(index, index + 1)}
            class="moveDownButton"
            >↓</gr-button
          >
        </td>
        <td>
          <gr-button
            link
            data-index=${index}
            @click=${() => this.deleteItem(index)}
            class="remove-button"
            >Delete</gr-button
          >
        </td>
      </tr>
    `;
  }

  private renderFooterRow() {
    return html`
      <tr>
        <th>
          <md-outlined-text-field
            class="showBlueFocusBorder"
            placeholder="New Title"
            .value=${this.newName}
            @input=${(e: InputEvent) =>
              (this.newName = (e.target as HTMLInputElement).value)}
            @keydown=${this.handleInputKeydown}
          ></md-outlined-text-field>
        </th>
        <th>
          <md-outlined-text-field
            class="newUrlInput showBlueFocusBorder"
            placeholder="New URL"
            .value=${this.newUrl}
            @input=${(e: InputEvent) =>
              (this.newUrl = (e.target as HTMLInputElement).value)}
            @keydown=${this.handleInputKeydown}
          ></md-outlined-text-field>
        </th>
        <th>
          <md-checkbox
            ?checked=${this.newTarget}
            @change=${() => (this.newTarget = !this.newTarget)}
          ></md-checkbox>
        </th>
        <th></th>
        <th></th>
        <th>
          <gr-button
            id="add"
            link
            ?disabled=${this.newName.length === 0 || this.newUrl.length === 0}
            @click=${this.handleAddButton}
            >Add</gr-button
          >
        </th>
      </tr>
    `;
  }

  private startEdit(index: number, field: 'name' | 'url') {
    this.editingIndex = null;
    this.editingField = null;
    this.editingBackup = null;

    this.editingBackup = structuredClone(this.menuItems[index]);
    this.editingIndex = index;
    this.editingField = field;
  }

  private cancelEdit() {
    if (this.editingIndex === null || !this.editingBackup) return;

    this.menuItems = this.menuItems.map((item, i) =>
      i === this.editingIndex ? this.editingBackup! : item
    );

    this.editingIndex = null;
    this.editingField = null;
    this.editingBackup = null;
  }

  private handleNameChanged(e: InputEvent, index: number) {
    const value = (e.target as HTMLInputElement).value;

    this.menuItems = this.menuItems.map((item, i) =>
      i === index ? {...item, name: value} : item
    );
  }

  private handleUrlChanged(e: InputEvent, index: number) {
    const value = (e.target as HTMLInputElement).value;

    this.menuItems = this.menuItems.map((item, i) =>
      i === index ? {...item, url: value} : item
    );
  }

  private swapItems(i: number, j: number) {
    if (i < 0 || j < 0) return;
    if (i >= this.menuItems.length || j >= this.menuItems.length) return;

    const updated = [...this.menuItems];
    [updated[i], updated[j]] = [updated[j], updated[i]];
    this.menuItems = updated;

    if (this.editingIndex === i) {
      this.editingIndex = j;
    } else if (this.editingIndex === j) {
      this.editingIndex = i;
    }
  }

  private deleteItem(index: number) {
    this.menuItems = this.menuItems.filter((_, i) => i !== index);

    this.editingIndex = null;
    this.editingField = null;
    this.editingBackup = null;
  }

  private handleInputKeydown(e: KeyboardEvent) {
    if (e.key === 'Enter') {
      e.stopPropagation();
      this.handleAddButton();
    }
  }

  private handleCheckboxChange(e: Event, index: number) {
    const checked = (e.target as HTMLInputElement).checked;

    this.menuItems = this.menuItems.map((item, i) =>
      i === index ? {...item, target: checked ? '_blank' : undefined} : item
    );
  }

  // visible for testing
  handleAddButton() {
    if (!this.newName || !this.newUrl) return;

    this.menuItems = [
      ...this.menuItems,
      {
        name: this.newName,
        url: this.newUrl,
        target: this.newTarget ? '_blank' : undefined,
      },
    ];

    this.newName = '';
    this.newUrl = '';
    this.newTarget = false;
  }

  private handleSave() {
    this.getUserModel().updatePreferences({
      ...this.originalPrefs,
      my: this.menuItems,
    });

    this.editingIndex = null;
    this.editingField = null;
    this.editingBackup = null;
  }

  private handleReset() {
    this.menuItems = [...this.originalPrefs.my];

    this.editingIndex = null;
    this.editingField = null;
    this.editingBackup = null;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-menu-editor': GrMenuEditor;
  }
}
