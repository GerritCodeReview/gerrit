/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
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
import '@polymer/iron-autogrow-textarea/iron-autogrow-textarea';
import '../../../styles/shared-styles';
import '../gr-storage/gr-storage';
import '../gr-button/gr-button';
import {GrStorage} from '../gr-storage/gr-storage';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {customElement, property} from '@polymer/decorators';
import {htmlTemplate} from './gr-editable-content_html';
import {fireAlert, fireEvent} from '../../../utils/event-util';
import {appContext} from '../../../services/app-context';
import {KnownExperimentId} from '../../../services/flags/flags';

const RESTORED_MESSAGE = 'Content restored from a previous edit.';
const STORAGE_DEBOUNCE_INTERVAL_MS = 400;

declare global {
  interface HTMLElementTagNameMap {
    'gr-editable-content': GrEditableContent;
  }
}

const DEBOUNCER_STORE = 'store';

@customElement('gr-editable-content')
export class GrEditableContent extends GestureEventListeners(
  LegacyElementMixin(PolymerElement)
) {
  static get template() {
    return htmlTemplate;
  }

  /**
   * Fired when the save button is pressed.
   *
   * @event editable-content-save
   */

  /**
   * Fired when the cancel button is pressed.
   *
   * @event editable-content-cancel
   */

  /**
   * Fired when content is restored from storage.
   *
   * @event show-alert
   */

  @property({type: String, notify: true, observer: '_contentChanged'})
  content?: string;

  @property({type: Boolean, reflectToAttribute: true})
  disabled = false;

  @property({type: Boolean, observer: '_editingChanged', notify: true})
  editing = false;

  @property({type: Boolean})
  removeZeroWidthSpace?: boolean;

  // If no storage key is provided, content is not stored.
  @property({type: String})
  storageKey?: string;

  /** If false, then the "Show more" button was used to expand. */
  @property({type: Boolean})
  _commitCollapsed = true;

  @property({type: Boolean})
  commitCollapsible = true;

  @property({
    type: Boolean,
    computed:
      '_computeHideShowAllContainer(hideEditCommitMessage, _hideShowAllButton, editing)',
  })
  _hideShowAllContainer = false;

  @property({
    type: Boolean,
    computed: '_computeHideShowAllButton(commitCollapsible, editing)',
  })
  _hideShowAllButton = false;

  @property({type: Boolean})
  hideEditCommitMessage?: boolean;

  @property({
    type: Boolean,
    computed: '_computeSaveDisabled(disabled, content, _newContent)',
  })
  _saveDisabled!: boolean;

  @property({type: String, observer: '_newContentChanged'})
  _newContent?: string;

  @property({type: Boolean})
  _isNewChangeSummaryUiEnabled = false;

  private readonly storage = new GrStorage();

  private readonly flagsService = appContext.flagsService;

  private readonly reporting = appContext.reportingService;

  /** @override */
  ready() {
    super.ready();
    this._isNewChangeSummaryUiEnabled = this.flagsService.isEnabled(
      KnownExperimentId.NEW_CHANGE_SUMMARY_UI
    );
  }

  /** @override */
  detached() {
    this.cancelDebouncer(DEBOUNCER_STORE);
  }

  _contentChanged() {
    /* A changed content means that either a different change has been loaded
     * or new content was saved. Either way, let's reset the component.
     */
    this.editing = false;
    this._newContent = '';
  }

  focusTextarea() {
    this.shadowRoot!.querySelector('iron-autogrow-textarea')!.textarea.focus();
  }

  _newContentChanged(newContent: string) {
    if (!this.storageKey) return;
    const storageKey = this.storageKey;

    this.debounce(
      DEBOUNCER_STORE,
      () => {
        if (newContent.length) {
          this.storage.setEditableContentItem(storageKey, newContent);
        } else {
          // This does not really happen, because we don't clear newContent
          // after saving (see below). So this only occurs when the user clears
          // all the content in the editable textarea. But GrStorage cleans
          // up itself after one day, so we are not so concerned about leaving
          // some garbage behind.
          this.storage.eraseEditableContentItem(storageKey);
        }
      },
      STORAGE_DEBOUNCE_INTERVAL_MS
    );
  }

  _editingChanged(editing: boolean) {
    // This method is for initializing _newContent when you start editing.
    // Restoring content from local storage is not perfect and has
    // some issues:
    //
    // 1. When you start editing in multiple tabs, then we are vulnerable to
    // race conditions between the tabs.
    // 2. The stored content is keyed by revision, so when you upload a new
    // patchset and click "reload" and then click "cancel" on the content-
    // editable, then you won't be able to recover the content anymore.
    //
    // Because of these issues we believe that it is better to only recover
    // content from local storage when you enter editing mode for the first
    // time. Otherwise it is better to just keep the last editing state from
    // the same session.
    if (!editing || this._newContent) return;

    let content;
    if (this.storageKey) {
      const storedContent = this.storage.getEditableContentItem(
        this.storageKey
      );
      if (storedContent?.message) {
        content = storedContent.message;
        fireAlert(this, RESTORED_MESSAGE);
      }
    }
    if (!content) {
      content = this.content || '';
    }

    // TODO(wyatta) switch linkify sequence, see issue 5526.
    this._newContent = this.removeZeroWidthSpace
      ? content.replace(/^R=\u200B/gm, 'R=')
      : content;
  }

  _computeSaveDisabled(
    disabled?: boolean,
    content?: string,
    newContent?: string
  ): boolean {
    return disabled || !newContent || content === newContent;
  }

  _handleSave(e: Event) {
    e.preventDefault();
    this.dispatchEvent(
      new CustomEvent('editable-content-save', {
        detail: {content: this._newContent},
        composed: true,
        bubbles: true,
      })
    );
    // It would be nice, if we would set this._newContent = undefined here,
    // but we can only do that when we are sure that the save operation has
    // succeeded.
  }

  _handleCancel(e: Event) {
    e.preventDefault();
    this.editing = false;
    fireEvent(this, 'editable-content-cancel');
  }

  _computeCollapseText(collapsed: boolean) {
    return collapsed ? 'Show all' : 'Show less';
  }

  _toggleCommitCollapsed() {
    this._commitCollapsed = !this._commitCollapsed;
    this.reporting.reportInteraction('toggle show all button', {
      sectionName: 'Commit message',
      toState: !this._commitCollapsed ? 'Show all' : 'Show less',
    });
    if (this._commitCollapsed) {
      window.scrollTo(0, 0);
    }
  }

  _computeHideShowAllContainer(
    hideEditCommitMessage?: boolean,
    _hideShowAllButton?: boolean,
    editing?: boolean
  ) {
    if (editing) return false;
    return _hideShowAllButton && hideEditCommitMessage;
  }

  _computeHideShowAllButton(commitCollapsible?: boolean, editing?: boolean) {
    return !commitCollapsible || editing;
  }

  _computeCommitMessageCollapsed(collapsed?: boolean, collapsible?: boolean) {
    return collapsible && collapsed;
  }

  _handleEditCommitMessage() {
    this.editing = true;
    this.focusTextarea();
  }
}
