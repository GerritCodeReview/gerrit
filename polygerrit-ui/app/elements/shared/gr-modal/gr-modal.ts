/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

// import { html, LitElement } from "lit";
import {html, LitElement} from 'lit';
import {customElement, query} from 'lit/decorators.js';
import {modalStyles} from '../../../styles/gr-modal-styles';

declare global {
  interface HTMLElementTagNameMap {
    'gr-modal': GrModal;
  }
}

@customElement('gr-modal')
export class GrModal extends LitElement {
  @query('#dialog')
  dialog?: HTMLDialogElement;

  static override get styles() {
    return [modalStyles];
  }

  constructor() {
    super();
    window.addEventListener('popstate', () => this.close());
  }

  close() {
    this.dialog?.close();
  }

  showModal() {
    this.dialog?.showModal();
  }

  override render() {
    return html`<dialog id="dialog" tabindex="-1"><slot></slot></dialog>`;
  }
}
