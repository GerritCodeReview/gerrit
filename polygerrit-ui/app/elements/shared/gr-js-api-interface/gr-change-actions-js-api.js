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
(function(window) {
  'use strict';

  /**
   * Ensure GrChangeActionsInterface instance has access to gr-change-actions
   * element and retrieve if the interface was created before element.
   *
   * @param {!GrChangeActionsInterface} api
   */
  function ensureEl(api) {
    if (!api._el) {
      const sharedApiElement = document.createElement('gr-js-api-interface');
      setEl(api, sharedApiElement.getElement(
          sharedApiElement.Element.CHANGE_ACTIONS));
    }
  }

  /**
   * Set gr-change-actions element to a GrChangeActionsInterface instance.
   *
   * @param {!GrChangeActionsInterface} api
   * @param {!Element} el gr-change-actions
   */
  function setEl(api, el) {
    if (!el) {
      console.warn('changeActions() is not ready');
      return;
    }
    api._el = el;
    api.RevisionActions = el.RevisionActions;
    api.ChangeActions = el.ChangeActions;
    api.ActionType = el.ActionType;
  }

  function GrChangeActionsInterface(plugin, el) {
    this.plugin = plugin;
    setEl(this, el);
  }

  GrChangeActionsInterface.prototype.addPrimaryActionKey = function(key) {
    ensureEl(this);
    if (this._el.primaryActionKeys.includes(key)) { return; }

    this._el.push('primaryActionKeys', key);
  };

  GrChangeActionsInterface.prototype.removePrimaryActionKey = function(key) {
    ensureEl(this);
    this._el.primaryActionKeys = this._el.primaryActionKeys.filter(k => {
      return k !== key;
    });
  };

  GrChangeActionsInterface.prototype.hideQuickApproveAction = function() {
    ensureEl(this);
    this._el.hideQuickApproveAction();
  };

  GrChangeActionsInterface.prototype.setActionOverflow = function(type, key,
      overflow) {
    ensureEl(this);
    return this._el.setActionOverflow(type, key, overflow);
  };

  GrChangeActionsInterface.prototype.setActionPriority = function(type, key,
      priority) {
    ensureEl(this);
    return this._el.setActionPriority(type, key, priority);
  };

  GrChangeActionsInterface.prototype.setActionHidden = function(type, key,
      hidden) {
    ensureEl(this);
    return this._el.setActionHidden(type, key, hidden);
  };

  GrChangeActionsInterface.prototype.add = function(type, label) {
    ensureEl(this);
    return this._el.addActionButton(type, label);
  };

  GrChangeActionsInterface.prototype.remove = function(key) {
    ensureEl(this);
    return this._el.removeActionButton(key);
  };

  GrChangeActionsInterface.prototype.addTapListener = function(key, handler) {
    ensureEl(this);
    this._el.addEventListener(key + '-tap', handler);
  };

  GrChangeActionsInterface.prototype.removeTapListener = function(key,
      handler) {
    ensureEl(this);
    this._el.removeEventListener(key + '-tap', handler);
  };

  GrChangeActionsInterface.prototype.setLabel = function(key, text) {
    ensureEl(this);
    this._el.setActionButtonProp(key, 'label', text);
  };

  GrChangeActionsInterface.prototype.setTitle = function(key, text) {
    ensureEl(this);
    this._el.setActionButtonProp(key, 'title', text);
  };

  GrChangeActionsInterface.prototype.setEnabled = function(key, enabled) {
    ensureEl(this);
    this._el.setActionButtonProp(key, 'enabled', enabled);
  };

  GrChangeActionsInterface.prototype.setIcon = function(key, icon) {
    ensureEl(this);
    this._el.setActionButtonProp(key, 'icon', icon);
  };

  GrChangeActionsInterface.prototype.getActionDetails = function(action) {
    ensureEl(this);
    return this._el.getActionDetails(action) ||
      this._el.getActionDetails(this.plugin.getPluginName() + '~' + action);
  };

  window.GrChangeActionsInterface = GrChangeActionsInterface;
})(window);
