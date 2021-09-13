/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
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
import {html} from '@polymer/polymer/lib/utils/html-tag';

export const htmlTemplate = html`
  <style include="gr-font-styles">
    /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
  </style>
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
    .hidden {
      display: none;
    }
    gr-download-commands {
      width: min(80vw, 1200px);
    }
  </style>
  <section>
    <h3 class="heading-3">
      Patch set [[patchNum]] of [[_computePatchSetQuantity(change.revisions)]]
    </h3>
  </section>
  <section class$="[[_computeShowDownloadCommands(_schemes)]]">
    <gr-download-commands
      id="downloadCommands"
      commands="[[_computeDownloadCommands(change, patchNum, _selectedScheme)]]"
      schemes="[[_schemes]]"
      selected-scheme="{{_selectedScheme}}"
      show-keyboard-shortcut-tooltips
    ></gr-download-commands>
  </section>
  <section class="flexContainer">
    <div
      class="patchFiles"
      hidden="[[_computeHidePatchFile(change, patchNum)]]"
    >
      <label>Patch file</label>
      <div>
        <a
          id="download"
          href$="[[_computeDownloadLink(change, patchNum)]]"
          download=""
        >
          [[_computeDownloadFilename(change, patchNum)]]
        </a>
        <a href$="[[_computeZipDownloadLink(change, patchNum)]]" download="">
          [[_computeZipDownloadFilename(change, patchNum)]]
        </a>
      </div>
    </div>
    <div
      class="archivesContainer"
      hidden$="[[!config.archives.length]]"
      hidden=""
    >
      <label>Archive</label>
      <div id="archives" class="archives">
        <template is="dom-repeat" items="[[config.archives]]" as="format">
          <a
            href$="[[_computeArchiveDownloadLink(change, patchNum, format)]]"
            download=""
          >
            [[format]]
          </a>
        </template>
      </div>
    </div>
  </section>
  <section class="footer">
    <span class="closeButtonContainer">
      <gr-button id="closeButton" link="" on-click="_handleCloseTap"
        >Close</gr-button
      >
    </span>
  </section>
`;
