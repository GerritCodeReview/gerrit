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

import '../../change/gr-submit-requirement-dashboard-hovercard/gr-submit-requirement-dashboard-hovercard';
import '../../shared/gr-change-status/gr-change-status';
import {LitElement, css, html} from 'lit';
import {customElement, property} from 'lit/decorators';
import {ChangeInfo, SubmitRequirementStatus} from '../../../api/rest-api';
import {submitRequirementsStyles} from '../../../styles/gr-submit-requirements-styles';
import {getRequirements, iconForStatus} from '../../../utils/label-util';

@customElement('gr-change-list-column-requirement')
export class GrChangeListColumnRequirement extends LitElement {
  @property({type: Object})
  change?: ChangeInfo;

  @property()
  labelName?: string;

  static override get styles() {
    return [submitRequirementsStyles, css``];
  }

  override render() {
    return html`<div
      title="${this.computeTitle()}"
      class="${this.computeClass()}"
    >
      ${this.renderContent()}
    </div>`;
  }

  private renderContent() {
    if (!this.labelName) return;
    const requirements = this.getRequirement(this.labelName);
    const icon = iconForStatus(
      requirements?.[0].status ?? SubmitRequirementStatus.ERROR
    );
    return html`<iron-icon
      class="${icon}"
      icon="gr-icons:${icon}"
    ></iron-icon>`;
  }

  private computeTitle() {
    return 'hello';
  }

  private computeClass() {
    if (!this.labelName) return;
    const classes = ['cell', 'label'];
    const requirements = this.getRequirement(this.labelName);
    if (requirements.length === 1) {
      classes.push('requirement');
    }
    // Do not add label category classes.
    return classes.sort().join(' ');
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
