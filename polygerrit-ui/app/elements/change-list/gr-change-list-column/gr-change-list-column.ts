/**
 * @license
 * Copyright (C) 2015 The Android Open Source Project
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

import {LitElement, css, html} from 'lit';
import {customElement, property} from 'lit/decorators';
import {ChangeInfo, SubmitRequirementStatus} from '../../../api/rest-api';
import {changeIsMerged} from '../../../utils/change-util';

@customElement('gr-change-list-column-requirements')
export class GrChangeListColumRequirements extends LitElement {
  @property({type: Object})
  change?: ChangeInfo;

  static override get styles() {
    return css`
      iron-icon {
        width: var(--line-height-normal, 20px);
        height: var(--line-height-normal, 20px);
      }
      iron-icon.check,
      .merged,
      .ready {
        color: var(--success-foreground);
      }
      iron-icon.close {
        color: var(--error-foreground);
      }
    `;
  }

  override render() {
    if (changeIsMerged(this.change)) {
      return html`<span class="merged"
        ><iron-icon class="check" icon="gr-icons:check" role="img"></iron-icon
        >Merged</span
      >`;
    }

    const submitRequirements = (this.change?.submit_requirements ?? []).filter(
      req => req.status !== SubmitRequirementStatus.NOT_APPLICABLE
    );
    if (!submitRequirements.length) return html`n/a`;
    const numOfRequirements = submitRequirements.length;
    const numOfSatisfiedRequirements = submitRequirements.filter(
      req => req.status === SubmitRequirementStatus.SATISFIED
    ).length;

    if (numOfSatisfiedRequirements === numOfRequirements) {
      return html`<span class="ready"
        ><iron-icon class="check" icon="gr-icons:check" role="img"></iron-icon
        >Ready</span
      >`;
    }
    return html`<span class="granted"
      ><iron-icon class="close" icon="gr-icons:close" role="img"></iron-icon
      >${numOfSatisfiedRequirements} of ${numOfRequirements} granted</span
    >`;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-change-list-column-requirements': GrChangeListColumRequirements;
  }
}
