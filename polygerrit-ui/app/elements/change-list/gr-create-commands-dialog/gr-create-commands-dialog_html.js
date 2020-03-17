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
<link rel="import" href="../../shared/gr-shell-command/gr-shell-command.html">

<dom-module id="gr-create-commands-dialog">
  <template>
    <style include="shared-styles">
      ol {
        list-style: decimal;
        margin-left: var(--spacing-l);
      }
      p {
        margin-bottom: var(--spacing-m);
      }
      #commandsDialog {
        max-width: 40em;
      }
    </style>
    <gr-overlay id="commandsOverlay" with-backdrop>
      <gr-dialog
          id="commandsDialog"
          confirm-label="Done"
          cancel-label=""
          confirm-on-enter
          on-confirm="_handleClose">
        <div class="header" slot="header">
          Create change commands
        </div>
        <div class="main" slot="main">
          <ol>
            <li>
              <p>
                Make the changes to the files on your machine
              </p>
            </li>
            <li>
              <p>
                If you are making a new commit use
              </p>
              <gr-shell-command command="[[_createNewCommitCommand]]"></gr-shell-command>
              <p>
                Or to amend an existing commit use
              </p>
              <gr-shell-command command="[[_amendExistingCommitCommand]]"></gr-shell-command>
              <p>
                Please make sure you add a commit message as it becomes the
                description for your change.
              </p>
            </li>
            <li>
              <p>
                Push the change for code review
              </p>
              <gr-shell-command command="[[_pushCommand]]"></gr-shell-command>
            </li>
            <li>
              <p>
                Close this dialog and you should be able to see your recently
                created change in the 'Outgoing changes' section on the
                'Your changes' page.
              </p>
            </li>
          </ol>
        </div>
      </gr-dialog>
    </gr-overlay>
  </template>
  <script src="gr-create-commands-dialog.js"></script>
</dom-module>
