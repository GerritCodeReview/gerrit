<!--
@license
Copyright (C) 2018 The Android Open Source Project

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
<link rel="import" href="../../shared/gr-dialog/gr-dialog.html">
<link rel="import" href="../../../styles/shared-styles.html">

<dom-module id="gr-error-dialog">
  <template>
    <style include="shared-styles">
      .main {
        max-height: 40em;
        max-width: 60em;
        overflow-y: auto;
        white-space: pre-wrap;
      }
      @media screen and (max-width: 50em) {
        .main {
          max-height: none;
          max-width: 50em;
        }
      }
      .signInLink {
        text-decoration: none;
      }
    </style>
    <gr-dialog
        id="dialog"
        cancel-label=""
        on-confirm="_handleConfirm"
        confirm-label="Dismiss"
        confirm-on-enter>
      <div class="header" slot="header">An error occurred</div>
      <div class="main" slot="main">[[text]]</div>
      <gr-button
          id="signIn"
          class$="signInLink"
          hidden$="[[!showSignInButton]]"
          link
          slot="footer">
        <a href$="[[loginUrl]]" class="signInLink">Sign in</a>
      </gr-button>
    </gr-dialog>
  </template>
  <script src="gr-error-dialog.js"></script>
</dom-module>
