/**
 * @license
 * Copyright (C) 2021 The Android Open Source Project
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
import '../../shared/gr-label-info/gr-label-info';
import {customElement, property} from 'lit/decorators';
import {AccountInfo, LabelInfo} from '../../../api/rest-api';
import {ParsedChangeInfo} from '../../../types/types';
import {css, html, LitElement} from 'lit';
import {HovercardMixin} from '../../../mixins/hovercard-mixin/hovercard-mixin';
import {fontStyles} from '../../../styles/gr-font-styles';

// This avoids JSC_DYNAMIC_EXTENDS_WITHOUT_JSDOC closure compiler error.
const base = HovercardMixin(LitElement);

@customElement('gr-trigger-vote-hovercard')
export class GrTriggerVoteHovercard extends base {
  @property()
  labelName?: string;

  @property({type: Object})
  labelInfo?: LabelInfo;

  @property({type: Object})
  change?: ParsedChangeInfo;

  @property({type: Object})
  account?: AccountInfo;

  @property({type: Boolean})
  mutable = false;

  static override get styles() {
    return [
      fontStyles,
      base.styles || [],
      css`
        #container {
          min-width: 356px;
          max-width: 356px;
          padding: var(--spacing-xl) 0 var(--spacing-m) 0;
        }
        .row {
          display: flex;
        }
        .title {
          color: var(--deemphasized-text-color);
          margin-right: var(--spacing-m);
        }
        div.section {
          margin: 0 var(--spacing-xl) var(--spacing-m) var(--spacing-xl);
          display: flex;
          align-items: flex-start;
        }
        div.sectionIcon {
          flex: 0 0 30px;
        }
        div.sectionIcon iron-icon {
          position: relative;
          width: 20px;
          height: 20px;
        }
      `,
    ];
  }

  override render() {
    return html` <div id="container" role="tooltip" tabindex="-1">
      <div class="section">
        <div class="sectionContent">
          <h3 class="name heading-3">
            <span>${this.labelName}</span>
          </h3>
        </div>
      </div>
      <div class="section">
        <div class="sectionIcon">
          <iron-icon class="small" icon="gr-icons:info-outline"></iron-icon>
        </div>
        <div class="sectionContent">
          <div class="row">
            <div class="title">Status</div>
            <div>
              <gr-label-info
                .change=${this.change}
                .account=${this.account}
                .mutable=${this.mutable}
                .label=${this.labelName}
                .labelInfo=${this.labelInfo}
                .showAllReviewers=${false}
              ></gr-label-info>
            </div>
          </div>
        </div>
      </div>
    </div>`;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-trigger-vote-hovercard': GrTriggerVoteHovercard;
  }
}
