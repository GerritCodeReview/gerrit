/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {LitElement, html, css} from 'lit';
import {customElement, property} from 'lit/decorators.js';
import {
  ValidationOptionsInfo,
  ValidationOptionInfo,
} from '../../../api/rest-api';
import {repeat} from 'lit/directives/repeat.js';

@customElement('gr-validation-options')
export class GrValidationOptions extends LitElement {
  @property({type: Object}) validationOptions?: ValidationOptionsInfo;

  private isOptionSelected: Map<string, boolean> = new Map();

  static override get styles() {
    return [
      css`
        .selectionLabel {
          padding: 10px;
          margin: -10px;
          display: block;
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
    return html` <div class="validationOptionContainer">
      <label class="selectionLabel">
        <input
          type="checkbox"
          .checked=${!!this.isOptionSelected.get(option.name)}
          @click=${() => this.toggleCheckbox(option)}
        />
        ${option.description}
      </label>
      <div></div>
    </div>`;
  }

  private toggleCheckbox(option: ValidationOptionInfo) {
    this.isOptionSelected.set(
      option.name,
      !this.isOptionSelected.get(option.name)
    );
  }
}
