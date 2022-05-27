/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import '@polymer/iron-input/iron-input';
import '@polymer/paper-toggle-button/paper-toggle-button';
import '../../shared/gr-button/gr-button';
import {
  PluginConfigOptionsChangedEventDetail,
  ArrayPluginOption,
} from '../gr-repo-plugin-config/gr-repo-plugin-config-types';
import {formStyles} from '../../../styles/gr-form-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, html, css} from 'lit';
import {customElement, property, state} from 'lit/decorators';
import {BindValueChangeEvent} from '../../../types/events';

declare global {
  interface HTMLElementTagNameMap {
    'gr-plugin-config-array-editor': GrPluginConfigArrayEditor;
  }
}

@customElement('gr-plugin-config-array-editor')
export class GrPluginConfigArrayEditor extends LitElement {
  /**
   * Fired when the plugin config option changes.
   *
   * @event plugin-config-option-changed
   */

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
      sharedStyles,
      formStyles,
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
      `,
    ];
  }

  override render() {
    return html`
      <div class="wrapper gr-form-styles">
        ${this.renderPluginOptions()}
        <div class="row ${this.disabled ? 'hide' : ''}">
          <iron-input
            .bindValue=${this.newValue}
            @bind-value-changed=${this.handleBindValueChangedNewValue}
          >
            <input
              id="input"
              @keydown=${this.handleInputKeydown}
              ?disabled=${this.disabled}
            />
          </iron-input>
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
    // Enter.
    if (e.keyCode === 13) {
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
    this.dispatchEvent(
      new CustomEvent('plugin-config-option-changed', {detail})
    );
  }

  private handleBindValueChangedNewValue(e: BindValueChangeEvent) {
    this.newValue = e.detail.value;
  }
}
