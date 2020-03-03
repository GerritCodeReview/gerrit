<!--
@license
Copyright (C) 2017 The Android Open Source Project

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
<link rel="import" href="../../shared/gr-dialog/gr-dialog.html">
<link rel="import" href="../../../styles/shared-styles.html">

<dom-module id="gr-confirm-delete-item-dialog">
  <template>
    <style include="shared-styles">
      :host {
        display: block;
        width: 30em;
      }
    </style>
    <gr-dialog
        confirm-label="Delete [[_computeItemName(itemType)]]"
        confirm-on-enter
        on-confirm="_handleConfirmTap"
        on-cancel="_handleCancelTap">
      <div class="header" slot="header">[[_computeItemName(itemType)]] Deletion</div>
      <div class="main" slot="main">
        <label for="branchInput">
          Do you really want to delete the following [[_computeItemName(itemType)]]?
        </label>
        <div>
          [[item]]
        </div>
      </div>
    </gr-dialog>
  </template>
  <script src="gr-confirm-delete-item-dialog.js"></script>
</dom-module>
