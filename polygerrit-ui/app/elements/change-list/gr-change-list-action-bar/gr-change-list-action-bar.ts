/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {html, LitElement} from 'lit';
import {customElement} from 'lit/decorators';

@customElement('gr-change-list-action-bar')
export class GrChangeListActionBar extends LitElement {
  override render() {
    return html`<div>ACTION BAR</div>`;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-change-list-action-bar': GrChangeListActionBar;
  }
}
