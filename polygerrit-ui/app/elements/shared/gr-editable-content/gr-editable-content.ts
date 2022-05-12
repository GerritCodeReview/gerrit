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
import '../gr-button/gr-button';
import '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator';
import '../../plugins/gr-endpoint-param/gr-endpoint-param';
import '../../plugins/gr-endpoint-slot/gr-endpoint-slot';
import {fire, fireAlert, fireEvent} from '../../../utils/event-util';
import {getAppContext} from '../../../services/app-context';
import {debounce, DelayedTask} from '../../../utils/async-util';
import {queryAndAssert} from '../../../utils/common-util';
import {IronAutogrowTextareaElement} from '@polymer/iron-autogrow-textarea/iron-autogrow-textarea';
import {Interaction} from '../../../constants/reporting';
import {LitElement, html} from 'lit';
import {customElement, property, state} from 'lit/decorators';
import {sharedStyles} from '../../../styles/shared-styles';
import {css} from 'lit';
import {PropertyValues} from 'lit';
import {BindValueChangeEvent, ValueChangedEvent} from '../../../types/events';
import {nothing} from 'lit';
import {classMap} from 'lit/directives/class-map';
import {when} from 'lit/directives/when';

const RESTORED_MESSAGE = 'Content restored from a previous edit.';
const STORAGE_DEBOUNCE_INTERVAL_MS = 400;

declare global {
  interface HTMLElementTagNameMap {
    'gr-editable-content': GrEditableContent;
  }
  interface HTMLElementEventMap {
    'content-changed': ValueChangedEvent<string>;
    'editing-changed': ValueChangedEvent<boolean>;
  }
}

@customElement('gr-editable-content')
export class GrEditableContent extends LitElement {
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

  @property({type: String})
  content?: string;

  @property({type: Boolean, reflect: true})
  disabled = false;

  @property({
    type: Boolean,
    reflect: true,
  })
  editing = false;

  @property({type: Boolean, attribute: 'remove-zero-width-space'})
  removeZeroWidthSpace?: boolean;

  // If no storage key is provided, content is not stored.
  @property({type: String, attribute: 'storage-key'})
  storageKey?: string;

  @property({type: Boolean, attribute: 'commit-collapsible'})
  commitCollapsible = true;

  @property({type: Boolean, attribute: 'hide-edit-commit-message'})
  hideEditCommitMessage?: boolean;

  /** If false, then the "Show more" button was used to expand. */
  @state() commitCollapsed = true;

  @state() newContent = '';

  private readonly storage = getAppContext().storageService;

  private readonly reporting = getAppContext().reportingService;

  // Tests use this so needs to be non private
  storeTask?: DelayedTask;

  override disconnectedCallback() {
    this.storeTask?.cancel();
    super.disconnectedCallback();
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('editing')) this.editingChanged();
    if (changedProperties.has('newContent')) this.newContentChanged();
    if (changedProperties.has('content')) this.contentChanged();
  }

  static override get styles() {
    return [
      sharedStyles,
      css`
        :host {
          display: block;
        }
        :host([disabled]) iron-autogrow-textarea {
          opacity: 0.5;
        }
        .viewer {
          background-color: var(--view-background-color);
          border: 1px solid var(--view-background-color);
          border-radius: var(--border-radius);
          box-shadow: var(--elevation-level-1);
          padding: var(--spacing-m);
        }
        :host(.collapsed) .viewer,
        .viewer.collapsed {
          max-height: var(--collapsed-max-height, 300px);
          overflow: hidden;
        }
        .editor iron-autogrow-textarea,
        .viewer {
          min-height: 100px;
        }
        .editor iron-autogrow-textarea {
          background-color: var(--view-background-color);
          width: 100%;
          display: block;
          --iron-autogrow-textarea_-_padding: var(--spacing-m);
        }
        .editButtons {
          display: flex;
          justify-content: space-between;
        }
        .show-all-container {
          background-color: var(--view-background-color);
          display: flex;
          justify-content: flex-end;
          border: 1px solid transparent;
          border-top-color: var(--border-color);
          border-radius: 0 0 4px 4px;
          box-shadow: var(--elevation-level-1);
          /* slightly up to cover rounded corner of the commit msg */
          margin-top: calc(-1 * var(--spacing-xs));
          /* To make this bar pop over editor, since editor has relative position.
          */
          position: relative;
        }
        :host([editing]) .show-all-container {
          box-shadow: none;
          border: 1px solid var(--border-color);
        }
        .flex-space {
          flex-grow: 1;
        }
        .show-all-container iron-icon {
          color: inherit;
          --iron-icon-height: 18px;
          --iron-icon-width: 18px;
        }
        .cancel-button {
          margin-right: var(--spacing-l);
        }
        .save-button {
          margin-right: var(--spacing-xs);
        }
        gr-button {
          font-family: var(--font-family);
          line-height: var(--line-height-normal);
          padding: var(--spacing-xs);
        }
      `,
    ];
  }

  override render() {
    return html`
      <gr-endpoint-decorator name="commit-message">
        <gr-endpoint-param
          name="editing"
          .value=${this.editing}
        ></gr-endpoint-param>
        ${this.renderViewer()} ${this.renderEditor()} ${this.renderButtons()}
        <gr-endpoint-slot name="above-actions"></gr-endpoint-slot>
      </gr-endpoint-decorator>
    `;
  }

  private renderViewer() {
    if (this.editing) return;
    return html`
      <div
        class=${classMap({
          viewer: true,
          collapsed: this.commitCollapsed && this.commitCollapsible,
        })}
      >
        <slot></slot>
      </div>
    `;
  }

  private renderEditor() {
    if (!this.editing) return;
    return html`
      <div class="editor">
        <div>
          <iron-autogrow-textarea
            autocomplete="on"
            .bindValue=${this.newContent}
            ?disabled=${this.disabled}
            @bind-value-changed=${(e: BindValueChangeEvent) => {
              this.newContent = e.detail.value;
            }}
          ></iron-autogrow-textarea>
        </div>
      </div>
    `;
  }

  private renderButtons() {
    if (!this.editing && !this.commitCollapsible && this.hideEditCommitMessage)
      return nothing;

    return html`
      <div class="show-all-container">
        ${when(
          this.commitCollapsible && !this.editing,
          () => html`
            <gr-button
              link
              class="show-all-button"
              @click=${this.toggleCommitCollapsed}
            >
              ${when(
                !this.commitCollapsed,
                () => html`
                  <iron-icon icon="gr-icons:expand-less"></iron-icon>
                `
              )}
              ${when(
                this.commitCollapsed,
                () => html`
                  <iron-icon icon="gr-icons:expand-more"></iron-icon>
                `
              )}
              ${this.commitCollapsed ? 'Show all' : 'Show less'}
            </gr-button>
            <div class="flex-space"></div>
          `
        )}
        ${when(
          !this.hideEditCommitMessage,
          () => html`
            <gr-button
              link
              class="edit-commit-message"
              title="Edit commit message"
              @click=${this.handleEditCommitMessage}
              ><iron-icon icon="gr-icons:edit"></iron-icon> Edit</gr-button
            >
          `
        )}
        ${when(
          this.editing,
          () => html` <div class="editButtons">
            <gr-button
              link
              class="cancel-button"
              @click=${this.handleCancel}
              ?disabled=${this.disabled}
              >Cancel</gr-button
            >
            <gr-button
              class="save-button"
              primary=""
              @click=${this.handleSave}
              ?disabled=${this.computeSaveDisabled()}
              >Save</gr-button
            >
          </div>`
        )}
        </div>
      </div>
    `;
  }

  contentChanged() {
    /* A changed content means that either a different change has been loaded
     * or new content was saved. Either way, let's reset the component.
     */
    this.editing = false;
    this.newContent = '';
    fire(this, 'content-changed', {
      value: this.content ?? '',
    });
  }

  focusTextarea() {
    queryAndAssert<IronAutogrowTextareaElement>(
      this,
      'iron-autogrow-textarea'
    ).textarea.focus();
  }

  newContentChanged() {
    if (!this.storageKey) return;
    const storageKey = this.storageKey;

    this.storeTask = debounce(
      this.storeTask,
      () => {
        if (this.newContent.length) {
          this.storage.setEditableContentItem(storageKey, this.newContent);
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

  editingChanged() {
    // This method is for initializing newContent when you start editing.
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
    fire(this, 'editing-changed', {
      value: this.editing,
    });

    if (!this.editing || this.newContent) return;

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
    this.newContent = this.removeZeroWidthSpace
      ? content.replace(/^R=\u200B/gm, 'R=')
      : content;
  }

  computeSaveDisabled(): boolean {
    return (
      this.disabled || !this.newContent || this.content === this.newContent
    );
  }

  handleSave(e: Event) {
    e.preventDefault();
    this.dispatchEvent(
      new CustomEvent('editable-content-save', {
        detail: {content: this.newContent},
        composed: true,
        bubbles: true,
      })
    );
    // It would be nice, if we would set this.newContent = undefined here,
    // but we can only do that when we are sure that the save operation has
    // succeeded.
  }

  handleCancel(e: Event) {
    e.preventDefault();
    this.editing = false;
    fireEvent(this, 'editable-content-cancel');
  }

  toggleCommitCollapsed() {
    this.commitCollapsed = !this.commitCollapsed;
    this.reporting.reportInteraction(Interaction.TOGGLE_SHOW_ALL_BUTTON, {
      sectionName: 'Commit message',
      toState: !this.commitCollapsed ? 'Show all' : 'Show less',
    });
    if (this.commitCollapsed) {
      window.scrollTo(0, 0);
    }
  }

  async handleEditCommitMessage() {
    this.editing = true;
    await this.updateComplete;
    this.focusTextarea();
  }
}
