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

<link rel="import" href="/bower_components/iron-autogrow-textarea/iron-autogrow-textarea.html">
<link rel="import" href="/bower_components/polymer/polymer.html">
<link rel="import" href="../../../behaviors/fire-behavior/fire-behavior.html">
<link rel="import" href="../../shared/gr-dialog/gr-dialog.html">
<link rel="import" href="../../../styles/shared-styles.html">

<dom-module id="gr-confirm-delete-comment-dialog">
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
      p {
        margin-bottom: var(--spacing-l);
      }
      label {
        cursor: pointer;
        display: block;
        width: 100%;
      }
      iron-autogrow-textarea {
        font-family: var(--monospace-font-family);
        font-size: var(--font-size-mono);
        line-height: var(--line-height-mono);
        width: 73ch; /* Add a char to account for the border. */
      }
    </style>
    <gr-dialog
        confirm-label="Delete"
        on-confirm="_handleConfirmTap"
        on-cancel="_handleCancelTap">
      <div class="header" slot="header">Delete Comment</div>
      <div class="main" slot="main">
        <p>This is an admin function. Please only use in exceptional circumstances.</p>
        <label for="messageInput">Enter comment delete reason</label>
        <iron-autogrow-textarea
            id="messageInput"
            class="message"
            autocomplete="on"
            placeholder="<Insert reasoning here>"
            bind-value="{{message}}"></iron-autogrow-textarea>
      </div>
    </gr-dialog>
  </template>
  <script src="gr-confirm-delete-comment-dialog.js"></script>
</dom-module>
