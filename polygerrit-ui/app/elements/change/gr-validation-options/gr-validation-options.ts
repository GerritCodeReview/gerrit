/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {css, html, LitElement} from 'lit';
import '@material/web/checkbox/checkbox';
import {materialStyles} from '../../../styles/gr-material-styles';
import {customElement, property} from 'lit/decorators.js';
import {
  ValidationOptionInfo,
  ValidationOptionsInfo,
} from '../../../api/rest-api';
import {repeat} from 'lit/directives/repeat.js';
import {capitalizeFirstLetter} from '../../../utils/string-util';

@customElement('gr-validation-options')
export class GrValidationOptions extends LitElement {
  @property({type: Object}) validationOptions?: ValidationOptionsInfo;

  private isOptionSelected: Map<string, boolean> = new Map();

  static override get styles() {
    return [
      materialStyles,
      css`
        :host {
          display: block;
        }
        div.checkbox-container {
          display: flex;
          align-items: center;
          gap: var(--spacing-m);
          margin-top: var(--spacing-l);
        }
        label {
          cursor: pointer;
        }
      `,
    ];
  }

  init() {
    this.isOptionSelected = new Map();
  }

  getSelectedOptions(): ValidationOptionInfo[] {
    return (this.validationOptions?.validation_options ?? []).filter(
      validationOption => this.isOptionSelected.get(validationOption.name)
    );
  }

  override render() {
    if (!this.validationOptions) return;
    return html`${repeat(
      this.validationOptions.validation_options,
      option => option.name,
      option => this.renderValidationOption(option)
    )}`;
  }

  private renderValidationOption(option: ValidationOptionInfo) {
    return html`
      <div class="checkbox-container">
        <md-checkbox
          class="selectionLabel"
          id=${option.name}
          ?checked=${!!this.isOptionSelected.get(option.name)}
          @click=${() => this.toggleCheckbox(option)}
        ></md-checkbox>
        <label for=${option.name}
          >${capitalizeFirstLetter(option.description)}</label
        >
      </div>
    `;
  }

  private toggleCheckbox(option: ValidationOptionInfo) {
    this.isOptionSelected.set(
      option.name,
      !this.isOptionSelected.get(option.name)
    );
  }
}
