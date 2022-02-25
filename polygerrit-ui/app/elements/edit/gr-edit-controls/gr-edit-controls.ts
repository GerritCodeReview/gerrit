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
import '../../../styles/shared-styles';
import {dom, EventApi} from '@polymer/polymer/lib/legacy/polymer.dom';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-edit-controls_html';
import {GrEditAction, GrEditConstants} from '../gr-edit-constants';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {customElement, property} from '@polymer/decorators';
import {ChangeInfo, PatchSetNum} from '../../../types/common';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';
import {GrDialog} from '../../shared/gr-dialog/gr-dialog';
import {
  AutocompleteQuery,
  AutocompleteSuggestion,
} from '../../shared/gr-autocomplete/gr-autocomplete';
import {getAppContext} from '../../../services/app-context';
import {IronInputElement} from '@polymer/iron-input';
import {fireAlert, fireReload} from '../../../utils/event-util';
import {assertIsDefined} from '../../../utils/common-util';

export interface GrEditControls {
  $: {
    newPathIronInput: IronInputElement;
    overlay: GrOverlay;
    openDialog: GrDialog;
    deleteDialog: GrDialog;
    renameDialog: GrDialog;
    restoreDialog: GrDialog;
  };
}

@customElement('gr-edit-controls')
export class GrEditControls extends PolymerElement {
  static get template() {
    return htmlTemplate;
  }

  @property({type: Object})
  change?: ChangeInfo;

  @property({type: String})
  patchNum?: PatchSetNum;

  @property({type: Array})
  hiddenActions: string[] = [GrEditConstants.Actions.RESTORE.id];

  @property({type: Array})
  _actions: GrEditAction[] = Object.values(GrEditConstants.Actions);

  @property({type: String})
  _path = '';

  @property({type: String})
  _newPath = '';

  @property({type: Object})
  _query: AutocompleteQuery;

  private readonly restApiService = getAppContext().restApiService;

  constructor() {
    super();
    this._query = (input: string) => this._queryFiles(input);
  }

  _handleTap(e: Event) {
    e.preventDefault();
    const target = (dom(e) as EventApi).localTarget as Element;
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
      this._path = path;
    }
    return this._showDialog(this.$.openDialog);
  }

  openDeleteDialog(path?: string) {
    if (path) {
      this._path = path;
    }
    return this._showDialog(this.$.deleteDialog);
  }

  openRenameDialog(path?: string) {
    if (path) {
      this._path = path;
    }
    return this._showDialog(this.$.renameDialog);
  }

  openRestoreDialog(path?: string) {
    if (path) {
      this._path = path;
    }
    return this._showDialog(this.$.restoreDialog);
  }

  /**
   * Given a path string, checks that it is a valid file path.
   */
  _isValidPath(path: string) {
    // Double negation needed for strict boolean return type.
    return !!path.length && !path.endsWith('/');
  }

  _computeRenameDisabled(path: string, newPath: string) {
    return this._isValidPath(path) && this._isValidPath(newPath);
  }

  /**
   * Given a dom event, gets the dialog that lies along this event path.
   */
  _getDialogFromEvent(e: Event): GrDialog | undefined {
    return (dom(e) as EventApi).path.find(element => {
      if (!(element instanceof Element)) return false;
      if (!element.classList) return false;
      return element.classList.contains('dialog');
    }) as GrDialog | undefined;
  }

  _showDialog(dialog: GrDialog) {
    // Some dialogs may not fire their on-close event when closed in certain
    // ways (e.g. by clicking outside the dialog body). This call prevents
    // multiple dialogs from being shown in the same overlay.
    this._hideAllDialogs();

    return this.$.overlay.open().then(() => {
      dialog.classList.toggle('invisible', false);
      const autocomplete = dialog.querySelector('gr-autocomplete');
      if (autocomplete) {
        autocomplete.focus();
      }
      setTimeout(() => {
        this.$.overlay.center();
      }, 1);
    });
  }

  _hideAllDialogs() {
    const dialogs = this.root!.querySelectorAll<GrDialog>('.dialog');
    for (const dialog of dialogs) {
      // We set the second param to false, because this function
      // is called by _showDialog which when you open either restore,
      // delete or rename dialogs, it reseted the automatically
      // set input.
      this._closeDialog(dialog, false);
    }
  }

  _closeDialog(dialog?: GrDialog, clearInputs = true) {
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
    this.$.overlay.close();
  }

  _handleDialogCancel(e: Event) {
    this._closeDialog(this._getDialogFromEvent(e));
  }

  _handleOpenConfirm(e: Event) {
    if (!this.change || !this._path) {
      fireAlert(this, 'You must enter a path.');
      this._closeDialog(this.$.openDialog);
      return;
    }
    const url = GerritNav.getEditUrlForDiff(
      this.change,
      this._path,
      this.patchNum
    );
    GerritNav.navigateToRelativeUrl(url);
    this._closeDialog(this._getDialogFromEvent(e));
  }

  _handleUploadConfirm(path: string, fileData: string) {
    if (!this.change || !path || !fileData) {
      fireAlert(this, 'You must enter a path and data.');
      this._closeDialog(this.$.openDialog);
      return Promise.resolve();
    }
    return this.restApiService
      .saveFileUploadChangeEdit(this.change._number, path, fileData)
      .then(res => {
        if (!res || !res.ok) {
          return;
        }
        this._closeDialog(this.$.openDialog);
        fireReload(this, true);
      });
  }

  _handleDeleteConfirm(e: Event) {
    // Get the dialog before the api call as the event will change during bubbling
    // which will make Polymer.dom(e).path an empty array in polymer 2
    const dialog = this._getDialogFromEvent(e);
    if (!this.change || !this._path) {
      fireAlert(this, 'You must enter a path.');
      this._closeDialog(dialog);
      return;
    }
    this.restApiService
      .deleteFileInChangeEdit(this.change._number, this._path)
      .then(res => {
        if (!res || !res.ok) {
          return;
        }
        this._closeDialog(dialog);
        fireReload(this);
      });
  }

  _handleRestoreConfirm(e: Event) {
    const dialog = this._getDialogFromEvent(e);
    if (!this.change || !this._path) {
      fireAlert(this, 'You must enter a path.');
      this._closeDialog(dialog);
      return;
    }
    this.restApiService
      .restoreFileInChangeEdit(this.change._number, this._path)
      .then(res => {
        if (!res || !res.ok) {
          return;
        }
        this._closeDialog(dialog);
        fireReload(this);
      });
  }

  _handleRenameConfirm(e: Event) {
    const dialog = this._getDialogFromEvent(e);
    if (!this.change || !this._path || !this._newPath) {
      fireAlert(this, 'You must enter a old path and a new path.');
      this._closeDialog(dialog);
      return;
    }
    return this.restApiService
      .renameFileInChangeEdit(this.change._number, this._path, this._newPath)
      .then(res => {
        if (!res || !res.ok) {
          return;
        }
        this._closeDialog(dialog);
        fireReload(this, true);
      });
  }

  _queryFiles(input: string): Promise<AutocompleteSuggestion[]> {
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

  _computeIsInvisible(id: string, hiddenActions: string[]) {
    return hiddenActions.includes(id) ? 'invisible' : '';
  }

  _handleDragAndDropUpload(event: DragEvent) {
    event.preventDefault();
    event.stopPropagation();

    if (!event.dataTransfer) return;
    this._fileUpload(event.dataTransfer.files);
  }

  _handleFileUploadChanged(event: InputEvent) {
    if (!event.target) return;
    if (!(event.target instanceof HTMLInputElement)) return;
    const input = event.target;
    if (!input.files) return;
    this._fileUpload(input.files);
  }

  _fileUpload(files: FileList) {
    for (const file of files) {
      if (!file) continue;

      let path = this._path;
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
        this._handleUploadConfirm(path, fileData);
      };
      fr.readAsDataURL(file);
    }
  }

  _handleKeyPress(event: KeyboardEvent) {
    event.preventDefault();
    event.stopImmediatePropagation();
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-edit-controls': GrEditControls;
  }
}
