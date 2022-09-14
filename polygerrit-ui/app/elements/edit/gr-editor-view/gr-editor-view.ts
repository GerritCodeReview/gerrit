/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator';
import '../../plugins/gr-endpoint-param/gr-endpoint-param';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-editable-label/gr-editable-label';
import '../gr-default-editor/gr-default-editor';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {computeTruncatedPath} from '../../../utils/path-list-util';
import {
  PatchSetNum,
  EditPreferencesInfo,
  Base64FileContent,
  NumericChangeId,
  EDIT,
  PatchSetNumber,
} from '../../../types/common';
import {ParsedChangeInfo} from '../../../types/types';
import {HttpMethod, NotifyType} from '../../../constants/constants';
import {fireAlert, fireTitleChange} from '../../../utils/event-util';
import {getAppContext} from '../../../services/app-context';
import {ErrorCallback} from '../../../api/rest';
import {assertIsDefined} from '../../../utils/common-util';
import {debounce, DelayedTask} from '../../../utils/async-util';
import {changeIsMerged, changeIsAbandoned} from '../../../utils/change-util';
import {Modifier} from '../../../utils/dom-util';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, PropertyValues, html, css, nothing} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {subscribe} from '../../lit/subscription-controller';
import {GenerateUrlEditViewParameters} from '../../../utils/router-util';
import {GerritView} from '../../../services/router/router-model';
import {resolve} from '../../../models/dependency';
import {changeModelToken} from '../../../models/change/change-model';
import {ShortcutController} from '../../lit/shortcut-controller';

const RESTORED_MESSAGE = 'Content restored from a previous edit.';
const SAVING_MESSAGE = 'Saving changes...';
const SAVED_MESSAGE = 'All changes saved';
const SAVE_FAILED_MSG = 'Failed to save changes';
const PUBLISHING_EDIT_MSG = 'Publishing edit...';
const PUBLISH_FAILED_MSG = 'Failed to publish edit';

const STORAGE_DEBOUNCE_INTERVAL_MS = 100;

@customElement('gr-editor-view')
export class GrEditorView extends LitElement {
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

  @property({type: Object})
  params?: GenerateUrlEditViewParameters;

  // private but used in test
  @state() change?: ParsedChangeInfo;

  // private but used in test
  @state() changeNum?: NumericChangeId;

  // private but used in test
  @state() patchNum?: PatchSetNum;

  // private but used in test
  @state() path?: string;

  // private but used in test
  @state() type?: string;

  // private but used in test
  @state() content?: string;

  // private but used in test
  @state() newContent = '';

  // private but used in test
  @state() saving = false;

  // private but used in test
  @state() successfulSave = false;

  @state() private editPrefs?: EditPreferencesInfo;

  @state() private lineNum?: number;

  @state() private latestPatchsetNumber?: PatchSetNumber;

  private readonly restApiService = getAppContext().restApiService;

  private readonly storage = getAppContext().storageService;

  private readonly reporting = getAppContext().reportingService;

  private readonly userModel = getAppContext().userModel;

  private readonly getChangeModel = resolve(this, changeModelToken);

  private readonly shortcuts = new ShortcutController(this);

  // Tests use this so needs to be non private
  storeTask?: DelayedTask;

  constructor() {
    super();
    this.addEventListener('content-change', e => {
      this.handleContentChange(e as CustomEvent<{value: string}>);
    });
    subscribe(
      this,
      () => this.userModel.editPreferences$,
      editPreferences => {
        this.editPrefs = editPreferences;
      }
    );
    subscribe(
      this,
      () => this.getChangeModel().latestPatchNum$,
      x => (this.latestPatchsetNumber = x)
    );
    this.shortcuts.addLocal({key: 's', modifiers: [Modifier.CTRL_KEY]}, () =>
      this.handleSaveShortcut()
    );
    this.shortcuts.addLocal({key: 's', modifiers: [Modifier.META_KEY]}, () =>
      this.handleSaveShortcut()
    );
  }

  override connectedCallback() {
    super.connectedCallback();
  }

  override disconnectedCallback() {
    this.storeTask?.flush();
    super.disconnectedCallback();
  }

  static override get styles() {
    return [
      sharedStyles,
      css`
        :host {
          background-color: var(--view-background-color);
        }
        .stickyHeader {
          background-color: var(--edit-mode-background-color);
          border-bottom: 1px var(--border-color) solid;
          position: sticky;
          top: 0;
          z-index: 1;
        }
        header {
          align-items: center;
          display: flex;
          flex-wrap: wrap;
          justify-content: space-between;
          padding: var(--spacing-m) var(--spacing-l);
        }
        header gr-editable-label {
          font-family: var(--header-font-family);
          font-size: var(--font-size-h3);
          font-weight: var(--font-weight-h3);
          line-height: var(--line-height-h3);
        }
        header gr-editable-label::part(label) {
          text-overflow: initial;
          white-space: initial;
          word-break: break-all;
        }
        header gr-editable-label::part(input-container) {
          margin-top: var(--spacing-l);
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
        .warning {
          color: var(--error-text-color);
        }
      `,
    ];
  }

  override render() {
    return html` ${this.renderHeader()} ${this.renderEndpoint()} `;
  }

  private renderHeader() {
    return html`
      <div class="stickyHeader">
        <header>
          <span class="controlGroup">
            <span>Edit mode</span>
            ${this.renderEditingOldPatchsetWarning()}
            <span class="separator"></span>
            <gr-editable-label
              labelText="File path"
              .value=${this.path}
              placeholder="File path..."
              @changed=${this.handlePathChanged}
            ></gr-editable-label>
          </span>
          <span class="controlGroup rightControls">
            <gr-button id="close" link="" @click=${this.handleCloseTap}
              >Cancel</gr-button
            >
            <gr-button
              id="save"
              ?disabled=${this.computeSaveDisabled()}
              primary=""
              link=""
              title="Save and Close the file"
              @click=${this.handleSaveTap}
              >Save</gr-button
            >
            <gr-button
              id="publish"
              link=""
              primary=""
              title="Publish your edit. A new patchset will be created."
              @click=${this.handlePublishTap}
              ?disabled=${this.computeSaveDisabled()}
              >Save & Publish</gr-button
            >
          </span>
        </header>
      </div>
    `;
  }

  private renderEditingOldPatchsetWarning() {
    const patchset = this.params?.patchNum;
    if (patchset === this.latestPatchsetNumber) return nothing;
    return html`<span class="warning">&nbsp;(Old Patchset)</span>`;
  }

  private renderEndpoint() {
    return html`
      <div class="textareaWrapper">
        <gr-endpoint-decorator id="editorEndpoint" name="editor">
          <gr-endpoint-param
            name="fileContent"
            .value=${this.newContent}
          ></gr-endpoint-param>
          <gr-endpoint-param
            name="prefs"
            .value=${this.editPrefs}
          ></gr-endpoint-param>
          <gr-endpoint-param
            name="fileType"
            .value=${this.type}
          ></gr-endpoint-param>
          <gr-endpoint-param
            name="lineNum"
            .value=${this.lineNum}
          ></gr-endpoint-param>
          <gr-default-editor
            id="file"
            .fileContent=${this.newContent}
          ></gr-default-editor>
        </gr-endpoint-decorator>
      </div>
    `;
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('params')) {
      this.paramsChanged();
    }

    if (changedProperties.has('change')) {
      this.navigateToChangeIfEdit();
    }

    if (changedProperties.has('change') || changedProperties.has('type')) {
      this.navigateToChangeIfEditType();
    }
  }

  get storageKey() {
    return `c${this.changeNum}_ps${this.patchNum}_${this.path}`;
  }

  // private but used in test
  paramsChanged() {
    if (!this.params) return;

    if (this.params.view !== GerritView.EDIT) {
      return;
    }

    this.changeNum = this.params.changeNum;
    this.path = this.params.path;
    this.patchNum = this.params.patchNum || (EDIT as PatchSetNum);
    this.lineNum =
      typeof this.params.lineNum === 'string'
        ? Number(this.params.lineNum)
        : this.params.lineNum;

    // NOTE: This may be called before attachment (e.g. while parentElement is
    // null). Fire title-change in an async so that, if attachment to the DOM
    // has been queued, the event can bubble up to the handler in gr-app.
    setTimeout(() => {
      if (!this.params) return;
      const title = `Editing ${computeTruncatedPath(this.params.path)}`;
      fireTitleChange(this, title);
    });

    const promises = [];

    promises.push(this.getChangeDetail(this.changeNum));
    promises.push(this.getFileData(this.changeNum, this.path, this.patchNum));
    return Promise.all(promises);
  }

  private async getChangeDetail(changeNum: NumericChangeId) {
    this.change = await this.restApiService.getChangeDetail(changeNum);
  }

  private navigateToChangeIfEdit() {
    if (!this.change) return;
    if (!changeIsMerged(this.change) && !changeIsAbandoned(this.change)) return;
    fireAlert(
      this,
      'Change edits cannot be created if change is merged or abandoned. Redirected to non edit mode.'
    );
    GerritNav.navigateToChange(this.change);
  }

  private navigateToChangeIfEditType() {
    if (!this.change || !this.type || !this.type.startsWith('image/')) return;

    // Prevent editing binary files
    fireAlert(this, 'You cannot edit binary files within the inline editor.');
    GerritNav.navigateToChange(this.change);
  }

  // private but used in test
  async handlePathChanged(e: CustomEvent<string>): Promise<void> {
    // TODO(TS) could be cleaned up, it was added for type requirements
    if (this.changeNum === undefined || !this.path) {
      throw new Error('changeNum or path undefined');
    }
    const path = e.detail;
    if (path === this.path) return;
    const res = await this.restApiService.renameFileInChangeEdit(
      this.changeNum,
      this.path,
      path
    );
    if (!res?.ok) return;

    this.successfulSave = true;
    this.viewEditInChangeView();
  }

  // private but used in test
  viewEditInChangeView() {
    if (this.change)
      GerritNav.navigateToChange(this.change, {
        isEdit: true,
        forceReload: true,
      });
  }

  // private but used in test
  getFileData(
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

          this.newContent = storedContent.message;
        } else {
          this.newContent = content;
        }
        this.content = content;

        // A non-ok response may result if the file does not yet exist.
        // The `type` field of the response is only valid when the file
        // already exists.
        if (res && res.ok && res.type) {
          this.type = res.type;
        } else {
          this.type = '';
        }
      });
  }

  // private but used in test
  saveEdit() {
    if (this.changeNum === undefined || !this.path) {
      return Promise.reject(new Error('changeNum or path undefined'));
    }
    this.saving = true;
    this.showAlert(SAVING_MESSAGE);
    this.storage.eraseEditableContentItem(this.storageKey);
    if (!this.newContent)
      return Promise.reject(new Error('new content undefined'));
    return this.restApiService
      .saveChangeEdit(this.changeNum, this.path, this.newContent)
      .then(res => {
        this.saving = false;
        this.showAlert(res.ok ? SAVED_MESSAGE : SAVE_FAILED_MSG);
        if (!res.ok) {
          return res;
        }

        this.content = this.newContent;
        this.successfulSave = true;
        return res;
      });
  }

  // private but used in test
  showAlert(message: string) {
    fireAlert(this, message);
  }

  computeSaveDisabled() {
    if ([this.content, this.newContent, this.saving].includes(undefined)) {
      return true;
    }

    if (this.saving) {
      return true;
    }
    return this.content === this.newContent;
  }

  // private but used in test
  handleCloseTap = () => {
    // TODO(kaspern): Add a confirm dialog if there are unsaved changes.
    this.viewEditInChangeView();
  };

  private handleSaveTap = () => {
    this.saveEdit().then(res => {
      if (res.ok) this.viewEditInChangeView();
    });
  };

  private handlePublishTap = () => {
    assertIsDefined(this.changeNum, 'changeNum');

    const changeNum = this.changeNum;
    this.saveEdit().then(() => {
      const handleError: ErrorCallback = response => {
        this.showAlert(PUBLISH_FAILED_MSG);
        this.reporting.error(new Error(response?.statusText));
      };

      this.showAlert(PUBLISHING_EDIT_MSG);

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
          assertIsDefined(this.change, 'change');
          GerritNav.navigateToChange(this.change, {forceReload: true});
        });
    });
  };

  private handleContentChange(e: CustomEvent<{value: string}>) {
    this.storeTask = debounce(
      this.storeTask,
      () => {
        const content = e.detail.value;
        if (content) {
          this.newContent = e.detail.value;
          this.storage.setEditableContentItem(this.storageKey, content);
        } else {
          this.storage.eraseEditableContentItem(this.storageKey);
        }
      },
      STORAGE_DEBOUNCE_INTERVAL_MS
    );
  }

  // private but used in test
  handleSaveShortcut() {
    if (!this.computeSaveDisabled()) {
      this.saveEdit();
    }
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-editor-view': GrEditorView;
  }
}
