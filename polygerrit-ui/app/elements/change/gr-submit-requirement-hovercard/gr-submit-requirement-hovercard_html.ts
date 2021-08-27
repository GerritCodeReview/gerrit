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
import {html} from '@polymer/polymer/lib/utils/html-tag';

export const htmlTemplate = html`
  <style include="gr-hovercard-shared-style">
    #container {
      min-width: 356px;
      max-width: 356px;
      padding: var(--spacing-xl) 0 var(--spacing-m) 0;
    }
    section.label {
      display: table-row;
    }
    .label-title {
      min-width: 10em;
      padding: var(--spacing-s) 0 0 0;
    }
    .label-value {
      padding: var(--spacing-s) 0 0 0;
    }
    .label-title,
    .label-value {
      display: table-cell;
      vertical-align: top;
    }
    .row {
      display: flex;
      margin-top: var(--spacing-s);
    }
    .title {
      color: var(--deemphasized-text-color);
      margin-right: var(--spacing-m);
    }
    div.section {
      margin: 0 var(--spacing-xl) var(--spacing-m) var(--spacing-xl);
      display: flex;
    }
    div.sectionIcon iron-icon {
      position: relative;
      top: 2px;
      width: 20px;
      height: 20px;
    }
    iron-icon.check {
      color: var(--success-foreground);
    }
    iron-icon.close {
      color: var(--warning-foreground);
    }
  </style>
  <div id="container" role="tooltip" tabindex="-1">
    <div class="section">
      <div class="sectionIcon">
        <iron-icon
          class$="[[computeIcon(requirement.status)]]"
          icon="gr-icons:[[computeIcon(requirement.status)]]"
        ></iron-icon>
      </div>
      <div class="sectionContent">
        <h3 class="name heading-3">
          <span>[[requirement.name]]</span>
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
          <div>[[requirement.status]]</div>
        </div>
      </div>
    </div>
    <div class="section">
      <template is="dom-repeat" items="[[_labels]]">
        <section class="label">
          <div class="label-title">
            <gr-limited-text
              class="name"
              limit="25"
              text="[[item.labelName]]"
            ></gr-limited-text>
          </div>
          <div class="label-value">
            <gr-label-info
              change="{{change}}"
              account="[[account]]"
              mutable="[[mutable]]"
              label="[[item.labelName]]"
              label-info="[[item.labelInfo]]"
            ></gr-label-info>
          </div>
        </section>
      </template>
    </div>
  </div>
`;
