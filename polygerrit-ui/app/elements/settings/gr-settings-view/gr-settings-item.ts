/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {LitElement, css, html} from 'lit';
import {customElement, property} from 'lit/decorators';
import {formStyles} from '../../../styles/gr-form-styles';

declare global {
  interface HTMLElementTagNameMap {
    'gr-settings-item': GrSettingsItem;
  }
}

@customElement('gr-settings-item')
export class GrSettingsItem extends LitElement {
  @property({type: String})
  anchor?: string;

  @property({type: String})
  override title = '';

  static override get styles() {
    return [
      formStyles,
      css`
        :host {
          display: block;
          margin-bottom: var(--spacing-xxl);
        }
      `,
    ];
  }

  override render() {
    const anchor = this.anchor ?? '';
    return html`<h2 id=${anchor} class="heading-2">${this.title}</h2>`;
  }
}
