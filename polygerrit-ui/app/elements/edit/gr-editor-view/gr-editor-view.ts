/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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
import '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator';
import '../../plugins/gr-endpoint-param/gr-endpoint-param';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-editable-label/gr-editable-label';
import '../../shared/gr-storage/gr-storage';
import '../gr-default-editor/gr-default-editor';
import '../../../styles/shared-styles';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-editor-view_html';
import {KeyboardShortcutMixin} from '../../../mixins/keyboard-shortcut-mixin/keyboard-shortcut-mixin';
import {
  GerritNav,
  GenerateUrlEditViewParameters,
} from '../../core/gr-navigation/gr-navigation';
import {computeTruncatedPath} from '../../../utils/path-list-util';
import {customElement, property} from '@polymer/decorators';
import {
  ChangeInfo,
  PatchSetNum,
  EditPreferencesInfo,
  Base64FileContent,
  NumericChangeId,
  EditPatchSetNum,
} from '../../../types/common';
import {GrStorage} from '../../shared/gr-storage/gr-storage';
import {HttpMethod, NotifyType} from '../../../constants/constants';
import {fireAlert, fireTitleChange} from '../../../utils/event-util';
import {appContext} from '../../../services/app-context';
import {ErrorCallback} from '../../../api/rest';
import {assertIsDefined} from '../../../utils/common-util';

const RESTORED_MESSAGE = 'Content restored from a previous edit.';
const SAVING_MESSAGE = 'Saving changes...';
const SAVED_MESSAGE = 'All changes saved';
const SAVE_FAILED_MSG = 'Failed to save changes';
const PUBLISHING_EDIT_MSG = 'Publishing edit...';
const PUBLISH_FAILED_MSG = 'Failed to publish edit';

const STORAGE_DEBOUNCE_INTERVAL_MS = 100;

const DEBOUNCER_STORE = 'store';

@customElement('gr-editor-view')
export class GrEditorView extends KeyboardShortcutMixin(
  GestureEventListeners(LegacyElementMixin(PolymerElement))
) {
  static get template() {
    return htmlTemplate;
  }

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

  @property({type: Object, observer: '_paramsChanged'})
  params?: GenerateUrlEditViewParameters;

  @property({type: Object})
  _change?: ChangeInfo | null;

  @property({type: Number})
  _changeNum?: NumericChangeId;

  @property({type: String})
  _patchNum?: PatchSetNum;

  @property({type: String})
  _path?: string;

  @property({type: String})
  _type?: string;

  @property({type: String})
  _content?: string;

  @property({type: String})
  _newContent?: string;

  @property({type: Boolean})
  _saving = false;

  @property({type: Boolean})
  _successfulSave = false;

  @property({
    type: Boolean,
    computed: '_computeSaveDisabled(_content, _newContent, _saving)',
  })
  _saveDisabled = true;

  @property({type: Object})
  _prefs?: EditPreferencesInfo;

  @property({type: Number})
  _lineNum?: number;

  private readonly restApiService = appContext.restApiService;

  private readonly storage = new GrStorage();

  reporting = appContext.reportingService;

  get keyBindings() {
    return {
      'ctrl+s meta+s': '_handleSaveShortcut',
    };
  }

  /** @override */
  created() {
    super.created();
    this.addEventListener('content-change', e => {
      this._handleContentChange(e as CustomEvent<{value: string}>);
    });
  }

  /** @override */
  attached() {
    super.attached();
    this._getEditPrefs().then(prefs => {
      this._prefs = prefs;
    });
  }

  /** @override */
  detached() {
    this.cancelDebouncer(DEBOUNCER_STORE);
  }

  get storageKey() {
    return `c${this._changeNum}_ps${this._patchNum}_${this._path}`;
  }

  _getLoggedIn() {
    return this.restApiService.getLoggedIn();
  }

  _getEditPrefs() {
    return this.restApiService.getEditPreferences();
  }

  _paramsChanged(value: GenerateUrlEditViewParameters) {
    if (value.view !== GerritNav.View.EDIT) {
      return;
    }

    this._changeNum = value.changeNum;
    this._path = value.path;
    this._patchNum = value.patchNum || (EditPatchSetNum as PatchSetNum);
    this._lineNum =
      typeof value.lineNum === 'string' ? Number(value.lineNum) : value.lineNum;

    // NOTE: This may be called before attachment (e.g. while parentElement is
    // null). Fire title-change in an async so that, if attachment to the DOM
    // has been queued, the event can bubble up to the handler in gr-app.
    this.async(() => {
      const title = `Editing ${computeTruncatedPath(value.path)}`;
      fireTitleChange(this, title);
    });

    const promises = [];

    promises.push(this._getChangeDetail(this._changeNum));
    promises.push(
      this._getFileData(this._changeNum, this._path, this._patchNum)
    );
    return Promise.all(promises);
  }

  _getChangeDetail(changeNum: NumericChangeId) {
    return this.restApiService.getDiffChangeDetail(changeNum).then(change => {
      this._change = change;
    });
  }

  _handlePathChanged(e: CustomEvent<string>) {
    // TODO(TS) could be cleand up, it was added for type requirements
    if (this._changeNum === undefined || !this._path) {
      return Promise.reject(new Error('changeNum or path undefined'));
    }
    const path = e.detail;
    if (path === this._path) {
      return Promise.resolve();
    }
    return this.restApiService
      .renameFileInChangeEdit(this._changeNum, this._path, path)
      .then(res => {
        if (!res || !res.ok) {
          return;
        }

        this._successfulSave = true;
        this._viewEditInChangeView();
      });
  }

  _viewEditInChangeView() {
    const patch = this._successfulSave
      ? (EditPatchSetNum as PatchSetNum)
      : this._patchNum;
    if (this._change && patch)
      GerritNav.navigateToChange(
        this._change,
        patch,
        undefined,
        patch !== EditPatchSetNum
      );
  }

  _getFileData(
    changeNum: NumericChangeId,
    path: string,
    patchNum?: PatchSetNum
  ) {
    if (patchNum === undefined) {
      return Promise.reject(new Error('patchNum undefined'));
    }
    const storedContent = this.storage.getEditableContentItem(this.storageKey);

    return this.restApiService
      .getFileContent(changeNum, path, patchNum)
      .then(res => {
        const content = (res && (res as Base64FileContent).content) || '';
        if (
          storedContent &&
          storedContent.message &&
          storedContent.message !== content
        ) {
          fireAlert(this, RESTORED_MESSAGE);

          this._newContent = storedContent.message;
        } else {
          this._newContent = content;
        }
        this._content = content;

        // A non-ok response may result if the file does not yet exist.
        // The `type` field of the response is only valid when the file
        // already exists.
        if (res && res.ok && res.type) {
          this._type = res.type;
        } else {
          this._type = '';
        }
      });
  }

  _saveEdit() {
    if (this._changeNum === undefined || !this._path) {
      return Promise.reject(new Error('changeNum or path undefined'));
    }
    this._saving = true;
    this._showAlert(SAVING_MESSAGE);
    this.storage.eraseEditableContentItem(this.storageKey);
    if (!this._newContent)
      return Promise.reject(new Error('new content undefined'));
    return this.restApiService
      .saveChangeEdit(this._changeNum, this._path, this._newContent)
      .then(res => {
        this._saving = false;
        this._showAlert(res.ok ? SAVED_MESSAGE : SAVE_FAILED_MSG);
        if (!res.ok) {
          return res;
        }

        this._content = this._newContent;
        this._successfulSave = true;
        return res;
      });
  }

  _showAlert(message: string) {
    fireAlert(this, message);
  }

  _computeSaveDisabled(
    content?: string,
    newContent?: string,
    saving?: boolean
  ) {
    // Polymer 2: check for undefined
    if ([content, newContent, saving].includes(undefined)) {
      return true;
    }

    if (saving) {
      return true;
    }
    return content === newContent;
  }

  _handleCloseTap() {
    // TODO(kaspern): Add a confirm dialog if there are unsaved changes.
    this._viewEditInChangeView();
  }

  _handleSaveTap() {
    this._saveEdit().then(res => {
      if (res.ok) this._viewEditInChangeView();
    });
  }

  _handlePublishTap() {
    assertIsDefined(this._changeNum, '_changeNum');

    const changeNum = this._changeNum;
    this._saveEdit().then(() => {
      const handleError: ErrorCallback = response => {
        this._showAlert(PUBLISH_FAILED_MSG);
        this.reporting.error(new Error(response?.statusText));
      };

      this._showAlert(PUBLISHING_EDIT_MSG);

      this.restApiService
        .executeChangeAction(
          changeNum,
          HttpMethod.POST,
          '/edit:publish',
          undefined,
          {notify: NotifyType.NONE},
          handleError
        )
        .then(() => {
          assertIsDefined(this._change, '_change');
          GerritNav.navigateToChange(this._change);
        });
    });
  }

  _handleContentChange(e: CustomEvent<{value: string}>) {
    this.debounce(
      DEBOUNCER_STORE,
      () => {
        const content = e.detail.value;
        if (content) {
          this.set('_newContent', e.detail.value);
          this.storage.setEditableContentItem(this.storageKey, content);
        } else {
          this.storage.eraseEditableContentItem(this.storageKey);
        }
      },
      STORAGE_DEBOUNCE_INTERVAL_MS
    );
  }

  _handleSaveShortcut(e: KeyboardEvent) {
    e.preventDefault();
    if (!this._saveDisabled) {
      this._saveEdit();
    }
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-editor-view': GrEditorView;
  }
}
