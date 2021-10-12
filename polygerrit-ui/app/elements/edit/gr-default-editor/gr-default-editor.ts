/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

  static get styles() {
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
      .value="${this.fileContent}"
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
