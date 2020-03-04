<!--
@license
Copyright (C) 2016 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

<link rel="import" href="/bower_components/polymer/polymer.html">
<link rel="import" href="../../../styles/gr-form-styles.html">
<link rel="import" href="../../shared/gr-button/gr-button.html">
<link rel="import" href="../../shared/gr-copy-clipboard/gr-copy-clipboard.html">
<link rel="import" href="../../shared/gr-overlay/gr-overlay.html">
<link rel="import" href="../../shared/gr-rest-api-interface/gr-rest-api-interface.html">
<link rel="import" href="../../../styles/shared-styles.html">

<dom-module id="gr-http-password">
  <template>
    <style include="shared-styles">
      .password {
        font-family: var(--monospace-font-family);
        font-size: var(--font-size-mono);
        line-height: var(--line-height-mono);
      }
      #generatedPasswordOverlay {
        padding: var(--spacing-xxl);
        width: 50em;
      }
      #generatedPasswordDisplay {
        margin: var(--spacing-l) 0;
      }
      #generatedPasswordDisplay .title {
        width: unset;
      }
      #generatedPasswordDisplay .value {
        font-family: var(--monospace-font-family);
        font-size: var(--font-size-mono);
        line-height: var(--line-height-mono);
      }
      #passwordWarning {
        font-style: italic;
        text-align: center;
      }
      .closeButton {
        bottom: 2em;
        position: absolute;
        right: 2em;
      }
    </style>
    <style include="gr-form-styles">
      /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
    </style>
    <div class="gr-form-styles">
      <div hidden$="[[_passwordUrl]]">
        <section>
          <span class="title">Username</span>
          <span class="value">[[_username]]</span>
        </section>
        <gr-button
            id="generateButton"
            on-click="_handleGenerateTap">Generate new password</gr-button>
      </div>
      <span hidden$="[[!_passwordUrl]]">
        <a href$="[[_passwordUrl]]" target="_blank" rel="noopener">
          Obtain password</a>
        (opens in a new tab)
      </span>
    </div>
    <gr-overlay
        id="generatedPasswordOverlay"
        on-iron-overlay-closed="_generatedPasswordOverlayClosed"
        with-backdrop>
      <div class="gr-form-styles">
        <section id="generatedPasswordDisplay">
          <span class="title">New Password:</span>
          <span class="value">[[_generatedPassword]]</span>
          <gr-copy-clipboard
              has-tooltip
              button-title="Copy password to clipboard"
              hide-input
              text="[[_generatedPassword]]">
          </gr-copy-clipboard>
        </section>
        <section id="passwordWarning">
          This password will not be displayed again.<br>
          If you lose it, you will need to generate a new one.
        </section>
        <gr-button
            link
            class="closeButton"
            on-click="_closeOverlay">Close</gr-button>
      </div>
    </gr-overlay>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
  </template>
  <script src="gr-http-password.js"></script>
</dom-module>
