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
(function(window) {
  'use strict';

  function GrChangeActionsInterface(el) {
    this._el = el;
    this.RevisionActions = el.RevisionActions;
    this.ChangeActions = el.ChangeActions;
    this.ActionType = el.ActionType;
  }

  GrChangeActionsInterface.prototype.addPrimaryActionKey = function(key) {
    if (this._el.primaryActionKeys.indexOf(key) !== -1) { return; }

    this._el.push('primaryActionKeys', key);
  };

  GrChangeActionsInterface.prototype.removePrimaryActionKey = function(key) {
    this._el.primaryActionKeys = this._el.primaryActionKeys.filter(function(k) {
      return k !== key;
    });
  };

  GrChangeActionsInterface.prototype.setActionHidden = function(type, key,
      hidden) {
    return this._el.setActionHidden(type, key, hidden);
  };

  GrChangeActionsInterface.prototype.add = function(type, label) {
    return this._el.addActionButton(type, label);
  };

  GrChangeActionsInterface.prototype.remove = function(key) {
    return this._el.removeActionButton(key);
  };

  GrChangeActionsInterface.prototype.addTapListener = function(key, handler) {
    this._el.addEventListener(key + '-tap', handler);
  };

  GrChangeActionsInterface.prototype.removeTapListener = function(key,
      handler) {
    this._el.removeEventListener(key + '-tap', handler);
  };

  GrChangeActionsInterface.prototype.setLabel = function(key, text) {
    this._el.setActionButtonProp(key, 'label', text);
  };

  GrChangeActionsInterface.prototype.setEnabled = function(key, enabled) {
    this._el.setActionButtonProp(key, 'enabled', enabled);
  };

  window.GrChangeActionsInterface = GrChangeActionsInterface;
})(window);
