/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
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
  <style include="shared-styles">
    :host {
      display: table;
      width: 100%;
    }
    .status {
      color: #ffa62f;
      display: inline-block;
      text-align: center;
      font-family: var(--monospace-font-family);
      font-size: var(--font-size-mono);
      line-height: var(--line-height-mono);
    }
    .approved.status {
      color: var(--positive-green-text-color);
    }
    .rejected.status {
      color: var(--negative-red-text-color);
    }
    iron-icon {
      color: inherit;
    }
    .status iron-icon {
      vertical-align: top;
    }
    gr-endpoint-decorator.submit-requirement-endpoints,
    section {
      display: table-row;
    }
    .show-hide {
      float: right;
    }
    .title {
      min-width: 10em;
      padding: var(--spacing-s) var(--spacing-m) 0
        var(--requirements-horizontal-padding);
    }
    .value {
      padding: var(--spacing-s) 0 0 0;
    }
    .title,
    .value {
      display: table-cell;
      vertical-align: top;
    }
    .hidden {
      display: none;
    }
    .showHide {
      cursor: pointer;
    }
    .showHide .title {
      padding-bottom: var(--spacing-m);
      padding-top: var(--spacing-l);
    }
    .showHide .value {
      padding-top: 0;
      vertical-align: middle;
    }
    .showHide iron-icon {
      color: var(--deemphasized-text-color);
      float: right;
    }
    .spacer {
      height: var(--spacing-m);
    }
    gr-endpoint-param {
      display: none;
    }
  </style>
  <template is="dom-repeat" items="[[_requirements]]">
    <gr-endpoint-decorator
      class="submit-requirement-endpoints"
      name$="[[_computeSubmitRequirementEndpoint(item)]]"
    >
      <gr-endpoint-param name="change" value="[[change]]"></gr-endpoint-param>
      <gr-endpoint-param name="requirement" value="[[item]]">
      </gr-endpoint-param>
      <div class="title requirement">
        <span class$="status [[item.style]]">
          <iron-icon
            class="icon"
            icon="[[_computeRequirementIcon(item.satisfied)]]"
          ></iron-icon>
        </span>
        <gr-limited-text
          class="name"
          limit="40"
          tooltip="[[item.tooltip]]"
          text="[[item.fallback_text]]"
        ></gr-limited-text>
      </div>
      <div class="value">
        <gr-endpoint-slot name="value"></gr-endpoint-slot>
      </div>
    </gr-endpoint-decorator>
  </template>
  <template is="dom-repeat" items="[[_requiredLabels]]">
    <section>
      <div class="title">
        <span class$="status [[item.style]]">
          <iron-icon class="icon" icon="[[item.icon]]"></iron-icon>
        </span>
        <gr-limited-text
          class="name"
          limit="40"
          text="[[item.label]]"
        ></gr-limited-text>
      </div>
      <div class="value">
        <gr-label-info
          change="{{change}}"
          account="[[account]]"
          mutable="[[mutable]]"
          label="[[item.label]]"
          label-info="[[item.labelInfo]]"
        ></gr-label-info>
      </div>
    </section>
  </template>
  <section class="spacer"></section>
  <section
    class$="spacer [[_computeShowOptional(_optionalLabels.*)]]"
  ></section>
  <section
    show-bottom-border$="[[_showOptionalLabels]]"
    on-click="_handleShowHide"
    class$="showHide [[_computeShowOptional(_optionalLabels.*)]]"
  >
    <div class="title">Other labels</div>
    <div class="value">
      <iron-icon
        id="showHide"
        icon="[[_computeShowHideIcon(_showOptionalLabels)]]"
      >
      </iron-icon>
    </div>
  </section>
  <template is="dom-repeat" items="[[_optionalLabels]]">
    <section class$="optional [[_computeSectionClass(_showOptionalLabels)]]">
      <div class="title">
        <span class$="status [[item.style]]">
          <template is="dom-if" if="[[item.icon]]">
            <iron-icon class="icon" icon="[[item.icon]]"></iron-icon>
          </template>
          <template is="dom-if" if="[[!item.icon]]">
            <span>[[_computeLabelValue(item.labelInfo.value)]]</span>
          </template>
        </span>
        <gr-limited-text
          class="name"
          limit="40"
          text="[[item.label]]"
        ></gr-limited-text>
      </div>
      <div class="value">
        <gr-label-info
          change="{{change}}"
          account="[[account]]"
          mutable="[[mutable]]"
          label="[[item.label]]"
          label-info="[[item.labelInfo]]"
        ></gr-label-info>
      </div>
    </section>
  </template>
  <section
    class$="spacer [[_computeShowOptional(_optionalLabels.*)]] [[_computeSectionClass(_showOptionalLabels)]]"
  ></section>
`;
