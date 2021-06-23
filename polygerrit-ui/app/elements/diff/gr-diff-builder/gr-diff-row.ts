/**
 * @license
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import {html} from 'lit-html';
import {css, customElement, LitElement, property} from 'lit-element';
import {LineNumber} from '../../../api/diff';

@customElement('gr-diff-row')
export class GrDiffRow extends LitElement {
  @property()
  leftText?: string;

  @property()
  rightText?: string;

  @property()
  leftNumber?: LineNumber;

  @property()
  rightNumber?: LineNumber;

  static get styles() {
    return [
      css`
        :host {
          display: contents;
        }
      `,
    ];
  }

  render() {
    return html`
      <tr>
        <td></td>
        <td>12</td>
        <td>asdf</td>
        <td>13</td>
        <td>qwer</td>
      </tr>
    `;
    // return html`
    //   <tr>
    //     <td></td>
    //     <td>${this.leftNumber}</td>
    //     <td>${this.leftText}</td>
    //     <td>${this.rightNumber}</td>
    //     <td>${this.rightText}</td>
    //   </tr>
    // `;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-diff-row': GrDiffRow;
  }
}
