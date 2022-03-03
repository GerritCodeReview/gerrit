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

import '@polymer/iron-input/iron-input';
import '../../shared/gr-autocomplete/gr-autocomplete';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-dialog/gr-dialog';
import '../../shared/gr-dropdown/gr-dropdown';
import '../../shared/gr-overlay/gr-overlay';
import {GrEditAction, GrEditConstants} from '../gr-edit-constants';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {ChangeInfo, PatchSetNum} from '../../../types/common';
import {GrDialog} from '../../shared/gr-dialog/gr-dialog';
import {
  AutocompleteQuery,
  AutocompleteSuggestion,
} from '../../shared/gr-autocomplete/gr-autocomplete';
import {getAppContext} from '../../../services/app-context';
import {fireAlert, fireReload} from '../../../utils/event-util';
import {
  assertIsDefined,
  queryAll,
  queryAndAssert,
} from '../../../utils/common-util';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, html, css} from 'lit';
import {customElement, property, state} from 'lit/decorators';
import {BindValueChangeEvent} from '../../../types/events';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';

@customElement('gr-edit-controls')
export class GrEditControls extends LitElement {
  @property({type: Object})
  change?: ChangeInfo;

  @property({type: String})
  patchNum?: PatchSetNum;

  @property({type: Array})
  hiddenActions: string[] = [GrEditConstants.Actions.RESTORE.id];

  // private but used in test
  @state() actions: GrEditAction[] = Object.values(GrEditConstants.Actions);

  // private but used in test
  @state() path = '';

  // private but used in test
  @state() newPath = '';

  @state() private query: AutocompleteQuery;

  private readonly restApiService = getAppContext().restApiService;

  constructor() {
    super();
    this.query = (input: string) => this.queryFiles(input);
  }

  static override get styles() {
    return [
      sharedStyles,
      css`
        :host {
          align-items: center;
          display: flex;
          justify-content: flex-end;
        }
        .invisible {
          display: none;
        }
        gr-button {
          margin-left: var(--spacing-l);
          text-decoration: none;
        }
        gr-dialog {
          width: 50em;
        }
        gr-dialog .main {
          width: 100%;
        }
        gr-dialog .main > iron-input {
          width: 100%;
        }
        input {
          border: 1px solid var(--border-color);
          border-radius: var(--border-radius);
          margin: var(--spacing-m) 0;
          padding: var(--spacing-s);
          width: 100%;
          box-sizing: content-box;
        }
        #fileUploadBrowse {
          margin-left: 0;
        }
        #dragDropArea {
          border: 2px dashed var(--border-color);
          border-radius: var(--border-radius);
          margin-top: var(--spacing-l);
          padding: var(--spacing-xxl) var(--spacing-xxl);
          text-align: center;
        }
        #dragDropArea > p {
          font-weight: var(--font-weight-bold);
          padding: var(--spacing-s);
        }
        @media screen and (max-width: 50em) {
          gr-dialog {
            width: 100vw;
          }
        }
      `,
    ];
  }

  override render() {
    return html`
      ${this.actions.map(action => this.renderAction(action))}
      <gr-overlay id="overlay" with-backdrop="">
        ${this.renderOpenDialog()} ${this.renderDeleteDialog()}
        ${this.renderRenameDialog()} ${this.renderRestoreDialog()}
      </gr-overlay>
    `;
  }

  private renderAction(action: GrEditAction) {
    return html`
      <gr-button
        id="${action.id}"
        class="${this.computeIsInvisible(action.id)}"
        link=""
        @click=${(e: Event) => {
          this.handleTap(e);
        }}
        >${action.label}</gr-button
      >
    `;
  }

  private renderOpenDialog() {
    return html`
      <gr-dialog
        id="openDialog"
        class="invisible dialog"
        ?disabled=${!this.isValidPath(this.path)}
        confirm-label="Confirm"
        confirm-on-enter=""
        @confirm=${(e: Event) => {
          this.handleOpenConfirm(e);
        }}
        @cancel=${(e: Event) => {
          this.handleDialogCancel(e);
        }}
      >
        <div class="header" slot="header">
          Add a new file or open an existing file
        </div>
        <div class="main" slot="main">
          <gr-autocomplete
            placeholder="Enter an existing or new full file path."
            .query=${this.query}
            .text=${this.path}
            @text-changed=${(e: BindValueChangeEvent) => {
              this.handleTextChanged(e);
            }}
          ></gr-autocomplete>
          <div
            id="dragDropArea"
            contenteditable="true"
            @drop=${(e: DragEvent) => {
              this.handleDragAndDropUpload(e);
            }}
            @keypress=${(e: KeyboardEvent) => {
              this.handleKeyPress(e);
            }}
          >
            <p>Drag and drop a file here</p>
            <p>or</p>
            <p>
              <iron-input>
                <input
                  id="fileUploadInput"
                  type="file"
                  @change=${(e: InputEvent) => {
                    this.handleFileUploadChanged(e);
                  }}
                  multiple
                  hidden
                />
              </iron-input>
              <label for="fileUploadInput">
                <gr-button id="fileUploadBrowse">Browse</gr-button>
              </label>
            </p>
          </div>
        </div>
      </gr-dialog>
    `;
  }

  private renderDeleteDialog() {
    return html`
      <gr-dialog
        id="deleteDialog"
        class="invisible dialog"
        ?disabled=${!this.isValidPath(this.path)}
        confirm-label="Delete"
        confirm-on-enter=""
        @confirm=${(e: Event) => {
          this.handleDeleteConfirm(e);
        }}
        @cancel=${(e: Event) => {
          this.handleDialogCancel(e);
        }}
      >
        <div class="header" slot="header">Delete a file from the repo</div>
        <div class="main" slot="main">
          <gr-autocomplete
            placeholder="Enter an existing full file path."
            .query=${this.query}
            .text=${this.path}
            @text-changed=${(e: BindValueChangeEvent) => {
              this.handleTextChanged(e);
            }}
          ></gr-autocomplete>
        </div>
      </gr-dialog>
    `;
  }

  private renderRenameDialog() {
    return html`
      <gr-dialog
        id="renameDialog"
        class="invisible dialog"
        ?disabled=${!this.computeRenameDisabled(this.path, this.newPath)}
        confirm-label="Rename"
        confirm-on-enter=""
        @confirm=${(e: Event) => {
          this.handleRenameConfirm(e);
        }}
        @cancel=${(e: Event) => {
          this.handleDialogCancel(e);
        }}
      >
        <div class="header" slot="header">Rename a file in the repo</div>
        <div class="main" slot="main">
          <gr-autocomplete
            placeholder="Enter an existing full file path."
            .query=${this.query}
            .text=${this.path}
            @text-changed=${(e: BindValueChangeEvent) => {
              this.handleTextChanged(e);
            }}
          ></gr-autocomplete>
          <iron-input
            id="newPathIronInput"
            .bindValue=${this.newPath}
            @bind-value-changed=${(e: BindValueChangeEvent) => {
              this.handleBindValueChangedNewPath(e);
            }}
          >
            <input id="newPathInput" placeholder="Enter the new path." />
          </iron-input>
        </div>
      </gr-dialog>
    `;
  }

  private renderRestoreDialog() {
    return html`
      <gr-dialog
        id="restoreDialog"
        class="invisible dialog"
        confirm-label="Restore"
        confirm-on-enter=""
        @confirm=${(e: Event) => {
          this.handleRestoreConfirm(e);
        }}
        @cancel=${(e: Event) => {
          this.handleDialogCancel(e);
        }}
      >
        <div class="header" slot="header">Restore this file?</div>
        <div class="main" slot="main">
          <iron-input
            ?disabled=""
            .bindValue=${this.path}
            @bind-value-changed=${(e: BindValueChangeEvent) => {
              this.handleBindValueChangedPath(e);
            }}
          >
            <input ?disabled="" />
          </iron-input>
        </div>
      </gr-dialog>
    `;
  }

  private handleTap(e: Event) {
    e.preventDefault();
    const target = e.target as Element;
    const action = target.id;
    switch (action) {
      case GrEditConstants.Actions.OPEN.id:
        this.openOpenDialog();
        return;
      case GrEditConstants.Actions.DELETE.id:
        this.openDeleteDialog();
        return;
      case GrEditConstants.Actions.RENAME.id:
        this.openRenameDialog();
        return;
      case GrEditConstants.Actions.RESTORE.id:
        this.openRestoreDialog();
        return;
    }
  }

  openOpenDialog(path?: string) {
    if (path) {
      this.path = path;
    }
    return this.showDialog(queryAndAssert<GrDialog>(this, '#openDialog'));
  }

  openDeleteDialog(path?: string) {
    if (path) {
      this.path = path;
    }
    return this.showDialog(queryAndAssert<GrDialog>(this, '#deleteDialog'));
  }

  openRenameDialog(path?: string) {
    if (path) {
      this.path = path;
    }
    return this.showDialog(queryAndAssert<GrDialog>(this, '#renameDialog'));
  }

  openRestoreDialog(path?: string) {
    if (path) {
      this.path = path;
    }
    return this.showDialog(queryAndAssert<GrDialog>(this, '#restoreDialog'));
  }

  /**
   * Given a path string, checks that it is a valid file path.
   *
   * private but used in test
   */
  isValidPath(path: string) {
    // Double negation needed for strict boolean return type.
    return !!path.length && !path.endsWith('/');
  }

  computeRenameDisabled(path: string, newPath: string) {
    return this.isValidPath(path) && this.isValidPath(newPath);
  }

  /**
   * Given a dom event, gets the dialog that lies along this event path.
   *
   * private but used in test
   */
  getDialogFromEvent(e: Event): GrDialog | undefined {
    return e.composedPath().find(element => {
      if (!(element instanceof Element)) return false;
      if (!element.classList) return false;
      return element.classList.contains('dialog');
    }) as GrDialog | undefined;
  }

  // private but used in test
  showDialog(dialog: GrDialog) {
    // Some dialogs may not fire their on-close event when closed in certain
    // ways (e.g. by clicking outside the dialog body). This call prevents
    // multiple dialogs from being shown in the same overlay.
    this.hideAllDialogs();

    return queryAndAssert<GrOverlay>(this, '#overlay')
      .open()
      .then(() => {
        dialog.classList.toggle('invisible', false);
        const autocomplete = dialog.querySelector('gr-autocomplete');
        if (autocomplete) {
          autocomplete.focus();
        }
        setTimeout(() => {
          queryAndAssert<GrOverlay>(this, '#overlay').center();
        }, 1);
      });
  }

  // private but used in test
  hideAllDialogs() {
    const dialogs = queryAll<GrDialog>(this, '.dialog');
    for (const dialog of dialogs) {
      // We set the second param to false, because this function
      // is called by showDialog which when you open either restore,
      // delete or rename dialogs, it reseted the automatically
      // set input.
      this.closeDialog(dialog, false);
    }
  }

  // private but used in test
  closeDialog(dialog?: GrDialog, clearInputs = true) {
    if (!dialog) return;

    if (clearInputs) {
      // Dialog may have autocompletes and plain inputs -- as these have
      // different properties representing their bound text, it is easier to
      // just make two separate queries.
      dialog.querySelectorAll('gr-autocomplete').forEach(input => {
        input.text = '';
      });

      dialog.querySelectorAll('iron-input').forEach(input => {
        input.bindValue = '';
      });
    }

    dialog.classList.toggle('invisible', true);
    queryAndAssert<GrOverlay>(this, '#overlay').close();
  }

  private handleDialogCancel(e: Event) {
    this.closeDialog(this.getDialogFromEvent(e));
  }

  private handleOpenConfirm(e: Event) {
    if (!this.change || !this.path) {
      fireAlert(this, 'You must enter a path.');
      this.closeDialog(queryAndAssert<GrDialog>(this, '#openDialog'));
      return;
    }
    const url = GerritNav.getEditUrlForDiff(
      this.change,
      this.path,
      this.patchNum
    );
    GerritNav.navigateToRelativeUrl(url);
    this.closeDialog(this.getDialogFromEvent(e));
  }

  // private but used in test
  handleUploadConfirm(path: string, fileData: string) {
    if (!this.change || !path || !fileData) {
      fireAlert(this, 'You must enter a path and data.');
      this.closeDialog(queryAndAssert<GrDialog>(this, '#openDialog'));
      return Promise.resolve();
    }
    return this.restApiService
      .saveFileUploadChangeEdit(this.change._number, path, fileData)
      .then(res => {
        if (!res || !res.ok) {
          return;
        }
        this.closeDialog(queryAndAssert<GrDialog>(this, '#openDialog'));
        fireReload(this, true);
      });
  }

  private handleDeleteConfirm(e: Event) {
    // Get the dialog before the api call as the event will change during bubbling
    // which will make Polymer.dom(e).path an empty array in polymer 2
    const dialog = this.getDialogFromEvent(e);
    if (!this.change || !this.path) {
      fireAlert(this, 'You must enter a path.');
      this.closeDialog(dialog);
      return;
    }
    this.restApiService
      .deleteFileInChangeEdit(this.change._number, this.path)
      .then(res => {
        if (!res || !res.ok) {
          return;
        }
        this.closeDialog(dialog);
        fireReload(this);
      });
  }

  private handleRestoreConfirm(e: Event) {
    const dialog = this.getDialogFromEvent(e);
    if (!this.change || !this.path) {
      fireAlert(this, 'You must enter a path.');
      this.closeDialog(dialog);
      return;
    }
    this.restApiService
      .restoreFileInChangeEdit(this.change._number, this.path)
      .then(res => {
        if (!res || !res.ok) {
          return;
        }
        this.closeDialog(dialog);
        fireReload(this);
      });
  }

  private handleRenameConfirm(e: Event) {
    const dialog = this.getDialogFromEvent(e);
    if (!this.change || !this.path || !this.newPath) {
      fireAlert(this, 'You must enter a old path and a new path.');
      this.closeDialog(dialog);
      return;
    }
    return this.restApiService
      .renameFileInChangeEdit(this.change._number, this.path, this.newPath)
      .then(res => {
        if (!res || !res.ok) {
          return;
        }
        this.closeDialog(dialog);
        fireReload(this, true);
      });
  }

  private queryFiles(input: string): Promise<AutocompleteSuggestion[]> {
    assertIsDefined(this.change, 'this.change');
    assertIsDefined(this.patchNum, 'this.patchNum');
    return this.restApiService
      .queryChangeFiles(this.change._number, this.patchNum, input)
      .then(res => {
        if (!res)
          throw new Error('Failed to retrieve files. Response not set.');
        return res.map(file => {
          return {name: file};
        });
      });
  }

  private computeIsInvisible(id: string) {
    return this.hiddenActions.includes(id) ? 'invisible' : '';
  }

  private handleDragAndDropUpload(event: DragEvent) {
    event.preventDefault();
    event.stopPropagation();

    if (!event.dataTransfer) return;
    this.fileUpload(event.dataTransfer.files);
  }

  private handleFileUploadChanged(event: InputEvent) {
    if (!event.target) return;
    if (!(event.target instanceof HTMLInputElement)) return;
    const input = event.target;
    if (!input.files) return;
    this.fileUpload(input.files);
  }

  private fileUpload(files: FileList) {
    for (const file of files) {
      if (!file) continue;

      let path = this.path;
      if (!path) {
        path = file.name;
      }

      const fr = new FileReader();
      // TODO(TS): Do we need this line?
      // fr.file = file;
      fr.onload = (fileLoadEvent: ProgressEvent<FileReader>) => {
        if (!fileLoadEvent) return;
        const fileData = fileLoadEvent.target!.result;
        if (typeof fileData !== 'string') return;
        this.handleUploadConfirm(path, fileData);
      };
      fr.readAsDataURL(file);
    }
  }

  private handleKeyPress(event: KeyboardEvent) {
    event.preventDefault();
    event.stopImmediatePropagation();
  }

  private handleTextChanged(e: BindValueChangeEvent) {
    this.path = e.detail.value;
  }

  private handleBindValueChangedNewPath(e: BindValueChangeEvent) {
    this.newPath = e.detail.value;
  }

  private handleBindValueChangedPath(e: BindValueChangeEvent) {
    this.path = e.detail.value;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-edit-controls': GrEditControls;
  }
}
