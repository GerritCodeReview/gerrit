/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, css, html} from 'lit';
import {customElement, property} from 'lit/decorators';

declare global {
  interface HTMLElementTagNameMap {
    'gr-default-editor': GrDefaultEditor;
  }
}

@customElement('gr-default-editor')
export class GrDefaultEditor extends LitElement {
  /**
   * Fired when the content of the editor changes.
   *
   * @event content-change
   */

  @property({type: String})
  fileContent = '';

  static override get styles() {
    return [
      sharedStyles,
      css`
        textarea {
          border: none;
          box-sizing: border-box;
          font-family: var(--monospace-font-family);
          font-size: var(--font-size-code);
          /* usually 16px = 12px + 4px */
          line-height: calc(var(--font-size-code) + var(--spacing-s));
          min-height: 60vh;
          resize: none;
          white-space: pre;
          width: 100%;
        }
        textarea:focus {
          outline: none;
        }
      `,
    ];
  }

  override render() {
    return html` <textarea
      id="textarea"
      .value=${this.fileContent}
      @input=${this._handleTextareaInput}
    ></textarea>`;
  }

  _handleTextareaInput(e: Event) {
    this.dispatchEvent(
      new CustomEvent('content-change', {
        detail: {value: (e.target as HTMLTextAreaElement).value},
        bubbles: true,
        composed: true,
      })
    );
  }
}
