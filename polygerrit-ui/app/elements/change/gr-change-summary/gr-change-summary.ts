/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
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
import {css, customElement} from 'lit-element';
import {GrLitElement} from '../../lit/gr-lit-element';
import {sharedStyles} from '../../../styles/shared-styles';

@customElement('gr-change-summary')
export class GrChangeSummary extends GrLitElement {
  static get styles() {
    return [
      sharedStyles,
      css`
        :host {
          display: block;
        }
      `,
    ];
  }

  render() {
    return html`
      <div>
        <table>
          <tr>
            <td>Checks</td>
            <td></td>
          </tr>
          <tr>
            <td>Comments</td>
            <td></td>
          </tr>
          <tr>
            <td>Findings</td>
            <td></td>
          </tr>
        </table>
      </div>
    `;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-change-summary': GrChangeSummary;
  }
}
