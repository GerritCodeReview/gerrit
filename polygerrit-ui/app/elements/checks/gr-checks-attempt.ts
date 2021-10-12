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
import {LitElement, css, html} from 'lit';
import {customElement, property} from 'lit/decorators';
import {CheckRun} from '../../services/checks/checks-model';
import {ordinal} from '../../utils/string-util';

@customElement('gr-checks-attempt')
class GrChecksAttempt extends LitElement {
  @property({attribute: false})
  run?: CheckRun;

  static get styles() {
    return [
      css`
        .attempt {
          display: inline-block;
          height: var(--line-height-normal);
          vertical-align: top;
          font-size: var(--font-size-small);
          position: relative;
        }
        .attempt .box,
        .attempt .angle {
          box-sizing: border-box;
          height: calc(var(--line-height-normal) - 2px);
          line-height: calc(var(--line-height-normal) - 2px);
          border-radius: 2px;
        }
        .attempt .box {
          margin-left: 2px;
          margin-bottom: 2px;
          border: 1px solid var(--deemphasized-text-color);
          padding: 0 var(--spacing-s);
        }
        .attempt .angle {
          position: absolute;
          top: 2px;
          /* The text in the .angle div just ensures the correct width. */
          color: transparent;
          border-left: 1px solid var(--deemphasized-text-color);
          border-bottom: 1px solid var(--deemphasized-text-color);
          /* 1px for the border of the .box div. */
          padding: 0 calc(var(--spacing-s) + 1px);
        }
      `,
    ];
  }

  override render() {
    if (!this.run) return undefined;
    if (this.run.isSingleAttempt) return undefined;
    if (!this.run.attempt) return undefined;
    const attempt = ordinal(this.run.attempt);

    return html`
      <span class="attempt">
        <div class="box">${attempt}</div>
        <div class="angle">${attempt}</div>
      </span>
    `;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-checks-attempt': GrChecksAttempt;
  }
}
