/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, html} from 'lit';
import {customElement, query} from 'lit/decorators.js';
import {modalStyles} from '../../../styles/gr-modal-styles';

declare global {
  interface HTMLElementTagNameMap {
    'gr-plugin-popup': GrPluginPopup;
  }
}

@customElement('gr-plugin-popup')
export class GrPluginPopup extends LitElement {
  @query('#modal') protected modal!: HTMLDialogElement;

  static override get styles() {
    return [sharedStyles, modalStyles];
  }

  override render() {
    return html`<dialog id="modal">
      <slot></slot>
    </dialog>`;
  }

  get opened() {
    return this.modal.hasAttribute('open');
  }

  open() {
    this.modal.showModal();
  }

  close() {
    this.modal.close();
  }
}
