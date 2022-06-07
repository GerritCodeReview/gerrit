/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '@polymer/iron-input/iron-input';
import '../../shared/gr-button/gr-button';
import {PreferencesInfo, TopMenuItemInfo} from '../../../types/common';
import {css, html, LitElement} from 'lit';
import {sharedStyles} from '../../../styles/shared-styles';
import {formStyles} from '../../../styles/gr-form-styles';
import {state, customElement} from 'lit/decorators';
import {BindValueChangeEvent} from '../../../types/events';
import {subscribe} from '../../lit/subscription-controller';
import {getAppContext} from '../../../services/app-context';
import {deepEqual} from '../../../utils/deep-util';
import {createDefaultPreferences} from '../../../constants/constants';
import {fontStyles} from '../../../styles/gr-font-styles';
import {classMap} from 'lit/directives/class-map';
import {menuPageStyles} from '../../../styles/gr-menu-page-styles';

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

  private readonly userModel = getAppContext().userModel;

  constructor() {
    super();
    subscribe(
      this,
      () => this.userModel.preferences$,
      prefs => {
        this.originalPrefs = prefs;
        this.menuItems = [...prefs.my];
      }
    );
  }

  static override styles = [
    formStyles,
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
        word-break: break-word;
      }
      .newUrlInput {
        min-width: 23em;
      }
    `,
  ];

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
            >Save changes</gr-button
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
          <iron-input
            .bindValue=${this.newName}
            @bind-value-changed=${(e: BindValueChangeEvent) => {
              this.newName = e.detail.value ?? '';
            }}
          >
            <input
              is="iron-input"
              placeholder="New Title"
              @keydown=${this.handleInputKeydown}
            />
          </iron-input>
        </th>
        <th>
          <iron-input
            .bindValue=${this.newUrl}
            @bind-value-changed=${(e: BindValueChangeEvent) => {
              this.newUrl = e.detail.value ?? '';
            }}
          >
            <input
              class="newUrlInput"
              placeholder="New URL"
              @keydown=${this.handleInputKeydown}
            />
          </iron-input>
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
    this.userModel.updatePreferences({
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
      target: '_blank',
    });
    this.newName = '';
    this.newUrl = '';
    this.requestUpdate('menuItems');
  }

  private handleInputKeydown(e: KeyboardEvent) {
    if (e.keyCode === 13) {
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
