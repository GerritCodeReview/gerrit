import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
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
    <section class\$="[[_computeShowDownloadCommands(_schemes)]]">
      <gr-download-commands id="downloadCommands" commands="[[_computeDownloadCommands(change, patchNum, _selectedScheme)]]" schemes="[[_schemes]]" selected-scheme="{{_selectedScheme}}"></gr-download-commands>
    </section>
    <section class="flexContainer">
      <div class="patchFiles" hidden="[[_computeHidePatchFile(change, patchNum)]]">
        <label>Patch file</label>
        <div>
          <a id="download" href\$="[[_computeDownloadLink(change, patchNum)]]" download="">
            [[_computeDownloadFilename(change, patchNum)]]
          </a>
          <a href\$="[[_computeZipDownloadLink(change, patchNum)]]" download="">
            [[_computeZipDownloadFilename(change, patchNum)]]
          </a>
        </div>
      </div>
      <div class="archivesContainer" hidden\$="[[!config.archives.length]]" hidden="">
        <label>Archive</label>
        <div id="archives" class="archives">
          <template is="dom-repeat" items="[[config.archives]]" as="format">
            <a href\$="[[_computeArchiveDownloadLink(change, patchNum, format)]]" download="">
              [[format]]
            </a>
          </template>
        </div>
      </div>
    </section>
    <section class="footer">
      <span class="closeButtonContainer">
        <gr-button id="closeButton" link="" on-click="_handleCloseTap">Close</gr-button>
      </span>
    </section>
`;
