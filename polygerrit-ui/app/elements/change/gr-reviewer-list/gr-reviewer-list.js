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

  Polymer({
    is: 'gr-reviewer-list',

    properties: {
      change: Object,
      mutable: {
        type: Boolean,
        value: false,
      },
      disabled: {
        type: Boolean,
        value: false,
        reflectToAttribute: true,
      },
      suggestFrom: {
        type: Number,
        value: 3,
      },

      _reviewers: {
        type: Array,
        value: function() { return []; },
      },
      _autocompleteData: {
        type: Array,
        value: function() { return []; },
        observer: '_autocompleteDataChanged',
      },
      _inputVal: {
        type: String,
        value: '',
        observer: '_inputValChanged',
      },
      _inputRequestHandle: Number,
      _inputRequestTimeout: {
        type: Number,
        value: 250,
      },
      _showInput: {
        type: Boolean,
        value: false,
      },
      _hideAutocomplete: {
        type: Boolean,
        value: true,
        observer: '_hideAutocompleteChanged',
      },
      _selectedIndex: {
        type: Number,
        value: 0,
      },
      _boundBodyClickHandler: {
        type: Function,
        value: function() {
          return this._handleBodyClick.bind(this);
        },
      },

      // Used for testing.
      _lastAutocompleteRequest: Object,
      _xhrPromise: Object,
    },

    behaviors: [
      Gerrit.KeyboardShortcutBehavior,
    ],

    observers: [
      '_reviewersChanged(change.reviewers.*, change.owner)',
    ],

    detached: function() {
      this._clearInputRequestHandle();
    },

    _clearInputRequestHandle: function() {
      if (this._inputRequestHandle != null) {
        this.cancelAsync(this._inputRequestHandle);
        this._inputRequestHandle = null;
      }
    },

    _reviewersChanged: function(changeRecord, owner) {
      var result = [];
      var reviewers = changeRecord.base;
      for (var key in reviewers) {
        if (key == 'REVIEWER' || key == 'CC') {
          result = result.concat(reviewers[key]);
        }
      }
      this._reviewers = result.filter(function(reviewer) {
        return reviewer._account_id != owner._account_id;
      });
    },

    _computeCanRemoveReviewer: function(reviewer, mutable) {
      if (!mutable) { return false; }

      for (var i = 0; i < this.change.removable_reviewers.length; i++) {
        if (this.change.removable_reviewers[i]._account_id ==
            reviewer._account_id) {
          return true;
        }
      }
      return false;
    },

    _computeAutocompleteURL: function(change) {
      return '/changes/' + change._number + '/suggest_reviewers';
    },

    _computeAutocompleteParams: function(inputVal) {
      return {
        n: 10,  // Return max 10 results
        q: inputVal,
      };
    },

    _computeSelected: function(index, selectedIndex) {
      return index == selectedIndex;
    },

    _handleResponse: function(e) {
      this._autocompleteData = e.detail.response.filter(function(reviewer) {
        var account = reviewer.account;
        if (!account) { return true; }
        for (var i = 0; i < this._reviewers.length; i++) {
          if (account._account_id == this.change.owner._account_id ||
              account._account_id == this._reviewers[i]._account_id) {
            return false;
          }
        }
        return true;
      }, this);
    },

    _handleBodyClick: function(e) {
      var eventPath = Polymer.dom(e).path;
      for (var i = 0; i < eventPath.length; i++) {
        if (eventPath[i] == this) {
          return;
        }
      }
      this._selectedIndex = -1;
      this._autocompleteData = [];
    },

    _handleRemove: function(e) {
      e.preventDefault();
      var target = Polymer.dom(e).rootTarget;
      var accountID = parseInt(target.getAttribute('data-account-id'), 10);
      this._send('DELETE', this._restEndpoint(accountID)).then(function(req) {
        var reviewers = this.change.reviewers;
        ['REVIEWER', 'CC'].forEach(function(type) {
          reviewers[type] = reviewers[type] || [];
          for (var i = 0; i < reviewers[type].length; i++) {
            if (reviewers[type][i]._account_id == accountID) {
              this.splice('change.reviewers.' + type, i, 1);
              break;
            }
          }
        }, this);
      }.bind(this)).catch(function(err) {
        alert('Oops. Something went wrong. Check the console and bug the ' +
            'PolyGerrit team for assistance.');
        throw err;
      }.bind(this));
    },

    _handleAddTap: function(e) {
      e.preventDefault();
      this._showInput = true;
      this.$.input.focus();
    },

    _handleCancelTap: function(e) {
      e.preventDefault();
      this._cancel();
    },

    _handleMouseEnterItem: function(e) {
      this._selectedIndex =
          parseInt(Polymer.dom(e).rootTarget.getAttribute('data-index'), 10);
    },

    _handleItemTap: function(e) {
      var reviewerEl;
      var eventPath = Polymer.dom(e).path;
      for (var i = 0; i < eventPath.length; i++) {
        var el = eventPath[i];
        if (el.classList && el.classList.contains('reviewer')) {
          reviewerEl = el;
          break;
        }
      }
      this._selectedIndex =
          parseInt(reviewerEl.getAttribute('data-index'), 10);
      this._sendAddRequest();
    },

    _autocompleteDataChanged: function(data) {
      this._hideAutocomplete = data.length == 0;
    },

    _hideAutocompleteChanged: function(hidden) {
      if (hidden) {
        document.body.removeEventListener('click',
            this._boundBodyClickHandler);
        this._selectedIndex = -1;
      } else {
        document.body.addEventListener('click', this._boundBodyClickHandler);
        this._selectedIndex = 0;
      }
    },

    _inputValChanged: function(val) {
      var sendRequest = function() {
        if (this.disabled || val == null || val.trim().length == 0) {
          return;
        }
        if (val.length < this.suggestFrom) {
          this._clearInputRequestHandle();
          this._hideAutocomplete = true;
          this._selectedIndex = -1;
          return;
        }
        this._lastAutocompleteRequest =
            this.$.autocompleteXHR.generateRequest();
      }.bind(this);

      this._clearInputRequestHandle();
      if (this._inputRequestTimeout == 0) {
        sendRequest();
      } else {
        this._inputRequestHandle =
            this.async(sendRequest, this._inputRequestTimeout);
      }
    },

    _handleKey: function(e) {
      if (this._hideAutocomplete) {
        if (e.keyCode == 27) {  // 'esc'
          e.preventDefault();
          this._cancel();
        }
        return;
      }

      switch (e.keyCode) {
        case 38:  // 'up':
          e.preventDefault();
          this._selectedIndex = Math.max(this._selectedIndex - 1, 0);
          break;
        case 40:  // 'down'
          e.preventDefault();
          this._selectedIndex = Math.min(this._selectedIndex + 1,
                                         this._autocompleteData.length - 1);
          break;
        case 27:  // 'esc'
          e.preventDefault();
          this._hideAutocomplete = true;
          break;
        case 13:  // 'enter'
          e.preventDefault();
          this._sendAddRequest();
          break;
      }
    },

    _cancel: function() {
      this._showInput = false;
      this._selectedIndex = 0;
      this._inputVal = '';
      this._autocompleteData = [];
      this.$.addReviewer.focus();
    },

    _sendAddRequest: function() {
      this._clearInputRequestHandle();

      var reviewerID;
      var reviewer = this._autocompleteData[this._selectedIndex];
      if (reviewer.account) {
        reviewerID = reviewer.account._account_id;
      } else if (reviewer.group) {
        reviewerID = reviewer.group.id;
      }
      this._autocompleteData = [];
      this._send('POST', this._restEndpoint(), reviewerID).then(function(req) {
        this.change.reviewers.CC = this.change.reviewers.CC || [];
        req.response.reviewers.forEach(function(r) {
          this.push('change.removable_reviewers', r);
          this.push('change.reviewers.CC', r);
        }, this);
        this._inputVal = '';
        this.$.input.focus();
      }.bind(this)).catch(function(err) {
        // TODO(andybons): Use the message returned by the server.
        alert('Unable to add ' + reviewerID + ' as a reviewer.');
        throw err;
      }.bind(this));
    },

    _send: function(method, url, reviewerID) {
      this.disabled = true;
      var request = document.createElement('gr-request');
      var opts = {
        method: method,
        url: url,
      };
      if (reviewerID) {
        opts.body = {reviewer: reviewerID};
      }
      this._xhrPromise = request.send(opts);
      var enableEl = function() { this.disabled = false; }.bind(this);
      this._xhrPromise.then(enableEl).catch(enableEl);
      return this._xhrPromise;
    },

    _restEndpoint: function(id) {
      var path = '/changes/' + this.change._number + '/reviewers';
      if (id) {
        path += '/' + id;
      }
      return path;
    },
  });
})();
