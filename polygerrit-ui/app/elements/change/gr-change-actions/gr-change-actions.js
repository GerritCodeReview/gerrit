// Copyright (C) 2016 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
(function() {
  'use strict';

  // TODO(davido): Add the rest of the change actions.
  var ChangeActions = {
    ABANDON: 'abandon',
    DELETE: '/',
    RESTORE: 'restore',
  };

  // TODO(andybons): Add the rest of the revision actions.
  var RevisionActions = {
    CHERRYPICK: 'cherrypick',
    DELETE: '/',
    PUBLISH: 'publish',
    REBASE: 'rebase',
    SUBMIT: 'submit',
  };

  Polymer({
    is: 'gr-change-actions',

    /**
     * Fired when the change should be reloaded.
     *
     * @event reload-change
     */

    properties: {
      actions: {
        type: Object,
      },
      changeNum: String,
      patchNum: String,
      commitMessage: String,
      _loading: {
        type: Boolean,
        value: true,
      },
      _revisionActions: Object,
    },

    behaviors: [
      Gerrit.RESTClientBehavior,
    ],

    observers: [
      '_actionsChanged(actions, _revisionActions)',
    ],

    reload: function() {
      if (!this.changeNum || !this.patchNum) {
        return Promise.resolve();
      }
      return this.$.actionsXHR.generateRequest().completes;
    },

    _actionsChanged: function(actions, revisionActions) {
      this.hidden = actions.length == 0 && revisionActions.length == 0;
    },

    _computeRevisionActionsPath: function(changeNum, patchNum) {
      return this.changeBaseURL(changeNum, patchNum) + '/actions';
    },

    _getValuesFor: function(obj) {
      return Object.keys(obj).map(function(key) {
        return obj[key];
      });
    },

    _computeActionValues: function(actions, type) {
      var result = [];
      var values = this._getValuesFor(
          type == 'change' ? ChangeActions : RevisionActions);
      for (var a in actions) {
        if (values.indexOf(a) == -1) { continue; }
        actions[a].__key = a;
        actions[a].__type = type;
        result.push(actions[a]);
      }
      return result;
    },

    _computeLoadingLabel: function(action) {
      return {
        'cherrypick': 'Cherry-Picking...',
        'rebase': 'Rebasing...',
        'submit': 'Submitting...',
      }[action];
    },

    _computePrimary: function(actionKey) {
      return actionKey == 'submit';
    },

    _computeButtonClass: function(action) {
      if ([RevisionActions.SUBMIT,
          RevisionActions.PUBLISH].indexOf(action) != -1) {
        return 'primary';
      }
      return '';
    },

    _handleActionTap: function(e) {
      e.preventDefault();
      var el = Polymer.dom(e).rootTarget;
      var key = el.getAttribute('data-action-key');
      var type = el.getAttribute('data-action-type');
      if (type == 'revision') {
        if (key == RevisionActions.REBASE) {
          this._showActionDialog(this.$.confirmRebase);
          return;
        } else if (key == RevisionActions.CHERRYPICK) {
          this._showActionDialog(this.$.confirmCherrypick);
          return;
        }
        this._fireRevisionAction(this._prependSlash(key),
            this._revisionActions[key]);
      } else {
        this._fireChangeAction(this._prependSlash(key), this.actions[key]);
      }
    },

    _prependSlash: function(key) {
      return key == '/' ? key : '/' + key;
    },

    _handleConfirmDialogCancel: function() {
      var dialogEls =
          Polymer.dom(this.root).querySelectorAll('.confirmDialog');
      for (var i = 0; i < dialogEls.length; i++) {
        dialogEls[i].hidden = true;
      }
      this.$.overlay.close();
    },

    _handleRebaseConfirm: function() {
      var payload = {};
      var el = this.$.confirmRebase;
      if (el.clearParent) {
        // There is a subtle but important difference between setting the base
        // to an empty string and omitting it entirely from the payload. An
        // empty string implies that the parent should be cleared and the
        // change should be rebased on top of the target branch. Leaving out
        // the base implies that it should be rebased on top of its current
        // parent.
        payload.base = '';
      } else if (el.base && el.base.length > 0) {
        payload.base = el.base;
      }
      this.$.overlay.close();
      el.hidden = false;
      this._fireRevisionAction('/rebase', this._revisionActions.rebase,
          payload);
    },

    _handleCherrypickConfirm: function() {
      var el = this.$.confirmCherrypick;
      if (!el.branch) {
        // TODO(davido): Fix error handling
        alert('The destination branch can’t be empty.');
        return;
      }
      if (!el.message) {
        alert('The commit message can’t be empty.');
        return;
      }
      this.$.overlay.close();
      el.hidden = false;
      this._fireRevisionAction('/cherrypick',
          this._revisionActions.cherrypick,
          {
            destination: el.branch,
            message: el.message,
          }
      );
    },

    _fireChangeAction: function(endpoint, action) {
      this._send(action.method, {}, endpoint).then(
        function() {
          // We can’t reload a change that was deleted.
          if (endpoint == ChangeActions.DELETE) {
            page.show('/');
          } else {
            this.fire('reload-change', null, {bubbles: false});
          }
        }.bind(this)).catch(function(err) {
          alert('Oops. Something went wrong. Check the console and bug the ' +
              'PolyGerrit team for assistance.');
          throw err;
        });
    },

    _fireRevisionAction: function(endpoint, action, opt_payload) {
      var buttonEl = this.$$('[data-action-key="' + action.__key + '"]');
      buttonEl.setAttribute('loading', true);
      buttonEl.disabled = true;
      function enableButton() {
        buttonEl.removeAttribute('loading');
        buttonEl.disabled = false;
      }

      this._send(action.method, opt_payload, endpoint, true).then(
        function(req) {
          if (action.__key == RevisionActions.CHERRYPICK) {
            page.show(this.changePath(req.response._number));
          } else {
            this.fire('reload-change', null, {bubbles: false});
          }
          enableButton();
        }.bind(this)).catch(function(err) {
          // TODO(andybons): Handle merge conflict (409 status);
          alert('Oops. Something went wrong. Check the console and bug the ' +
              'PolyGerrit team for assistance.');
          enableButton();
          throw err;
        });
    },

    _showActionDialog: function(dialog) {
      dialog.hidden = false;
      this.$.overlay.open();
    },

    _send: function(method, payload, actionEndpoint, revisionAction) {
      var xhr = document.createElement('gr-request');
      this._xhrPromise = xhr.send({
        method: method,
        url: this.changeBaseURL(this.changeNum,
            revisionAction ? this.patchNum : null) + actionEndpoint,
        body: payload,
      });

      return this._xhrPromise;
    },
  });
})();
