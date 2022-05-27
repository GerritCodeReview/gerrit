/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../shared/gr-overlay/gr-overlay';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, html} from 'lit';
import {customElement, query} from 'lit/decorators';

declare global {
  interface HTMLElementTagNameMap {
    'gr-plugin-popup': GrPluginPopup;
  }
}

@customElement('gr-plugin-popup')
export class GrPluginPopup extends LitElement {
  @query('#overlay') protected overlay!: GrOverlay;

  static override get styles() {
    return [sharedStyles];
  }

  override render() {
    return html`<gr-overlay id="overlay" with-backdrop="">
      <slot></slot>
    </gr-overlay>`;
  }

  get opened() {
    return this.overlay.opened;
  }

  open() {
    return this.overlay.open();
  }

  close() {
    this.overlay.close();
  }
}
