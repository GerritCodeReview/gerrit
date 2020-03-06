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
<link rel="import" href="../../shared/gr-overlay/gr-overlay.html">
<link rel="import" href="../../shared/gr-repo-branch-picker/gr-repo-branch-picker.html">

<dom-module id="gr-create-destination-dialog">
  <template>
    <style include="shared-styles">
    </style>
    <gr-overlay id="createOverlay" with-backdrop>
      <gr-dialog
          confirm-label="View commands"
          on-confirm="_pickerConfirm"
          on-cancel="_handleClose"
          disabled="[[!_repoAndBranchSelected]]">
        <div class="header" slot="header">
          Create change
        </div>
        <div class="main" slot="main">
          <gr-repo-branch-picker
              repo="{{_repo}}"
              branch="{{_branch}}"></gr-repo-branch-picker>
          <p>
            If you haven't done so, you will need to clone the repository.
          </p>
        </div>
      </gr-dialog>
    </gr-overlay>
  </template>
  <script src="gr-create-destination-dialog.js"></script>
</dom-module>
