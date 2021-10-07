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
  <style include="gr-font-styles">
    /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
  </style>
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
      padding-top: var(--spacing-s);
    }
    .label-value {
      padding-top: var(--spacing-s);
    }
    .label-title,
    .label-value {
      display: table-cell;
      vertical-align: top;
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
      align-items: center;
    }
    div.sectionIcon {
      flex: 0 0 30px;
    }
    div.sectionIcon iron-icon {
      position: relative;
      width: 20px;
      height: 20px;
    }
    .condition {
      background-color: var(--gray-background);
      padding: var(--spacing-m);
      flex-grow: 1;
    }
    .expression {
      color: var(--gray-foreground);
    }
    iron-icon.check {
      color: var(--success-foreground);
    }
    iron-icon.close {
      color: var(--warning-foreground);
    }
    .showConditions iron-icon {
      color: inherit;
    }
    div.showConditions {
      border-top: 1px solid var(--border-color);
      margin-top: var(--spacing-m);
      padding: var(--spacing-m) var(--spacing-xl) 0;
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
    <template is="dom-if" if="[[!expanded]]">
      <div class="showConditions">
        <gr-button
          link=""
          class="showConditions"
          on-click="_handleShowConditions"
        >
          View condition
          <iron-icon icon="gr-icons:expand-more"></iron-icon
        ></gr-button>
      </div>
    </template>
    <template is="dom-if" if="[[expanded]]">
      <div class="section">
        <div class="sectionIcon">
          <iron-icon icon="gr-icons:description"></iron-icon>
        </div>
        <div class="sectionContent">[[requirement.description]]</div>
      </div>
      <div class="section">
        <div class="sectionIcon"></div>
        <div class="sectionContent condition">
          Blocking condition:<br />
          <span class="expression">
            [[renderCondition(requirement.submittability_expression_result)]]
          </span>
        </div>
      </div>
      <template
        is="dom-if"
        if="[[requirement.applicability_expression_result]]"
      >
        <div class="section">
          <div class="sectionIcon"></div>
          <div class="sectionContent condition">
            Application condition:<br />
            <span class="expression">
              [[renderCondition(requirement.applicability_expression_result)]]
            </span>
          </div>
        </div>
      </template>
      <template is="dom-if" if="[[requirement.override_expression_result]]">
        <div class="section">
          <div class="sectionIcon"></div>
          <div class="sectionContent condition">
            Override condition:<br />
            <span class="expression">
              [[renderCondition(requirement.override_expression_result)]]
            </span>
          </div>
        </div>
      </template>
    </template>
  </div>
`;
