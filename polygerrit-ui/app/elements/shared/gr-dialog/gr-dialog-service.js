/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
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

  // auto-increment index of the registered dialogs
  let idPosix = 0;
  // singleton instance
  let grDialogService = null;

  class GrDialogService {
    constructor() {
      // singleton
      if (grDialogService) return grDialogService;

      /** A map between dialog id and dialog params */
      this._dialogStore = new Map();

      this._ensureDialogContainer();
    }

    _ensureDialogContainer() {
      this._dialogContainer = document.getElementById('dialogContainer');
      if (!this._dialogContainer) {
        this._dialogContainer = document.createElement('div');
        this._dialogContainer.id = 'dialogContainer';
        // for css ecapsulation, prefer all dialog to be in shadow DOM
        this._dialogContainer.attachShadow({mode: 'open'});
        document.body.appendChild(this._dialogContainer);
      }
    }

    /** Returns if its valid dialog param. To be removed after typescript */
    _isValidDialogParams(dialogParams) {
      return dialogParams && dialogParams.tagName;
    }

    _generateId(dialogParams) {
      return `${dialogParams.tagName}-${idPosix++}`;
    }

    _getDefaultParams() {
      return {
        // set to true if this dialog should after canceled all existing ones
        only: true,
      };
    }

    register(dialogParams) {
      if (!this._isValidDialogParams(dialogParams)) {
        throw new Error('Invalid dialog params');
      }
      const dialogId = this._generateId(dialogParams);
      this._dialogStore.set(
          dialogId,
          {
            dialogParams: Object.assign(this._getDefaultParams(), dialogParams),
            attributes: {},
            listeners: {},
            dialogEl: null,
            dialogStats: {},
          }
      );
      return dialogId;
    }

    _emptyContainer() {
      const container = this._dialogContainer;
      while (container.firstChild) {
        container.removeChild(contaienr.lastChild);
      }
    }

    _createDialog(dialogParams) {
      if (dialogParams.only) {
        this._emptyContainer();
      }

      const dialogElement = document.createElement(dialogParams.tagName);

      // map attributes to the element as well
      if (dialogParams.attributes) {
        Object.keys(dialogParams.attributes).forEach(attr => {
          const attrValue = dialogParams.attributes[attr];
          const attrName = this._getAttrName(attr);
          dialogElement.setAttribute(attrName, attrValue);
        });
      }

      // map listeners to the element
      if (dialogParams.listeners) {
        Object.keys(dialogParams.listeners).forEach(eventName => {
          const listener = dialogParams.listeners[eventName];
          dialogElement.addEventListener(eventName, e => {
            listener(e);
          });
        });
      }

      // This needs to happen after all mappings done
      // so it should have all needed properties to render and process
      this._dialogContainer.shadowRoot.appendChild(dialogElement);

      return dialogElement;
    }

    _getAttrName(attr) {
      // camel to dash
      return attr.replace(/([A-Z])/g, $1 => '-'+$1.toLowerCase());
    }

    _getDialogOptsById(dialogId) {
      const dialogOpts = this._dialogStore.get(dialogId);
      if (!dialogOpts || !dialogOpts.dialogParams
          || !this._isValidDialogParams(dialogOpts.dialogParams)) {
        throw new Error('Invalid dialog!');
      }
      return dialogOpts;
    }

    _afterNextRender(result, timeout = 300) {
      return Promise.race([
        new Promise(resolve => {
          Polymer.dom.flush();
          resolve(result);
        }),
        new Promise((resolve, reject) => {
          setTimeout(
              reject(
                  new Error(`Dialog failed to render within ${timeout} ms.`)
              ),
              timeout
          );
        }),
      ]);
    }

    _async(fn) {
      return new Promise(resolve => {
        setTimeout(() => resolve(fn()), 1);
      });
    }

    /**
     * Opens a dialog and returns a promise with that dialog element.
     *
     * @param {string} dialogId
     * @return {!Promise<?Element>}
     */
    open(dialogId) {
      const dialogOpts = this._getDialogOptsById(dialogId);

      // created
      if (dialogOpts.dialogEl) {
        dialogOpts.dialogEl.open();
      } else {
        const dialogEl = this._createDialog(dialogOpts.dialogParams);
        dialogOpts.dialogEl = dialogEl;
        dialogEl.open();
      }

      return this._async(() => {
        const dialog = dialogOpts.dialogEl;
        if (dialog.resetFocus) dialog.resetFocus();
        return dialog;
      });
    }

    /**
     * Closes a dialog if exists.
     *
     * @param {string} dialogId
     * @param {boolean} removeAfterClose
     * @return {!Promise<null>}
     */
    close(dialogId, removeAfterClose = false) {
      const dialogOpts = this._getDialogOptsById(dialogId);
      if (dialogEl) {
        dialogEl.close();
      }

      if (removeAfterClose) {
        dialogEl.remove();
        dialogOpts.dialogEl = null;
      }

      // add some stats ?

      return this._afterNextRender(null);
    }
  }

  grDialogService = new GrDialogService();

  // export this singleton
  window.grDialogService = grDialogService;
})();
