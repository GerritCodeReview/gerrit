/**
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
*/
import '../../../../@polymer/polymer/polymer-legacy.js';

import '../../../behaviors/gr-patch-set-behavior/gr-patch-set-behavior.js';
import '../../../behaviors/gr-path-list-behavior/gr-path-list-behavior.js';
import '../../../behaviors/keyboard-shortcut-behavior/keyboard-shortcut-behavior.js';
import '../../core/gr-navigation/gr-navigation.js';
import '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator.js';
import '../../plugins/gr-endpoint-param/gr-endpoint-param.js';
import '../../shared/gr-button/gr-button.js';
import '../../shared/gr-editable-label/gr-editable-label.js';
import '../../shared/gr-fixed-panel/gr-fixed-panel.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';
import '../../shared/gr-storage/gr-storage.js';
import '../gr-default-editor/gr-default-editor.js';
import '../../../styles/shared-styles.js';

const RESTORED_MESSAGE = 'Content restored from a previous edit.';
const SAVING_MESSAGE = 'Saving changes...';
const SAVED_MESSAGE = 'All changes saved';
const SAVE_FAILED_MSG = 'Failed to save changes';

const STORAGE_DEBOUNCE_INTERVAL_MS = 100;

Polymer({
  _template: Polymer.html`
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
        padding: .75em var(--default-horizontal-margin);
      }
      header gr-editable-label {
        font-size: var(--font-size-large);
        --label-style: {
          text-overflow: initial;
          white-space: initial;
          word-break: break-all;
        }
        --input-style: {
          margin-top: 1em;
        }
      }
      .textareaWrapper {
        border: 1px solid var(--border-color);
        border-radius: 3px;
        margin: var(--default-horizontal-margin);
      }
      .textareaWrapper .editButtons {
        display: none;
      }
      .controlGroup {
        align-items: center;
        display: flex;
        font-size: var(--font-size-large);
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
          <gr-button id="close" link="" on-tap="_handleCloseTap">Close</gr-button>
          <gr-button id="save" disabled\$="[[_saveDisabled]]" primary="" link="" on-tap="_saveEdit">Save</gr-button>
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
`,

  is: 'gr-editor-view',

  /**
   * Fired when the title of the page should change.
   *
   * @event title-change
   */

  /**
   * Fired to notify the user of
   *
   * @event show-alert
   */

  properties: {
    /**
     * URL params passed from the router.
     */
    params: {
      type: Object,
      observer: '_paramsChanged',
    },

    _change: Object,
    _changeEditDetail: Object,
    _changeNum: String,
    _patchNum: String,
    _path: String,
    _type: String,
    _content: String,
    _newContent: String,
    _saving: {
      type: Boolean,
      value: false,
    },
    _successfulSave: {
      type: Boolean,
      value: false,
    },
    _saveDisabled: {
      type: Boolean,
      value: true,
      computed: '_computeSaveDisabled(_content, _newContent, _saving)',
    },
    _prefs: Object,
  },

  behaviors: [
    Gerrit.KeyboardShortcutBehavior,
    Gerrit.PatchSetBehavior,
    Gerrit.PathListBehavior,
  ],

  listeners: {
    'content-change': '_handleContentChange',
  },

  keyBindings: {
    'ctrl+s meta+s': '_handleSaveShortcut',
  },

  attached() {
    this._getEditPrefs().then(prefs => { this._prefs = prefs; });
  },

  get storageKey() {
    return `c${this._changeNum}_ps${this._patchNum}_${this._path}`;
  },

  _getLoggedIn() {
    return this.$.restAPI.getLoggedIn();
  },

  _getEditPrefs() {
    return this.$.restAPI.getEditPreferences();
  },

  _paramsChanged(value) {
    if (value.view !== Gerrit.Nav.View.EDIT) { return; }

    this._changeNum = value.changeNum;
    this._path = value.path;
    this._patchNum = value.patchNum || this.EDIT_NAME;

    // NOTE: This may be called before attachment (e.g. while parentElement is
    // null). Fire title-change in an async so that, if attachment to the DOM
    // has been queued, the event can bubble up to the handler in gr-app.
    this.async(() => {
      const title = `Editing ${this.computeTruncatedPath(this._path)}`;
      this.fire('title-change', {title});
    });

    const promises = [];

    promises.push(this._getChangeDetail(this._changeNum));
    promises.push(
        this._getFileData(this._changeNum, this._path, this._patchNum));
    return Promise.all(promises);
  },

  _getChangeDetail(changeNum) {
    return this.$.restAPI.getDiffChangeDetail(changeNum).then(change => {
      this._change = change;
    });
  },

  _handlePathChanged(e) {
    const path = e.detail;
    if (path === this._path) { return Promise.resolve(); }
    return this.$.restAPI.renameFileInChangeEdit(this._changeNum,
        this._path, path).then(res => {
          if (!res.ok) { return; }

          this._successfulSave = true;
          this._viewEditInChangeView();
        });
  },

  _viewEditInChangeView() {
    const patch = this._successfulSave ? this.EDIT_NAME : this._patchNum;
    Gerrit.Nav.navigateToChange(this._change, patch, null,
        patch !== this.EDIT_NAME);
  },

  _getFileData(changeNum, path, patchNum) {
    const storedContent =
          this.$.storage.getEditableContentItem(this.storageKey);

    return this.$.restAPI.getFileContent(changeNum, path, patchNum)
        .then(res => {
          if (storedContent && storedContent.message &&
              storedContent.message !== res.content) {
            this.dispatchEvent(new CustomEvent('show-alert',
                {detail: {message: RESTORED_MESSAGE}, bubbles: true}));

            this._newContent = storedContent.message;
          } else {
            this._newContent = res.content || '';
          }
          this._content = res.content || '';

          // A non-ok response may result if the file does not yet exist.
          // The `type` field of the response is only valid when the file
          // already exists.
          if (res.ok && res.type) {
            this._type = res.type;
          } else {
            this._type = '';
          }
        });
  },

  _saveEdit() {
    this._saving = true;
    this._showAlert(SAVING_MESSAGE);
    this.$.storage.eraseEditableContentItem(this.storageKey);
    return this.$.restAPI.saveChangeEdit(this._changeNum, this._path,
        this._newContent).then(res => {
          this._saving = false;
          this._showAlert(res.ok ? SAVED_MESSAGE : SAVE_FAILED_MSG);
          if (!res.ok) { return; }

          this._content = this._newContent;
          this._successfulSave = true;
        });
  },

  _showAlert(message) {
    this.dispatchEvent(new CustomEvent('show-alert', {
      detail: {message},
      bubbles: true,
    }));
  },

  _computeSaveDisabled(content, newContent, saving) {
    if (saving) { return true; }
    return content === newContent;
  },

  _handleCloseTap() {
    // TODO(kaspern): Add a confirm dialog if there are unsaved changes.
    this._viewEditInChangeView();
  },

  _handleContentChange(e) {
    this.debounce('store', () => {
      const content = e.detail.value;
      if (content) {
        this.set('_newContent', e.detail.value);
        this.$.storage.setEditableContentItem(this.storageKey, content);
      } else {
        this.$.storage.eraseEditableContentItem(this.storageKey);
      }
    }, STORAGE_DEBOUNCE_INTERVAL_MS);
  },

  _handleSaveShortcut(e) {
    e.preventDefault();
    if (!this._saveDisabled) { this._saveEdit(); }
  }
});
