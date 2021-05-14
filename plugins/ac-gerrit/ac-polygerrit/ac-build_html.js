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

  .tooltip {
    position: relative;
    display: inline-block;
  }

  .tooltip .tooltiptext {
    visibility: hidden;
    width: 250px;
    background-color: #f8f8b6;
    color: #949468;
    text-align: center;
    padding: 5px 0;
    margin-left: 250px;
    /* Position the tooltip */
    position: absolute;
    z-index: 1;
  }

  .tooltip:hover .tooltiptext {
    visibility: visible;
  }
</style>
<gr-dialog id="build" confirm-label$="[[Start]]" confirm-on-enter on-cancel="_handleCancelTap"
  on-confirm="_handleConfirmTap" disabled$="{{disabled}}">
  <div class="header" slot="header">[[header]]</div>
  <div class="main" slot="main">
    <img src="[[waitImage]]" width="70" height="70" hidden$="[[hiddenImage]]">
    <div class="com-google-gerrit-client-change-Resources-Style-popupContent">
      <br>
      <div class="tooltip">
        <span class="tooltiptext">No need to change the default value.
          <br>If this is a customer debug version then insert customer name.
          <br>You can set the FIX number by adding ":"<br> For example: nuera:701
        </span>
        <label>Build Name&nbsp;&nbsp;</label>
        <input type="text" value="{{buildName::change}}" />
      </div>
      <br>
      <div style="border-style: groove;">
        <br>
        <h5>Boards</h5>
        <template is="dom-repeat" items="[[boards]]" as="board">
          <label><input type="checkbox" name="board" checked$="{{board.value}}" board-index$="[[index]]"
            on-click="_handleBoardClick" />[[board.label]]</label>
        </template>
      </div>
      <br />
      <template is="dom-if" if="{{is_tp}}"><label><input type="checkbox" checked="{{SIP::change}}" />SIP</label>&emsp;</template>
      <label><input type="checkbox" checked="{{LAB::change}}" />LAB </label>&emsp;
      <template is="dom-if" if="{{show_li}}"><label><input type="checkbox" checked="{{LI::change}}" />LI </label>&emsp;</template>
      <br />

      <div class="com-google-gerrit-client-change-Resources-Style-popupContent" hidden$="[[hidenChange]]">
        <p style="padding-top: 10px; margin-bottom: -10px; font-weight: bold; color: green" >There are open changes in other repositories:</p>
        <div style="border-style: groove;">
          <template is="dom-repeat" items="{{SubModules}}" as="subModule">
              <p class="main" style="color: rgb(153, 0, 255); padding-top: 15px; margin-bottom: 5px;">[[subModule.name]]:</p>
              <select value="{{subModule.selectedBranch::change}}">
                <template is="dom-repeat" items="{{subModule.branches}}" as="branch">
                  <option value="{{branch.value}}">{{branch.desc}}</option>
                </template>
              </select>
          </template>
          <div class="chapter-border"></div>
          <br>
        </div>
      </div>

      <div class="tooltip">
        <span class="tooltiptext">Comma-separated list of additional recipients on build success/failure<br>
          (The requester always receives an email).
        </span>
        <label>Extra recipients&nbsp;&nbsp;</label>

      <input type="text" value="{{recipients::change}}">
      </div>
      <br>
      <div></div>
        <div class="tooltip">
          <span class="tooltiptext">Adds the build number to the version number.<br />
            For example: 7.20A-u7282.252.236.<br />
            This enables easy tracking of the version running on the board.
          </span>
          <label><input type="checkbox" checked="{{git_ver::change}}" />&nbsp;&nbsp;Use git version</label>
        </div>
        <br>
        <div class="tooltip">
          <span class="tooltiptext">If checked, it will use the 'Build Name' as a customer name<br />
            and also will increase the FIX number by 500,<br />
            unless the FIX number was already mentioned in the 'Build Name' field.<br />
            Customer versions are never deleted from artifactory (unlike other builds).
          </span>
          <label><input type="checkbox" checked="{{debug::change}}" />&nbsp;&nbsp;Customer debug version</label>
        </div>

      <br>
      <div></div>
    </div>
    <p class="main" style="color: red;font-weight: bold;">[[errorMessage]]</p>
    <p class="main" style="color: red;font-weight: bold;">[[rebase]]</p>
    <p class="main" style="margin-bottom: 2em;">[[buttonText]]</p>
    <a style="color: green;" target=-blank href=[[url]]>[[link]]</a>
  </div>
</gr-dialog>
`;