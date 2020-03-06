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
<link rel="import" href="../../../behaviors/fire-behavior/fire-behavior.html">
<link rel="import" href="../../../styles/shared-styles.html">
<link rel="import" href="../../shared/gr-dialog/gr-dialog.html">

<dom-module id="gr-confirm-cherrypick-conflict-dialog">
  <template>
    <style include="shared-styles">
      :host {
        display: block;
      }
      :host([disabled]) {
        opacity: .5;
        pointer-events: none;
      }
      .main {
        display: flex;
        flex-direction: column;
        width: 100%;
      }
    </style>
    <gr-dialog
        confirm-label="Continue"
        on-confirm="_handleConfirmTap"
        on-cancel="_handleCancelTap">
      <div class="header" slot="header">Cherry Pick Conflict!</div>
      <div class="main" slot="main">
        <span>Cherry Pick failed! (merge conflicts)</span>

        <span>Please select "Continue" to continue with conflicts or select "cancel" to close the dialog.</span>
      </div>
    </gr-dialog>
  </template>
  <script src="gr-confirm-cherrypick-conflict-dialog.js"></script>
</dom-module>
