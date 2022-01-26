/**
 * @license
 * Copyright (C) 2022 The Android Open Source Project
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

import '../../change/gr-submit-requirement-dashboard-hovercard/gr-submit-requirement-dashboard-hovercard';
import '../../shared/gr-change-status/gr-change-status';
import {LitElement, css, html} from 'lit';
import {customElement, property} from 'lit/decorators';
import {ChangeInfo, SubmitRequirementStatus} from '../../../api/rest-api';
import {submitRequirementsStyles} from '../../../styles/gr-submit-requirements-styles';
import {getRequirements, iconForStatus} from '../../../utils/label-util';
import {sharedStyles} from '../../../styles/shared-styles';

@customElement('gr-change-list-column-requirement')
export class GrChangeListColumnRequirement extends LitElement {
  @property({type: Object})
  change?: ChangeInfo;

  @property()
  labelName?: string;

  //TODO: property that will say that this doesn't work.

  static override get styles() {
    return [
      submitRequirementsStyles,
      sharedStyles,
      css`
        iron-icon {
          vertical-align: top;
        }
        .container.u-gray-background {
          background-color: var(--table-header-background-color);
          display: inline;
        }
      `,
    ];
  }

  override render() {
    return html`<div
      title="${this.computeTitle()}"
      class="container ${this.computeClass()}"
    >
      ${this.renderContent()}
    </div>`;
  }

  private renderContent() {
    if (!this.labelName) return;
    const requirements = this.getRequirement(this.labelName);
    if (requirements.length === 0) return;

    const icon = iconForStatus(
      requirements[0].status ?? SubmitRequirementStatus.ERROR
    );
    return html`<iron-icon
      class="${icon}"
      icon="gr-icons:${icon}"
    ></iron-icon>`;

    // TODO: if no render gray background
  }

  private computeTitle() {
    return 'hello';
  }

  private computeClass(): string {
    if (!this.labelName) return '';
    const requirements = this.getRequirement(this.labelName);
    if (requirements.length === 0) {
      return 'u-gray-background';
    }
    return '';
  }

  private getRequirement(labelName: string) {
    const requirements = getRequirements(this.change).filter(
      sr => sr.name === labelName
    );
    // TODO(milutin): Remove this after migration from legacy requirements.
    if (requirements.length > 1) {
      return requirements.filter(sr => !sr.is_legacy);
    } else {
      return requirements;
    }
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-change-list-column-requirement': GrChangeListColumnRequirement;
  }
}
