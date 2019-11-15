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
(function() {
  'use strict';

  /**
    * @appliesMixin Gerrit.PatchSetMixin
    */
  class GrEditControls extends Polymer.mixinBehaviors( [
    Gerrit.PatchSetBehavior,
  ], Polymer.GestureEventListeners(
      Polymer.LegacyElementMixin(
          Polymer.Element))) {
    static get is() { return 'gr-edit-controls'; }

    static get properties() {
      return {
        change: Object,
        patchNum: String,

        /**
       * TODO(kaspern): by default, the RESTORE action should be hidden in the
       * file-list as it is a per-file action only. Remove this default value
       * when the Actions dictionary is moved to a shared constants file and
       * use the hiddenActions property in the parent component.
       */
        hiddenActions: {
          type: Array,
          value() { return [GrEditConstants.Actions.RESTORE.id]; },
        },

        _actions: {
          type: Array,
          value() { return Object.values(GrEditConstants.Actions); },
        },
        _path: {
          type: String,
          value: '',
        },
        _newPath: {
          type: String,
          value: '',
        },
        _query: {
          type: Function,
          value() {
            return this._queryFiles.bind(this);
          },
        },
      };
    }

    _handleTap(e) {
      e.preventDefault();
      const action = Polymer.dom(e).localTarget.id;
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

    /**
     * @param {string=} opt_path
     */
    openOpenDialog(opt_path) {
      if (opt_path) { this._path = opt_path; }
      return this._showDialog(this.$.openDialog);
    }

    /**
     * @param {string=} opt_path
     */
    openDeleteDialog(opt_path) {
      if (opt_path) { this._path = opt_path; }
      return this._showDialog(this.$.deleteDialog);
    }

    /**
     * @param {string=} opt_path
     */
    openRenameDialog(opt_path) {
      if (opt_path) { this._path = opt_path; }
      return this._showDialog(this.$.renameDialog);
    }

    /**
     * @param {string=} opt_path
     */
    openRestoreDialog(opt_path) {
      if (opt_path) { this._path = opt_path; }
      return this._showDialog(this.$.restoreDialog);
    }

    /**
     * Given a path string, checks that it is a valid file path.
     * @param {string} path
     * @return {boolean}
     */
    _isValidPath(path) {
      // Double negation needed for strict boolean return type.
      return !!path.length && !path.endsWith('/');
    }

    _computeRenameDisabled(path, newPath) {
      return this._isValidPath(path) && this._isValidPath(newPath);
    }

    /**
     * Given a dom event, gets the dialog that lies along this event path.
     * @param {!Event} e
     * @return {!Element|undefined}
     */
    _getDialogFromEvent(e) {
      return Polymer.dom(e).path.find(element => {
        if (!element.classList) { return false; }
        return element.classList.contains('dialog');
      });
    }

    _showDialog(dialog) {
      // Some dialogs may not fire their on-close event when closed in certain
      // ways (e.g. by clicking outside the dialog body). This call prevents
      // multiple dialogs from being shown in the same overlay.
      this._hideAllDialogs();

      return this.$.overlay.open().then(() => {
        dialog.classList.toggle('invisible', false);
        const autocomplete = dialog.querySelector('gr-autocomplete');
        if (autocomplete) { autocomplete.focus(); }
        this.async(() => { this.$.overlay.center(); }, 1);
      });
    }

    _hideAllDialogs() {
      const dialogs = Polymer.dom(this.root).querySelectorAll('.dialog');
      for (const dialog of dialogs) { this._closeDialog(dialog); }
    }

    /**
     * @param {Element|undefined} dialog
     * @param {boolean=} clearInputs
     */
    _closeDialog(dialog, clearInputs) {
      if (!dialog) { return; }

      if (clearInputs) {
        // Dialog may have autocompletes and plain inputs -- as these have
        // different properties representing their bound text, it is easier to
        // just make two separate queries.
        dialog.querySelectorAll('gr-autocomplete')
            .forEach(input => { input.text = ''; });

        dialog.querySelectorAll('iron-input')
            .forEach(input => { input.bindValue = ''; });
      }

      dialog.classList.toggle('invisible', true);
      return this.$.overlay.close();
    }

    _handleDialogCancel(e) {
      this._closeDialog(this._getDialogFromEvent(e));
    }

    _handleOpenConfirm(e) {
      const url = Gerrit.Nav.getEditUrlForDiff(this.change, this._path,
          this.patchNum);
      Gerrit.Nav.navigateToRelativeUrl(url);
      this._closeDialog(this._getDialogFromEvent(e), true);
    }

    _handleDeleteConfirm(e) {
      // Get the dialog before the api call as the event will change during bubbling
      // which will make Polymer.dom(e).path an emtpy array in polymer 2
      const dialog = this._getDialogFromEvent(e);
      this.$.restAPI.deleteFileInChangeEdit(this.change._number, this._path)
          .then(res => {
            if (!res.ok) { return; }
            this._closeDialog(dialog, true);
            Gerrit.Nav.navigateToChange(this.change);
          });
    }

    _handleRestoreConfirm(e) {
      const dialog = this._getDialogFromEvent(e);
      this.$.restAPI.restoreFileInChangeEdit(this.change._number, this._path)
          .then(res => {
            if (!res.ok) { return; }
            this._closeDialog(dialog, true);
            Gerrit.Nav.navigateToChange(this.change);
          });
    }

    _handleRenameConfirm(e) {
      const dialog = this._getDialogFromEvent(e);
      return this.$.restAPI.renameFileInChangeEdit(this.change._number,
          this._path, this._newPath).then(res => {
        if (!res.ok) { return; }
        this._closeDialog(dialog, true);
        Gerrit.Nav.navigateToChange(this.change);
      });
    }

    _queryFiles(input) {
      return this.$.restAPI.queryChangeFiles(this.change._number,
          this.patchNum, input).then(res => res.map(file => {
        return {name: file};
      }));
    }

    _computeIsInvisible(id, hiddenActions) {
      return hiddenActions.includes(id) ? 'invisible' : '';
    }
  }

  customElements.define(GrEditControls.is, GrEditControls);
})();
