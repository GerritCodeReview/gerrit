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

<link rel="import" href="../../../behaviors/fire-behavior/fire-behavior.html">
<link rel="import" href="../../../behaviors/gr-patch-set-behavior/gr-patch-set-behavior.html">
<link rel="import" href="../../../behaviors/rest-client-behavior/rest-client-behavior.html">
<link rel="import" href="../../../styles/shared-styles.html">
<link rel="import" href="../../shared/gr-download-commands/gr-download-commands.html">

<dom-module id="gr-download-dialog">
  <template>
    <style include="shared-styles">
      :host {
        display: block;
        padding: var(--spacing-m) 0;
      }
      section {
        display: flex;
        padding: var(--spacing-m) var(--spacing-xl);
      }
      .flexContainer {
        display: flex;
        justify-content: space-between;
        padding-top: var(--spacing-m);
      }
      .footer {
        justify-content: flex-end;
      }
      .closeButtonContainer {
        align-items: flex-end;
        display: flex;
        flex: 0;
        justify-content: flex-end;
      }
      .patchFiles,
      .archivesContainer {
        padding-bottom: var(--spacing-m);
      }
      .patchFiles {
        margin-right: var(--spacing-xxl);
      }
      .patchFiles a,
      .archives a {
        display: inline-block;
        margin-right: var(--spacing-l);
      }
      .patchFiles a:last-of-type,
      .archives a:last-of-type {
        margin-right: 0;
      }
      .title {
        flex: 1;
        font-weight: var(--font-weight-bold);
      }
      .hidden {
        display: none;
      }
    </style>
    <section>
      <h3 class="title">
        Patch set [[patchNum]] of [[_computePatchSetQuantity(change.revisions)]]
      </h3>
    </section>
    <section class$="[[_computeShowDownloadCommands(_schemes)]]">
      <gr-download-commands
          id="downloadCommands"
          commands="[[_computeDownloadCommands(change, patchNum, _selectedScheme)]]"
          schemes="[[_schemes]]"
          selected-scheme="{{_selectedScheme}}"></gr-download-commands>
    </section>
    <section class="flexContainer">
      <div class="patchFiles" hidden="[[_computeHidePatchFile(change, patchNum)]]" hidden>
        <label>Patch file</label>
        <div>
          <a
              id="download"
              href$="[[_computeDownloadLink(change, patchNum)]]"
              download>
            [[_computeDownloadFilename(change, patchNum)]]
          </a>
          <a
              href$="[[_computeZipDownloadLink(change, patchNum)]]"
              download>
            [[_computeZipDownloadFilename(change, patchNum)]]
          </a>
        </div>
      </div>
      <div class="archivesContainer" hidden$="[[!config.archives.length]]" hidden>
        <label>Archive</label>
        <div id="archives" class="archives">
          <template is="dom-repeat" items="[[config.archives]]" as="format">
            <a
                href$="[[_computeArchiveDownloadLink(change, patchNum, format)]]"
                download>
              [[format]]
            </a>
          </template>
        </div>
      </div>
    </section>
    <section class="footer">
      <span class="closeButtonContainer">
        <gr-button id="closeButton"
            link
            on-click="_handleCloseTap">Close</gr-button>
      </span>
    </section>
  </template>
  <script src="gr-download-dialog.js"></script>
</dom-module>
