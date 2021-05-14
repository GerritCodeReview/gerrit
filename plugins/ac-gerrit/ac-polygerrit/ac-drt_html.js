/**
 * @license
 * Copyright (C) 2021 AudioCodes Ltd.
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

export const htmlTemplate = Polymer.html`
<style include="shared-styles">
  #dialog {
    min-width: 40em;
  }

  p {
    margin-bottom: 1em;
  }

  .chapter-border {
    border-bottom: 1px solid #ddd;
    padding-bottom: 10px;
  }

  @media screen and (max-width: 50em) {
    #dialog {
      min-width: inherit;
      width: 100%;
    }
  }
</style>
<gr-dialog id="drt" confirm-label$="[[Start]]" confirm-on-enter on-cancel="_handleCancelTap" on-confirm="_handleConfirmTap" disabled$="[[disabled]]">
  <div class="header" slot="header">[[header]]</div>
  <div class="main" slot="main">
      <img src="[[waitImage]]" width="70" height="70" hidden$="[[hiddenImage]]">
      <div class="com-google-gerrit-client-change-Resources-Style-popupContent" hidden$="[[hidenSanity]]">
        <div style="border-style: groove;">
        <p style="padding-top: 10px; margin-bottom: -10px; font-weight: bold; color: red">Choose the Sanity you want:</p><br>
          <template is="dom-repeat" items="[[sanityCheck]]" as="sanity">
            <input type="checkbox" name="sanity" checked$="{{sanity.value}}" disabled$="[[sanity.disabled]]" sanity-index$="[[index]]" on-click="_handleSanityClick">[[sanity.key]]&nbsp;&nbsp;
          </template>
          <div class="chapter-border"></div><br>
        </div>
      </div>

      <div class="com-google-gerrit-client-change-Resources-Style-popupContent" hidden$="[[hidenChange]]">
      <p style="padding-top: 10px; margin-bottom: -10px; font-weight: bold; color: green">There are open changes in other repositories.
      <br>If compilation requires additional change please pick the relevant change from the below list.
      <br>If not then choose "Latest".</p>
      <div style="border-style: groove;">
        <template is="dom-repeat" items="{{SubModules}}" as="subModule">
            <p class="main" style="color: rgb(153, 0, 255); padding-top: 15px; margin-bottom: 5px;">[[subModule.name]]:</p>
            <select value="{{subModule.selectedBranch::change}}">
              <template is="dom-repeat" items="{{subModule.branches}}" as="branch">
                <option value="{{branch.value}}">{{branch.desc}}</option>
              </template>
              <div class="chapter-border"></div><br>
            </select>
        </template>
      </div>
    </div>
    <p class="main" style="color: red;font-weight: bold;">[[rebase]]</p>
    <p class="main" style="margin-bottom: 2em;">[[buttonText]]</p>
    <p class="main" style="color: red;font-weight: bold;">[[errorMessage]]</p>
    <a style="color: green;" target=-blank href=[[url]]>[[link]]</a>
  </div>
</gr-dialog>
`;