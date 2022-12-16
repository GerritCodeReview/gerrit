/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '@polymer/iron-input/iron-input';
import '../../shared/gr-autocomplete/gr-autocomplete';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-dialog/gr-dialog';
import '../../shared/gr-dropdown/gr-dropdown';
import {GrEditAction, GrEditConstants} from '../gr-edit-constants';
import {navigationToken} from '../../core/gr-navigation/gr-navigation';
import {ChangeInfo, RevisionPatchSetNum} from '../../../types/common';
import {GrDialog} from '../../shared/gr-dialog/gr-dialog';
import {
  AutocompleteQuery,
  AutocompleteSuggestion,
  GrAutocomplete,
} from '../../shared/gr-autocomplete/gr-autocomplete';
import {getAppContext} from '../../../services/app-context';
import {fireAlert, fireReload} from '../../../utils/event-util';
import {
  assertIsDefined,
  query as queryUtil,
  queryAll,
} from '../../../utils/common-util';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, html, css} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {BindValueChangeEvent} from '../../../types/events';
import {IronInputElement} from '@polymer/iron-input/iron-input';
import {createEditUrl} from '../../../models/views/edit';
import {resolve} from '../../../models/dependency';
import {modalStyles} from '../../../styles/gr-modal-styles';
import {whenVisible} from '../../../utils/dom-util';
import {throwingErrorCallback} from '../../shared/gr-rest-api-interface/gr-rest-apis/gr-rest-api-helper';

@customElement('gr-edit-controls')
export class GrEditControls extends LitElement {
  // private but used in test
  @query('#newPathIronInput') newPathIronInput?: IronInputElement;

  @query('#modal') modal?: HTMLDialogElement;

  // private but used in test
  @query('#openDialog') openDialog?: GrDialog;

  // private but used in test
  @query('#deleteDialog') deleteDialog?: GrDialog;

  // private but used in test
  @query('#renameDialog') renameDialog?: GrDialog;

  // private but used in test
  @query('#restoreDialog') restoreDialog?: GrDialog;

  @property({type: Object})
  change?: ChangeInfo;

  @property({type: String})
  patchNum?: RevisionPatchSetNum;

  @property({type: Array})
  hiddenActions: string[] = [GrEditConstants.Actions.RESTORE.id];

  // private but used in test
  @state() actions: GrEditAction[] = Object.values(GrEditConstants.Actions);

  // private but used in test
  @state() path = '';

  // private but used in test
  @state() newPath = '';

  private readonly query: AutocompleteQuery = (input: string) =>
    this.queryFiles(input);

  private readonly restApiService = getAppContext().restApiService;

  private readonly getNavigation = resolve(this, navigationToken);

  static override get styles() {
    return [
      sharedStyles,
      modalStyles,
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
      <dialog id="modal" tabindex="-1">
        ${this.renderOpenDialog()} ${this.renderDeleteDialog()}
        ${this.renderRenameDialog()} ${this.renderRestoreDialog()}
      </dialog>
    `;
  }

  private renderAction(action: GrEditAction) {
    return html`
      <gr-button
        id=${action.id}
        class=${this.computeIsInvisible(action.id)}
        link=""
        @click=${this.handleTap}
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
        @confirm=${this.handleOpenConfirm}
        @cancel=${this.handleDialogCancel}
      >
        <div class="header" slot="header">
          Add a new file or open an existing file
        </div>
        <div class="main" slot="main">
          <gr-autocomplete
            placeholder="Enter an existing or new full file path."
            .query=${this.query}
            .text=${this.path}
            @text-changed=${this.handleTextChanged}
          ></gr-autocomplete>
          <div
            id="dragDropArea"
            contenteditable="true"
            @drop=${this.handleDragAndDropUpload}
            @keypress=${this.handleKeyPress}
          >
            <p>Drag and drop a file here</p>
            <p>or</p>
            <p>
              <iron-input>
                <input
                  id="fileUploadInput"
                  type="file"
                  @change=${this.handleFileUploadChanged}
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
        @confirm=${this.handleDeleteConfirm}
        @cancel=${this.handleDialogCancel}
      >
        <div class="header" slot="header">Delete a file from the repo</div>
        <div class="main" slot="main">
          <gr-autocomplete
            placeholder="Enter an existing full file path."
            .query=${this.query}
            .text=${this.path}
            @text-changed=${this.handleTextChanged}
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
        ?disabled=${!this.isValidPath(this.path) ||
        !this.isValidPath(this.newPath)}
        confirm-label="Rename"
        confirm-on-enter=""
        @confirm=${this.handleRenameConfirm}
        @cancel=${this.handleDialogCancel}
      >
        <div class="header" slot="header">Rename a file in the repo</div>
        <div class="main" slot="main">
          <gr-autocomplete
            placeholder="Enter an existing full file path."
            .query=${this.query}
            .text=${this.path}
            @text-changed=${this.handleTextChanged}
          ></gr-autocomplete>
          <iron-input
            id="newPathIronInput"
            .bindValue=${this.newPath}
            @bind-value-changed=${this.handleBindValueChangedNewPath}
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
        @confirm=${this.handleRestoreConfirm}
        @cancel=${this.handleDialogCancel}
      >
        <div class="header" slot="header">Restore this file?</div>
        <div class="main" slot="main">
          <iron-input
            .bindValue=${this.path}
            @bind-value-changed=${this.handleBindValueChangedPath}
          >
            <input ?disabled=${''} />
          </iron-input>
        </div>
      </gr-dialog>
    `;
  }

  private readonly handleTap = (e: Event) => {
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
  };

  openOpenDialog(path?: string) {
    if (path) {
      this.path = path;
    }
    assertIsDefined(this.openDialog, 'openDialog');
    this.showDialog(this.openDialog);
  }

  openDeleteDialog(path?: string) {
    if (path) {
      this.path = path;
    }
    assertIsDefined(this.deleteDialog, 'deleteDialog');
    this.showDialog(this.deleteDialog);
  }

  openRenameDialog(path?: string) {
    if (path) {
      this.path = path;
    }
    assertIsDefined(this.renameDialog, 'renameDialog');
    this.showDialog(this.renameDialog);
  }

  openRestoreDialog(path?: string) {
    assertIsDefined(this.restoreDialog, 'restoreDialog');
    if (path) {
      this.path = path;
    }
    this.showDialog(this.restoreDialog);
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
    assertIsDefined(this.modal, 'modal');

    // Some dialogs may not fire their on-close event when closed in certain
    // ways (e.g. by clicking outside the dialog body). This call prevents
    // multiple dialogs from being shown in the same modal.
    this.hideAllDialogs();

    this.modal.showModal();
    whenVisible(this.modal, () => {
      dialog.classList.toggle('invisible', false);
      const autocomplete = queryUtil<GrAutocomplete>(dialog, 'gr-autocomplete');
      if (autocomplete) {
        autocomplete.focus();
      }
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

    assertIsDefined(this.modal, 'modal');
    this.modal.close();
  }

  private readonly handleDialogCancel = (e: Event) => {
    this.closeDialog(this.getDialogFromEvent(e));
  };

  private readonly handleOpenConfirm = (e: Event) => {
    if (!this.change || !this.path) {
      fireAlert(this, 'You must enter a path.');
      this.closeDialog(this.openDialog);
      return;
    }
    assertIsDefined(this.patchNum, 'patchset number');
    const url = createEditUrl({
      changeNum: this.change._number,
      repo: this.change.project,
      patchNum: this.patchNum,
      editView: {path: this.path},
    });

    this.getNavigation().setUrl(url);
    this.closeDialog(this.getDialogFromEvent(e));
  };

  // private but used in test
  handleUploadConfirm(path: string, fileData: string) {
    if (!this.change || !path || !fileData) {
      fireAlert(this, 'You must enter a path and data.');
      this.closeDialog(this.openDialog);
      return Promise.resolve();
    }
    return this.restApiService
      .saveFileUploadChangeEdit(this.change._number, path, fileData)
      .then(res => {
        if (!res || !res.ok) {
          return;
        }
        this.closeDialog(this.openDialog);
        fireReload(this, true);
      });
  }

  private readonly handleDeleteConfirm = (e: Event) => {
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
  };

  private readonly handleRestoreConfirm = (e: Event) => {
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
  };

  private readonly handleRenameConfirm = (e: Event) => {
    const dialog = this.getDialogFromEvent(e);
    if (!this.change || !this.path || !this.newPath) {
      fireAlert(this, 'You must enter a old path and a new path.');
      this.closeDialog(dialog);
      return;
    }
    this.restApiService
      .renameFileInChangeEdit(this.change._number, this.path, this.newPath)
      .then(res => {
        if (!res || !res.ok) {
          return;
        }
        this.closeDialog(dialog);
        fireReload(this, true);
      });
  };

  private queryFiles(input: string): Promise<AutocompleteSuggestion[]> {
    assertIsDefined(this.change, 'this.change');
    assertIsDefined(this.patchNum, 'this.patchNum');
    return this.restApiService
      .queryChangeFiles(
        this.change._number,
        this.patchNum,
        input,
        throwingErrorCallback
      )
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

  private readonly handleDragAndDropUpload = (e: DragEvent) => {
    e.preventDefault();
    e.stopPropagation();

    if (!e.dataTransfer) return;
    this.fileUpload(e.dataTransfer.files);
  };

  private readonly handleFileUploadChanged = (e: InputEvent) => {
    if (!e.target) return;
    if (!(e.target instanceof HTMLInputElement)) return;
    const input = e.target;
    if (!input.files) return;
    this.fileUpload(input.files);
  };

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

  private readonly handleKeyPress = (e: KeyboardEvent) => {
    e.preventDefault();
    e.stopImmediatePropagation();
  };

  private readonly handleTextChanged = (e: BindValueChangeEvent) => {
    this.path = e.detail.value ?? '';
  };

  private readonly handleBindValueChangedNewPath = (
    e: BindValueChangeEvent
  ) => {
    this.newPath = e.detail.value ?? '';
  };

  private readonly handleBindValueChangedPath = (e: BindValueChangeEvent) => {
    this.path = e.detail.value ?? '';
  };
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-edit-controls': GrEditControls;
  }
}
