// Copyright (C) 2017 The Android Open Source Project
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

  function GrProjectActionsInterface(plugin, el) {
    this.plugin = plugin;
    this._el = el;
    this.RevisionActions = el.RevisionActions;
    this.ChangeActions = el.ChangeActions;
    this.ActionType = el.ActionType;
  }

  GrProjectActionsInterface.prototype.addPrimaryActionKey = function(key) {
    if (this._el.primaryActionKeys.includes(key)) { return; }

    this._el.push('primaryActionKeys', key);
  };

  GrProjectActionsInterface.prototype.removePrimaryActionKey = function(key) {
    this._el.primaryActionKeys = this._el.primaryActionKeys.filter(k => {
      return k !== key;
    });
  };

  GrProjectActionsInterface.prototype.setActionOverflow = function(type, key,
      overflow) {
    return this._el.setActionOverflow(type, key, overflow);
  };

  GrProjectActionsInterface.prototype.setActionPriority = function(type, key,
      priority) {
    return this._el.setActionPriority(type, key, priority);
  };

  GrProjectActionsInterface.prototype.setActionHidden = function(type, key,
      hidden) {
    return this._el.setActionHidden(type, key, hidden);
  };

  GrProjectActionsInterface.prototype.add = function(type, label) {
    return this._el.addActionButton(type, label);
  };

  GrProjectActionsInterface.prototype.remove = function(key) {
    return this._el.removeActionButton(key);
  };

  GrProjectActionsInterface.prototype.addTapListener = function(key, handler) {
    this._el.addEventListener(key + '-tap', handler);
  };

  GrProjectActionsInterface.prototype.removeTapListener = function(key,
      handler) {
    this._el.removeEventListener(key + '-tap', handler);
  };

  GrProjectActionsInterface.prototype.setLabel = function(key, text) {
    this._el.setActionButtonProp(key, 'label', text);
  };

  GrProjectActionsInterface.prototype.setEnabled = function(key, enabled) {
    this._el.setActionButtonProp(key, 'enabled', enabled);
  };

  GrProjectActionsInterface.prototype.getActionDetails = function(action) {
    return this._el.getActionDetails(action) ||
      this._el.getActionDetails(this.plugin.getPluginName() + '~' + action);
  };

  window.GrProjectActionsInterface = GrProjectActionsInterface;
})(window);
