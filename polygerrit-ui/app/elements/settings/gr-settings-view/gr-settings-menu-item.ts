/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {pageNavStyles} from '../../../styles/gr-page-nav-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, html} from 'lit';
import {customElement, property} from 'lit/decorators';

declare global {
  interface HTMLElementTagNameMap {
    'gr-settings-menu-item': GrSettingsMenuItem;
  }
}

@customElement('gr-settings-menu-item')
export class GrSettingsMenuItem extends LitElement {
  @property({type: String})
  href?: string;

  @property({type: String})
  override title = '';

  static override get styles() {
    return [sharedStyles, pageNavStyles];
  }

  override render() {
    const href = this.href ?? '';
    return html` <div class="navStyles">
      <li><a href=${href}>${this.title}</a></li>
    </div>`;
  }
}
