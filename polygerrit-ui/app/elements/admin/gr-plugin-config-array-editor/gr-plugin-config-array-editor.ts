/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../shared/gr-button/gr-button';
import {
  ArrayPluginOption,
  PluginConfigOptionsChangedEventDetail,
} from '../gr-repo-plugin-config/gr-repo-plugin-config-types';
import {grFormStyles} from '../../../styles/gr-form-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {css, html, LitElement} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {fireNoBubbleNoCompose} from '../../../utils/event-util';
import '@material/web/textfield/outlined-text-field';
import {materialStyles} from '../../../styles/gr-material-styles';

declare global {
  interface HTMLElementTagNameMap {
    'gr-plugin-config-array-editor': GrPluginConfigArrayEditor;
  }
  interface HTMLElementEventMap {
    'plugin-config-option-changed': CustomEvent<PluginConfigOptionsChangedEventDetail>;
  }
}

@customElement('gr-plugin-config-array-editor')
export class GrPluginConfigArrayEditor extends LitElement {
  // private but used in test
  @state() newValue = '';

  // This property is never null, since this component in only about operations
  // on pluginOption.
  @property({type: Object})
  pluginOption!: ArrayPluginOption;

  @property({type: Boolean, reflect: true})
  disabled = false;

  static override get styles() {
    return [
      materialStyles,
      sharedStyles,
      grFormStyles,
      css`
        .wrapper {
          width: 30em;
        }
        .existingItems {
          background: var(--table-header-background-color);
          border: 1px solid var(--border-color);
          border-radius: var(--border-radius);
        }
        gr-button {
          float: right;
          margin-left: var(--spacing-m);
          width: 4.5em;
        }
        .row {
          align-items: center;
          display: flex;
          justify-content: space-between;
          padding: var(--spacing-m) 0;
          width: 100%;
        }
        .existingItems .row {
          padding: var(--spacing-m);
        }
        .existingItems .row:not(:first-of-type) {
          border-top: 1px solid var(--border-color);
        }
        input {
          flex-grow: 1;
        }
        .hide {
          display: none;
        }
        .placeholder {
          color: var(--deemphasized-text-color);
          padding-top: var(--spacing-m);
        }
        md-outlined-text-field {
          flex-grow: 1;
        }
      `,
    ];
  }

  override render() {
    return html`
      <div class="wrapper gr-form-styles">
        ${this.renderPluginOptions()}
        <div class="row ${this.disabled ? 'hide' : ''}">
          <md-outlined-text-field
            id="input"
            class="showBlueFocusBorder"
            .value=${this.newValue ?? ''}
            ?disabled=${this.disabled}
            @input=${(e: InputEvent) => {
              const target = e.target as HTMLInputElement;
              this.newValue = target.value;
            }}
            @keydown=${this.handleInputKeydown}
          >
          </md-outlined-text-field>
          <gr-button
            id="addButton"
            ?disabled=${!this.newValue.length}
            link
            @click=${this.handleAddTap}
            >Add</gr-button
          >
        </div>
      </div>
    `;
  }

  private renderPluginOptions() {
    if (!this.pluginOption?.info?.values?.length) {
      return html`<div class="row placeholder">None configured.</div>`;
    }

    return html`
      <div class="existingItems">
        ${this.pluginOption.info.values.map(item =>
          this.renderPluginOptionValue(item)
        )}
      </div>
    `;
  }

  private renderPluginOptionValue(item: string) {
    return html`
      <div class="row">
        <span>${item}</span>
        <gr-button
          link
          ?disabled=${this.disabled}
          @click=${() => this.handleDelete(item)}
          >Delete</gr-button
        >
      </div>
    `;
  }

  private handleAddTap(e: MouseEvent) {
    e.preventDefault();
    this.handleAdd();
  }

  private handleInputKeydown(e: KeyboardEvent) {
    if (e.key === 'Enter') {
      e.preventDefault();
      this.handleAdd();
    }
  }

  private handleAdd() {
    if (!this.newValue.length) {
      return;
    }
    this.dispatchChanged(this.pluginOption.info.values.concat([this.newValue]));
    this.newValue = '';
  }

  private handleDelete(value: string) {
    this.dispatchChanged(
      this.pluginOption.info.values.filter(str => str !== value)
    );
  }

  // private but used in test
  dispatchChanged(values: string[]) {
    const {_key, info} = this.pluginOption;
    const detail: PluginConfigOptionsChangedEventDetail = {
      _key,
      info: {...info, values},
      notifyPath: `${_key}.values`,
    };
    fireNoBubbleNoCompose(this, 'plugin-config-option-changed', detail);
  }
}
