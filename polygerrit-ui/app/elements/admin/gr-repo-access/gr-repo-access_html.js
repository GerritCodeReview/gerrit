import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
    </style>
    <style include="gr-subpage-styles">
      gr-button,
      #inheritsFrom,
      #editInheritFromInput,
      .editing #inheritFromName,
      .weblinks,
      .editing .invisible{
        display: none;
      }
      #inheritsFrom.show {
        display: flex;
        min-height: 2em;
        align-items: center;
      }
      .weblink {
        margin-right: var(--spacing-xs);
      }
      .weblinks.show,
      .referenceContainer {
        display: block;
      }
      .rightsText {
        margin-right: var(--spacing-s);
      }

      .editing gr-button,
      .admin #editBtn {
        display: inline-block;
        margin: var(--spacing-l) 0;
      }
      .editing #editInheritFromInput {
        display: inline-block;
      }
    </style>
    <style include="gr-menu-page-styles">
      /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
    </style>
    <main class\$="[[_computeMainClass(_ownerOf, _canUpload, _editing)]]">
      <div id="loading" class\$="[[_computeLoadingClass(_loading)]]">
        Loading...
      </div>
      <div id="loadedContent" class\$="[[_computeLoadingClass(_loading)]]">
        <h3 id="inheritsFrom" class\$="[[_computeShowInherit(_inheritsFrom)]]">
          <span class="rightsText">Rights Inherit From</span>
          <a href\$="[[_computeParentHref(_inheritsFrom.name)]]" rel="noopener" id="inheritFromName">
            [[_inheritsFrom.name]]</a>
          <gr-autocomplete id="editInheritFromInput" text="{{_inheritFromFilter}}" query="[[_query]]" on-commit="_handleUpdateInheritFrom"></gr-autocomplete>
        </h3>
        <div class\$="weblinks [[_computeWebLinkClass(_weblinks)]]">
          History:
          <template is="dom-repeat" items="[[_weblinks]]" as="link">
            <a href="[[link.url]]" class="weblink" rel="noopener" target="[[link.target]]">
              [[link.name]]
            </a>
          </template>
        </div>
        <gr-button id="editBtn" on-click="_handleEdit">[[_editOrCancel(_editing)]]</gr-button>
        <gr-button id="saveBtn" primary="" class\$="[[_computeSaveBtnClass(_ownerOf)]]" on-click="_handleSave" disabled\$="[[!_modified]]">Save</gr-button>
        <gr-button id="saveReviewBtn" primary="" class\$="[[_computeSaveReviewBtnClass(_canUpload)]]" on-click="_handleSaveForReview" disabled\$="[[!_modified]]">Save for review</gr-button>
        <template is="dom-repeat" items="{{_sections}}" initial-count="5" target-framerate="60" as="section">
          <gr-access-section capabilities="[[_capabilities]]" section="{{section}}" labels="[[_labels]]" can-upload="[[_canUpload]]" editing="[[_editing]]" owner-of="[[_ownerOf]]" groups="[[_groups]]" on-added-section-removed="_handleAddedSectionRemoved"></gr-access-section>
        </template>
        <div class="referenceContainer">
          <gr-button id="addReferenceBtn" on-click="_handleCreateSection">Add Reference</gr-button>
        </div>
      </div>
    </main>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`;
