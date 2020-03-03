import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      :host {
        background-color: var(--view-background-color);
      }
      gr-fixed-panel {
        background-color: var(--edit-mode-background-color);
        border-bottom: 1px var(--border-color) solid;
        z-index: 1;
      }
      header,
      .subHeader {
        align-items: center;
        display: flex;
        justify-content: space-between;
        padding: var(--spacing-m) var(--spacing-l);
      }
      header gr-editable-label {
        font-family: var(--header-font-family);
        font-size: var(--font-size-h3);
        font-weight: var(--font-weight-h3);
        line-height: var(--line-height-h3);
        --label-style: {
          text-overflow: initial;
          white-space: initial;
          word-break: break-all;
        }
        --input-style: {
          margin-top: var(--spacing-l);
        }
      }
      .textareaWrapper {
        border: 1px solid var(--border-color);
        border-radius: var(--border-radius);
        margin: var(--spacing-l);
      }
      .textareaWrapper .editButtons {
        display: none;
      }
      .controlGroup {
        align-items: center;
        display: flex;
        font-family: var(--header-font-family);
        font-size: var(--font-size-h3);
        font-weight: var(--font-weight-h3);
        line-height: var(--line-height-h3);
      }
      .rightControls {
        justify-content: flex-end;
      }
      @media screen and (max-width: 50em) {
        header,
        .subHeader {
          display: block;
        }
        .rightControls {
          float: right;
        }
      }
    </style>
    <gr-fixed-panel keep-on-scroll="">
      <header>
        <span class="controlGroup">
          <span>Edit mode</span>
          <span class="separator"></span>
          <gr-editable-label label-text="File path" value="[[_path]]" placeholder="File path..." on-changed="_handlePathChanged"></gr-editable-label>
        </span>
        <span class="controlGroup rightControls">
          <gr-button id="close" link="" on-click="_handleCloseTap">Close</gr-button>
          <gr-button id="save" disabled\$="[[_saveDisabled]]" primary="" link="" on-click="_saveEdit">Save</gr-button>
        </span>
      </header>
    </gr-fixed-panel>
    <div class="textareaWrapper">
      <gr-endpoint-decorator id="editorEndpoint" name="editor">
        <gr-endpoint-param name="fileContent" value="[[_newContent]]"></gr-endpoint-param>
        <gr-endpoint-param name="prefs" value="[[_prefs]]"></gr-endpoint-param>
        <gr-endpoint-param name="fileType" value="[[_type]]"></gr-endpoint-param>
        <gr-default-editor id="file" file-content="[[_newContent]]"></gr-default-editor>
      </gr-endpoint-decorator>
    </div>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
    <gr-storage id="storage"></gr-storage>
`;
