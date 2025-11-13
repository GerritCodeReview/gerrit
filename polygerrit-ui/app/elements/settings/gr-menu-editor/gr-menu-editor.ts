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
import {classMap} from 'lit/directives/class-map.js';
import {menuPageStyles} from '../../../styles/gr-menu-page-styles';
import {userModelToken} from '../../../models/user/user-model';
import {resolve} from '../../../models/dependency';
import '@material/web/textfield/outlined-text-field';
import {materialStyles} from '../../../styles/gr-material-styles';
import '@material/web/checkbox/checkbox';

@customElement('gr-menu-editor')
export class GrMenuEditor extends LitElement {
  @state()
  menuItems: TopMenuItemInfo[] = [];

  @state()
  originalPrefs: PreferencesInfo = createDefaultPreferences();

  @state()
  newName = '';

  @state()
  newUrl = '';

  @state()
  newTarget = false;

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
        td.urlCell {
          overflow-wrap: anywhere;
        }
        .newUrlInput {
          min-width: 23em;
        }
        th {
          white-space: nowrap;
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
          <gr-button id="save" @click=${this.handleSave} ?disabled=${unchanged}
            >Save Changes</gr-button
          >
          <gr-button id="reset" link @click=${this.handleReset}
            >Reset</gr-button
          >
        </fieldset>
      </div>
    `;
  }

  private renderMenuItemRow(item: TopMenuItemInfo, index: number) {
    return html`
      <tr>
        <td>${item.name}</td>
        <td class="urlCell">${item.url}</td>
        <td>
          <md-checkbox
            disabled
            ?checked=${item.target === '_blank'}
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
            @click=${() => {
              this.menuItems.splice(index, 1);
              this.requestUpdate('menuItems');
            }}
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
            .value=${this.newName ?? ''}
            @input=${(e: InputEvent) => {
              const target = e.target as HTMLInputElement;
              this.newName = target.value;
            }}
            @keydown=${this.handleInputKeydown}
          >
          </md-outlined-text-field>
        </th>
        <th>
          <md-outlined-text-field
            class="newUrlInput showBlueFocusBorder"
            placeholder="New URL"
            .value=${this.newUrl ?? ''}
            @input=${(e: InputEvent) => {
              const target = e.target as HTMLInputElement;
              this.newUrl = target.value;
            }}
            @keydown=${this.handleInputKeydown}
          >
          </md-outlined-text-field>
        </th>
        <th>
          <md-checkbox
            id="lineWrappingInput"
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

  private handleSave() {
    this.getUserModel().updatePreferences({
      ...this.originalPrefs,
      my: this.menuItems,
    });
  }

  private handleReset() {
    this.menuItems = [...this.originalPrefs.my];
  }

  private swapItems(i: number, j: number) {
    const max = this.menuItems.length - 1;
    if (i < 0 || j < 0) return;
    if (i > max || j > max) return;
    const x = this.menuItems[i];
    this.menuItems[i] = this.menuItems[j];
    this.menuItems[j] = x;
    this.requestUpdate('menuItems');
  }

  // visible for testing
  handleAddButton() {
    if (this.newName.length === 0 || this.newUrl.length === 0) return;

    this.menuItems.push({
      name: this.newName,
      url: this.newUrl,
      target: this.newTarget ? '_blank' : undefined,
    });
    this.newName = '';
    this.newUrl = '';
    this.requestUpdate('menuItems');
  }

  private handleInputKeydown(e: KeyboardEvent) {
    if (e.key === 'Enter') {
      e.stopPropagation();
      this.handleAddButton();
    }
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-menu-editor': GrMenuEditor;
  }
}
