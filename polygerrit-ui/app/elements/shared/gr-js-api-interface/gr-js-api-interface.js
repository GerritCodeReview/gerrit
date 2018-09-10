/**
@license
Copyright (C) 2016 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
import '../../../../@polymer/polymer/polymer-legacy.js';

import '../../../behaviors/base-url-behavior/base-url-behavior.js';
import '../../../behaviors/gr-patch-set-behavior/gr-patch-set-behavior.js';
import '../../core/gr-reporting/gr-reporting.js';
import '../../plugins/gr-admin-api/gr-admin-api.js';
import '../../plugins/gr-attribute-helper/gr-attribute-helper.js';
import '../../plugins/gr-change-metadata-api/gr-change-metadata-api.js';
import '../../plugins/gr-dom-hooks/gr-dom-hooks.js';
import '../../plugins/gr-event-helper/gr-event-helper.js';
import '../../plugins/gr-popup-interface/gr-popup-interface.js';
import '../../plugins/gr-repo-api/gr-repo-api.js';
import '../../plugins/gr-settings-api/gr-settings-api.js';
import '../../plugins/gr-theme-api/gr-theme-api.js';
import '../gr-rest-api-interface/gr-rest-api-interface.js';
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
(function(window) {
  'use strict';

  /**
   * Used to create a context for GrAnnotationActionsInterface.
   * @param {HTMLElement} el The DIV.contentText element to apply the
   *     annotation to using annotateRange.
   * @param {GrDiffLine} line The line object.
   * @param {String} path The file path (eg: /COMMIT_MSG').
   * @param {String} changeNum The Gerrit change number.
   * @param {String} patchNum The Gerrit patch number.
   */
  function GrAnnotationActionsContext(el, line, path, changeNum, patchNum) {
    this._el = el;

    this.line = line;
    this.path = path;
    this.changeNum = parseInt(changeNum);
    this.patchNum = parseInt(patchNum);
  }

  /**
   * Method to add annotations to a line.
   * @param {Number} start The line number where the update starts.
   * @param {Number} end The line number where the update ends.
   * @param {String} cssClass The name of a CSS class created using Gerrit.css.
   * @param {String} side The side of the update. ('left' or 'right')
   */
  GrAnnotationActionsContext.prototype.annotateRange = function(
      start, end, cssClass, side) {
    if (this._el.getAttribute('data-side') == side) {
      GrAnnotation.annotateElement(this._el, start, end, cssClass);
    }
  };

  window.GrAnnotationActionsContext = GrAnnotationActionsContext;
})(window);
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
(function(window) {
  'use strict';

  function GrAnnotationActionsInterface(plugin) {
    this.plugin = plugin;
    // Return this instance when there is an annotatediff event.
    plugin.on('annotatediff', this);

    // Collect all annotation layers instantiated by getLayer. Will be used when
    // notifying their listeners in the notify function.
    this._annotationLayers = [];

    // Default impl is a no-op.
    this._addLayerFunc = annotationActionsContext => {};
  }

  /**
   * Register a function to call to apply annotations. Plugins should use
   * GrAnnotationActionsContext.annotateRange to apply a CSS class to a range
   * within a line.
   * @param {Function<GrAnnotationActionsContext>} addLayerFunc The function
   *     that will be called when the AnnotationLayer is ready to annotate.
   */
  GrAnnotationActionsInterface.prototype.addLayer = function(addLayerFunc) {
    this._addLayerFunc = addLayerFunc;
    return this;
  };

  /**
   * The specified function will be called with a notify function for the plugin
   * to call when it has all required data for annotation. Optional.
   * @param {Function<Function<String, Number, Number, String>>} notifyFunc See
   *     doc of the notify function below to see what it does.
   */
  GrAnnotationActionsInterface.prototype.addNotifier = function(notifyFunc) {
    // Register the notify function with the plugin's function.
    notifyFunc(this.notify.bind(this));
    return this;
  };

  /**
   * Returns a checkbox HTMLElement that can be used to toggle annotations
   * on/off. The checkbox will be initially disabled. Plugins should enable it
   * when data is ready and should add a click handler to toggle CSS on/off.
   *
   * Note1: Calling this method from multiple plugins will only work for the
   *        1st call. It will print an error message for all subsequent calls
   *        and will not invoke their onAttached functions.
   * Note2: This method will be deprecated and eventually removed when
   *        https://bugs.chromium.org/p/gerrit/issues/detail?id=8077 is
   *        implemented.
   *
   * @param {String} checkboxLabel Will be used as the label for the checkbox.
   *     Optional. "Enable" is used if this is not specified.
   * @param {Function<HTMLElement>} onAttached The function that will be called
   *     when the checkbox is attached to the page.
   */
  GrAnnotationActionsInterface.prototype.enableToggleCheckbox = function(
      checkboxLabel, onAttached) {
    this.plugin.hook('annotation-toggler').onAttached(element => {
      if (!element.content.hidden) {
        console.error(
            element.content.id + ' is already enabled. Cannot re-enable.');
        return;
      }
      element.content.removeAttribute('hidden');

      const label = element.content.querySelector('#annotation-label');
      if (checkboxLabel) {
        label.textContent = checkboxLabel;
      } else {
        label.textContent = 'Enable';
      }
      const checkbox = element.content.querySelector('#annotation-checkbox');
      onAttached(checkbox);
    });
    return this;
  };

  /**
   * The notify function will call the listeners of all required annotation
   * layers. Intended to be called by the plugin when all required data for
   * annotation is available.
   * @param {String} path The file path whose listeners should be notified.
   * @param {Number} start The line where the update starts.
   * @param {Number} end The line where the update ends.
   * @param {String} side The side of the update ('left' or 'right').
   */
  GrAnnotationActionsInterface.prototype.notify = function(
      path, startRange, endRange, side) {
    for (const annotationLayer of this._annotationLayers) {
      // Notify only the annotation layer that is associated with the specified
      // path.
      if (annotationLayer._path === path) {
        annotationLayer.notifyListeners(startRange, endRange, side);
        break;
      }
    }
  };

  /**
   * Should be called to register annotation layers by the framework. Not
   * intended to be called by plugins.
   * @param {String} path The file path (eg: /COMMIT_MSG').
   * @param {String} changeNum The Gerrit change number.
   * @param {String} patchNum The Gerrit patch number.
   */
  GrAnnotationActionsInterface.prototype.getLayer = function(
      path, changeNum, patchNum) {
    const annotationLayer = new AnnotationLayer(path, changeNum, patchNum,
                                                this._addLayerFunc);
    this._annotationLayers.push(annotationLayer);
    return annotationLayer;
  };

  /**
   * Used to create an instance of the Annotation Layer interface.
   * @param {String} path The file path (eg: /COMMIT_MSG').
   * @param {String} changeNum The Gerrit change number.
   * @param {String} patchNum The Gerrit patch number.
   * @param {Function<GrAnnotationActionsContext>} addLayerFunc The function
   *     that will be called when the AnnotationLayer is ready to annotate.
   */
  function AnnotationLayer(path, changeNum, patchNum, addLayerFunc) {
    this._path = path;
    this._changeNum = changeNum;
    this._patchNum = patchNum;
    this._addLayerFunc = addLayerFunc;

    this._listeners = [];
  }

  /**
   * Register a listener for layer updates.
   * @param {Function<Number, Number, String>} fn The update handler function.
   *     Should accept as arguments the line numbers for the start and end of
   *     the update and the side as a string.
   */
  AnnotationLayer.prototype.addListener = function(fn) {
    this._listeners.push(fn);
  };

  /**
   * Layer method to add annotations to a line.
   * @param {HTMLElement} el The DIV.contentText element to apply the
   *     annotation to.
   * @param {GrDiffLine} line The line object.
   */
  AnnotationLayer.prototype.annotate = function(el, line) {
    const annotationActionsContext = new GrAnnotationActionsContext(
        el, line, this._path, this._changeNum, this._patchNum);
    this._addLayerFunc(annotationActionsContext);
  };

  /**
   * Notify Layer listeners of changes to annotations.
   * @param {Number} start The line where the update starts.
   * @param {Number} end The line where the update ends.
   * @param {String} side The side of the update. ('left' or 'right')
   */
  AnnotationLayer.prototype.notifyListeners = function(
      startRange, endRange, side) {
    for (const listener of this._listeners) {
      listener(startRange, endRange, side);
    }
  };

  window.GrAnnotationActionsInterface = GrAnnotationActionsInterface;
})(window);
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
   * Ensure GrChangeReplyInterface instance has access to gr-reply-dialog
   * element and retrieve if the interface was created before element.
   * @param {!GrChangeReplyInterfaceOld} api
   */
  function ensureEl(api) {
    if (!api._el) {
      const sharedApiElement = document.createElement('gr-js-api-interface');
      api._el = sharedApiElement.getElement(
          sharedApiElement.Element.REPLY_DIALOG);
    }
  }

  /**
   * @deprecated
   */
  function GrChangeReplyInterfaceOld(el) {
    this._el = el;
  }

  GrChangeReplyInterfaceOld.prototype.getLabelValue = function(label) {
    ensureEl(this);
    return this._el.getLabelValue(label);
  };

  GrChangeReplyInterfaceOld.prototype.setLabelValue = function(label, value) {
    ensureEl(this);
    this._el.setLabelValue(label, value);
  };

  GrChangeReplyInterfaceOld.prototype.send = function(opt_includeComments) {
    ensureEl(this);
    return this._el.send(opt_includeComments);
  };

  function GrChangeReplyInterface(plugin, el) {
    GrChangeReplyInterfaceOld.call(this, el);
    this.plugin = plugin;
    this._hookName = (plugin.getPluginName() || 'test') + '-autogenerated-'
      + String(Math.random()).split('.')[1];
  }
  GrChangeReplyInterface.prototype._hookName = '';
  GrChangeReplyInterface.prototype._hookClass = null;
  GrChangeReplyInterface.prototype._hookPromise = null;

  GrChangeReplyInterface.prototype =
    Object.create(GrChangeReplyInterfaceOld.prototype);
  GrChangeReplyInterface.prototype.constructor = GrChangeReplyInterface;

  GrChangeReplyInterface.prototype.addReplyTextChangedCallback =
    function(handler) {
      this.plugin.hook('reply-text').onAttached(el => {
        if (!el.content) { return; }
        el.content.addEventListener('value-changed', e => {
          handler(e.detail.value);
        });
      });
    };

  GrChangeReplyInterface.prototype.addLabelValuesChangedCallback =
    function(handler) {
      this.plugin.hook('reply-label-scores').onAttached(el => {
        if (!el.content) { return; }

        el.content.addEventListener('labels-changed', e => {
          console.log('labels-changed', e.detail);
          handler(e.detail);
        });
      });
    };

  GrChangeReplyInterface.prototype.showMessage = function(message) {
    return this._el.setPluginMessage(message);
  };

  window.GrChangeReplyInterface = GrChangeReplyInterface;
})(window);

const EventType = {
  HISTORY: 'history',
  LABEL_CHANGE: 'labelchange',
  SHOW_CHANGE: 'showchange',
  SUBMIT_CHANGE: 'submitchange',
  COMMIT_MSG_EDIT: 'commitmsgedit',
  COMMENT: 'comment',
  REVERT: 'revert',
  POST_REVERT: 'postrevert',
  ANNOTATE_DIFF: 'annotatediff',
  ADMIN_MENU_LINKS: 'admin-menu-links',
};

const Element = {
  CHANGE_ACTIONS: 'changeactions',
  REPLY_DIALOG: 'replydialog',
};

Polymer({
  is: 'gr-js-api-interface',

  properties: {
    _elements: {
      type: Object,
      value: {}, // Shared across all instances.
    },
    _eventCallbacks: {
      type: Object,
      value: {}, // Shared across all instances.
    },
  },

  behaviors: [Gerrit.PatchSetBehavior],

  Element,
  EventType,

  handleEvent(type, detail) {
    Gerrit.awaitPluginsLoaded().then(() => {
      switch (type) {
        case EventType.HISTORY:
          this._handleHistory(detail);
          break;
        case EventType.SHOW_CHANGE:
          this._handleShowChange(detail);
          break;
        case EventType.COMMENT:
          this._handleComment(detail);
          break;
        case EventType.LABEL_CHANGE:
          this._handleLabelChange(detail);
          break;
        default:
          console.warn('handleEvent called with unsupported event type:',
              type);
          break;
      }
    });
  },

  addElement(key, el) {
    this._elements[key] = el;
  },

  getElement(key) {
    return this._elements[key];
  },

  addEventCallback(eventName, callback) {
    if (!this._eventCallbacks[eventName]) {
      this._eventCallbacks[eventName] = [];
    }
    this._eventCallbacks[eventName].push(callback);
  },

  canSubmitChange(change, revision) {
    const submitCallbacks = this._getEventCallbacks(EventType.SUBMIT_CHANGE);
    const cancelSubmit = submitCallbacks.some(callback => {
      try {
        return callback(change, revision) === false;
      } catch (err) {
        console.error(err);
      }
      return false;
    });

    return !cancelSubmit;
  },

  _removeEventCallbacks() {
    for (const k in EventType) {
      if (!EventType.hasOwnProperty(k)) { continue; }
      this._eventCallbacks[EventType[k]] = [];
    }
  },

  _handleHistory(detail) {
    for (const cb of this._getEventCallbacks(EventType.HISTORY)) {
      try {
        cb(detail.path);
      } catch (err) {
        console.error(err);
      }
    }
  },

  _handleShowChange(detail) {
    // Note (issue 8221) Shallow clone the change object and add a mergeable
    // getter with deprecation warning. This makes the change detail appear as
    // though SKIP_MERGEABLE was not set, so that plugins that expect it can
    // still access.
    //
    // This clone and getter can be removed after plugins migrate to use
    // info.mergeable.
    const change = Object.assign({
      get mergeable() {
        console.warn('Accessing change.mergeable from SHOW_CHANGE is ' +
            'deprecated! Use info.mergeable instead.');
        return detail.info.mergeable;
      },
    }, detail.change);
    const patchNum = detail.patchNum;
    const info = detail.info;

    let revision;
    for (const rev of Object.values(change.revisions || {})) {
      if (this.patchNumEquals(rev._number, patchNum)) {
        revision = rev;
        break;
      }
    }

    for (const cb of this._getEventCallbacks(EventType.SHOW_CHANGE)) {
      try {
        cb(change, revision, info);
      } catch (err) {
        console.error(err);
      }
    }
  },

  handleCommitMessage(change, msg) {
    for (const cb of this._getEventCallbacks(EventType.COMMIT_MSG_EDIT)) {
      try {
        cb(change, msg);
      } catch (err) {
        console.error(err);
      }
    }
  },

  _handleComment(detail) {
    for (const cb of this._getEventCallbacks(EventType.COMMENT)) {
      try {
        cb(detail.node);
      } catch (err) {
        console.error(err);
      }
    }
  },

  _handleLabelChange(detail) {
    for (const cb of this._getEventCallbacks(EventType.LABEL_CHANGE)) {
      try {
        cb(detail.change);
      } catch (err) {
        console.error(err);
      }
    }
  },

  modifyRevertMsg(change, revertMsg, origMsg) {
    for (const cb of this._getEventCallbacks(EventType.REVERT)) {
      try {
        revertMsg = cb(change, revertMsg, origMsg);
      } catch (err) {
        console.error(err);
      }
    }
    return revertMsg;
  },

  getDiffLayers(path, changeNum, patchNum) {
    const layers = [];
    for (const annotationApi of
         this._getEventCallbacks(EventType.ANNOTATE_DIFF)) {
      try {
        const layer = annotationApi.getLayer(path, changeNum, patchNum);
        layers.push(layer);
      } catch (err) {
        console.error(err);
      }
    }
    return layers;
  },

  getAdminMenuLinks() {
    const links = [];
    for (const adminApi of
        this._getEventCallbacks(EventType.ADMIN_MENU_LINKS)) {
      links.push(...adminApi.getMenuLinks());
    }
    return links;
  },

  getLabelValuesPostRevert(change) {
    let labels = {};
    for (const cb of this._getEventCallbacks(EventType.POST_REVERT)) {
      try {
        labels = cb(change);
      } catch (err) {
        console.error(err);
      }
    }
    return labels;
  },

  _getEventCallbacks(type) {
    return this._eventCallbacks[type] || [];
  },
});
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
(function(window) {
  'use strict';

  function GrPluginEndpoints() {
    this._endpoints = {};
    this._callbacks = {};
  }

  GrPluginEndpoints.prototype.onNewEndpoint = function(endpoint, callback) {
    if (!this._callbacks[endpoint]) {
      this._callbacks[endpoint] = [];
    }
    this._callbacks[endpoint].push(callback);
  };

  GrPluginEndpoints.prototype._getOrCreateModuleInfo = function(plugin,
      endpoint, type, moduleName, domHook) {
    const existingModule = this._endpoints[endpoint].find(info =>
        info.plugin === plugin &&
        info.moduleName === moduleName &&
        info.domHook === domHook
    );
    if (existingModule) {
      return existingModule;
    } else {
      const newModule = {
        moduleName,
        plugin,
        pluginUrl: plugin._url,
        type,
        domHook,
      };
      this._endpoints[endpoint].push(newModule);
      return newModule;
    }
  };

  GrPluginEndpoints.prototype.registerModule = function(plugin, endpoint, type,
      moduleName, domHook) {
    if (!this._endpoints[endpoint]) {
      this._endpoints[endpoint] = [];
    }
    const moduleInfo = this._getOrCreateModuleInfo(plugin, endpoint, type,
        moduleName, domHook);
    if (Gerrit._arePluginsLoaded() && this._callbacks[endpoint]) {
      this._callbacks[endpoint].forEach(callback => callback(moduleInfo));
    }
  };

  /**
   * Get detailed information about modules registered with an extension
   * endpoint.
   * @param {string} name Endpoint name.
   * @param {?{
   *   type: (string|undefined),
   *   moduleName: (string|undefined)
   * }} opt_options
   * @return {!Array<{
   *   moduleName: string,
   *   plugin: Plugin,
   *   pluginUrl: String,
   *   type: EndpointType,
   *   domHook: !Object
   * }>}
   */
  GrPluginEndpoints.prototype.getDetails = function(name, opt_options) {
    const type = opt_options && opt_options.type;
    const moduleName = opt_options && opt_options.moduleName;
    if (!this._endpoints[name]) {
      return [];
    }
    return this._endpoints[name]
        .filter(item => (!type || item.type === type) &&
                    (!moduleName || moduleName == item.moduleName));
  };

  /**
   * Get detailed module names for instantiating at the endpoint.
   * @param {string} name Endpoint name.
   * @param {?{
   *   type: (string|undefined),
   *   moduleName: (string|undefined)
   * }} opt_options
   * @return {!Array<string>}
   */
  GrPluginEndpoints.prototype.getModules = function(name, opt_options) {
    const modulesData = this.getDetails(name, opt_options);
    if (!modulesData.length) {
      return [];
    }
    return modulesData.map(m => m.moduleName);
  };

  /**
   * Get .html plugin URLs with element and module definitions.
   * @param {string} name Endpoint name.
   * @param {?{
   *   type: (string|undefined),
   *   moduleName: (string|undefined)
   * }} opt_options
   * @return {!Array<!URL>}
   */
  GrPluginEndpoints.prototype.getPlugins = function(name, opt_options) {
    const modulesData =
          this.getDetails(name, opt_options).filter(
              data => data.pluginUrl.pathname.includes('.html'));
    if (!modulesData.length) {
      return [];
    }
    return Array.from(new Set(modulesData.map(m => m.pluginUrl)));
  };

  window.GrPluginEndpoints = GrPluginEndpoints;
})(window);
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

  function GrPluginActionContext(plugin, action, change, revision) {
    this.action = action;
    this.plugin = plugin;
    this.change = change;
    this.revision = revision;
    this._popups = [];
  }

  GrPluginActionContext.prototype.popup = function(element) {
    this._popups.push(this.plugin.deprecated.popup(element));
  };

  GrPluginActionContext.prototype.hide = function() {
    for (const popupApi of this._popups) {
      popupApi.close();
    }
    this._popups.splice(0);
  };

  GrPluginActionContext.prototype.refresh = function() {
    window.location.reload();
  };

  GrPluginActionContext.prototype.textfield = function() {
    return document.createElement('paper-input');
  };

  GrPluginActionContext.prototype.br = function() {
    return document.createElement('br');
  };

  GrPluginActionContext.prototype.msg = function(text) {
    const label = document.createElement('gr-label');
    Polymer.dom(label).appendChild(document.createTextNode(text));
    return label;
  };

  GrPluginActionContext.prototype.div = function(...els) {
    const div = document.createElement('div');
    for (const el of els) {
      Polymer.dom(div).appendChild(el);
    }
    return div;
  };

  GrPluginActionContext.prototype.button = function(label, callbacks) {
    const onClick = callbacks && callbacks.onclick;
    const button = document.createElement('gr-button');
    Polymer.dom(button).appendChild(document.createTextNode(label));
    if (onClick) {
      this.plugin.eventHelper(button).onTap(onClick);
    }
    return button;
  };

  GrPluginActionContext.prototype.checkbox = function() {
    const checkbox = document.createElement('input');
    checkbox.type = 'checkbox';
    return checkbox;
  };

  GrPluginActionContext.prototype.label = function(checkbox, title) {
    return this.div(checkbox, this.msg(title));
  };

  GrPluginActionContext.prototype.prependLabel = function(title, checkbox) {
    return this.label(checkbox, title);
  };

  GrPluginActionContext.prototype.call = function(payload, onSuccess) {
    if (!this.action.__url) {
      console.warn(`Unable to ${this.action.method} to ${this.action.__key}!`);
      return;
    }
    this.plugin.restApi()
        .send(this.action.method, this.action.__url, payload)
        .then(onSuccess)
        .catch(error => {
          document.dispatchEvent(new CustomEvent('show-alert', {
            detail: {
              message: `Plugin network error: ${error}`,
            },
          }));
        });
  };

  window.GrPluginActionContext = GrPluginActionContext;
})(window);
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
(function(window) {
  'use strict';

  let restApi;

  function getRestApi() {
    if (!restApi) {
      restApi = document.createElement('gr-rest-api-interface');
    }
    return restApi;
  }

  function GrPluginRestApi(opt_prefix) {
    this.opt_prefix = opt_prefix || '';
  }

  GrPluginRestApi.prototype.getLoggedIn = function() {
    return getRestApi().getLoggedIn();
  };

  GrPluginRestApi.prototype.getVersion = function() {
    return getRestApi().getVersion();
  };

  /**
   * Fetch and return native browser REST API Response.
   * @param {string} method HTTP Method (GET, POST, etc)
   * @param {string} url URL without base path or plugin prefix
   * @param {Object=} payload Respected for POST and PUT only.
   * @param {?function(?Response, string=)=} opt_errFn
   *    passed as null sometimes.
   * @return {!Promise}
   */
  GrPluginRestApi.prototype.fetch = function(method, url, opt_payload,
      opt_errFn) {
    return getRestApi().send(method, this.opt_prefix + url, opt_payload,
        opt_errFn);
  };

  /**
   * Fetch and parse REST API response, if request succeeds.
   * @param {string} method HTTP Method (GET, POST, etc)
   * @param {string} url URL without base path or plugin prefix
   * @param {Object=} payload Respected for POST and PUT only.
   * @param {?function(?Response, string=)=} opt_errFn
   *    passed as null sometimes.
   * @return {!Promise} resolves on success, rejects on error.
   */
  GrPluginRestApi.prototype.send = function(method, url, opt_payload,
      opt_errFn) {
    return this.fetch(method, url, opt_payload, opt_errFn).then(response => {
      if (response.status < 200 || response.status >= 300) {
        return response.text().then(text => {
          if (text) {
            return Promise.reject(text);
          } else {
            return Promise.reject(response.status);
          }
        });
      } else {
        return getRestApi().getResponseObject(response);
      }
    });
  };

  /**
   * @param {string} url URL without base path or plugin prefix
   * @return {!Promise} resolves on success, rejects on error.
   */
  GrPluginRestApi.prototype.get = function(url) {
    return this.send('GET', url);
  };

  /**
   * @param {string} url URL without base path or plugin prefix
   * @return {!Promise} resolves on success, rejects on error.
   */
  GrPluginRestApi.prototype.post = function(url, opt_payload) {
    return this.send('POST', url, opt_payload);
  };

  /**
   * @param {string} url URL without base path or plugin prefix
   * @return {!Promise} resolves on success, rejects on error.
   */
  GrPluginRestApi.prototype.put = function(url, opt_payload) {
    return this.send('PUT', url, opt_payload);
  };

  /**
   * @param {string} url URL without base path or plugin prefix
   * @return {!Promise} resolves on 204, rejects on error.
   */
  GrPluginRestApi.prototype.delete = function(url) {
    return this.fetch('DELETE', url).then(response => {
      if (response.status !== 204) {
        return response.text().then(text => {
          if (text) {
            return Promise.reject(text);
          } else {
            return Promise.reject(response.status);
          }
        });
      }
      return response;
    });
  };

  window.GrPluginRestApi = GrPluginRestApi;
})(window);
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
   * Hash of loaded and installed plugins, name to Plugin object.
   */
  const _plugins = {};

  /**
   * Array of plugin URLs to be loaded, name to url.
   */
  let _pluginsPending = {};

  let _pluginsInstalled = [];

  let _pluginsPendingCount = -1;

  const PRELOADED_PROTOCOL = 'preloaded:';

  const UNKNOWN_PLUGIN = 'unknown';

  const PANEL_ENDPOINTS_MAPPING = {
    CHANGE_SCREEN_BELOW_COMMIT_INFO_BLOCK: 'change-view-integration',
    CHANGE_SCREEN_BELOW_CHANGE_INFO_BLOCK: 'change-metadata-item',
  };

  const PLUGIN_LOADING_TIMEOUT_MS = 10000;

  let _restAPI;

  const getRestAPI = () => {
    if (!_restAPI) {
      _restAPI = document.createElement('gr-rest-api-interface');
    }
    return _restAPI;
  };

  let _reporting;
  const getReporting = () => {
    if (!_reporting) {
      _reporting = document.createElement('gr-reporting');
    }
    return _reporting;
  };

  // TODO (viktard): deprecate in favor of GrPluginRestApi.
  function send(method, url, opt_callback, opt_payload) {
    return getRestAPI().send(method, url, opt_payload).then(response => {
      if (response.status < 200 || response.status >= 300) {
        return response.text().then(text => {
          if (text) {
            return Promise.reject(text);
          } else {
            return Promise.reject(response.status);
          }
        });
      } else {
        return getRestAPI().getResponseObject(response);
      }
    }).then(response => {
      if (opt_callback) {
        opt_callback(response);
      }
      return response;
    });
  }

  const API_VERSION = '0.1';

  /**
   * Plugin-provided custom components can affect content in extension
   * points using one of following methods:
   * - DECORATE: custom component is set with `content` attribute and may
   *   decorate (e.g. style) DOM element.
   * - REPLACE: contents of extension point are replaced with the custom
   *   component.
   * - STYLE: custom component is a shared styles module that is inserted
   *   into the extension point.
   */
  const EndpointType = {
    DECORATE: 'decorate',
    REPLACE: 'replace',
    STYLE: 'style',
  };

  // GWT JSNI uses $wnd to refer to window.
  // http://www.gwtproject.org/doc/latest/DevGuideCodingBasicsJSNI.html
  window.$wnd = window;

  function flushPreinstalls() {
    if (window.Gerrit.flushPreinstalls) {
      window.Gerrit.flushPreinstalls();
    }
  }

  function installPreloadedPlugins() {
    if (!Gerrit._preloadedPlugins) { return; }
    for (const name in Gerrit._preloadedPlugins) {
      if (!Gerrit._preloadedPlugins.hasOwnProperty(name)) { continue; }
      const callback = Gerrit._preloadedPlugins[name];
      Gerrit.install(callback, API_VERSION, PRELOADED_PROTOCOL + name);
    }
  }

  function getPluginNameFromUrl(url) {
    if (!(url instanceof URL)) {
      try {
        url = new URL(url);
      } catch (e) {
        console.warn(e);
        return null;
      }
    }
    if (url.protocol === PRELOADED_PROTOCOL) {
      return url.pathname;
    }
    const base = Gerrit.BaseUrlBehavior.getBaseUrl();
    const pathname = url.pathname.replace(base, '');
    // Site theme is server from predefined path.
    if (pathname === '/static/gerrit-theme.html') {
      return 'gerrit-theme';
    } else if (!pathname.startsWith('/plugins')) {
      console.warn('Plugin not being loaded from /plugins base path:',
          url.href, '— Unable to determine name.');
      return;
    }
    // Pathname should normally look like this:
    // /plugins/PLUGINNAME/static/SCRIPTNAME.html
    // Or, for app/samples:
    // /plugins/PLUGINNAME.html
    return pathname.split('/')[2].split('.')[0];
  }

  function Plugin(opt_url) {
    this._domHooks = new GrDomHooksManager(this);

    if (!opt_url) {
      console.warn('Plugin not being loaded from /plugins base path.',
          'Unable to determine name.');
      return;
    }
    this.deprecated = {
      _loadedGwt: deprecatedAPI._loadedGwt.bind(this),
      install: deprecatedAPI.install.bind(this),
      onAction: deprecatedAPI.onAction.bind(this),
      panel: deprecatedAPI.panel.bind(this),
      popup: deprecatedAPI.popup.bind(this),
      screen: deprecatedAPI.screen.bind(this),
      settingsScreen: deprecatedAPI.settingsScreen.bind(this),
    };

    this._url = new URL(opt_url);
    this._name = getPluginNameFromUrl(this._url);
    if (this._url.protocol === PRELOADED_PROTOCOL) {
      // Original plugin URL is used in plugin assets URLs calculation.
      const assetsBaseUrl = window.ASSETS_PATH ||
          (window.location.origin + Gerrit.BaseUrlBehavior.getBaseUrl());
      this._url = new URL(assetsBaseUrl + '/plugins/' + this._name +
          '/static/' + this._name + '.js');
    }
  }

  Plugin._sharedAPIElement = document.createElement('gr-js-api-interface');

  Plugin.prototype._name = '';

  Plugin.prototype.getPluginName = function() {
    return this._name;
  };

  Plugin.prototype.registerStyleModule = function(endpointName, moduleName) {
    Gerrit._endpoints.registerModule(
        this, endpointName, EndpointType.STYLE, moduleName);
  };

  Plugin.prototype.registerCustomComponent = function(
      endpointName, opt_moduleName, opt_options) {
    const type = opt_options && opt_options.replace ?
          EndpointType.REPLACE : EndpointType.DECORATE;
    const hook = this._domHooks.getDomHook(endpointName, opt_moduleName);
    const moduleName = opt_moduleName || hook.getModuleName();
    Gerrit._endpoints.registerModule(
        this, endpointName, type, moduleName, hook);
    return hook.getPublicAPI();
  };

  /**
   * Returns instance of DOM hook API for endpoint. Creates a placeholder
   * element for the first call.
   */
  Plugin.prototype.hook = function(endpointName, opt_options) {
    return this.registerCustomComponent(endpointName, undefined, opt_options);
  };

  Plugin.prototype.getServerInfo = function() {
    return document.createElement('gr-rest-api-interface').getConfig();
  };

  Plugin.prototype.on = function(eventName, callback) {
    Plugin._sharedAPIElement.addEventCallback(eventName, callback);
  };

  Plugin.prototype.url = function(opt_path) {
    const base = Gerrit.BaseUrlBehavior.getBaseUrl();
    return this._url.origin + base + '/plugins/' +
        this._name + (opt_path || '/');
  };

  Plugin.prototype.screenUrl = function(opt_screenName) {
    const origin = this._url.origin;
    const base = Gerrit.BaseUrlBehavior.getBaseUrl();
    const tokenPart = opt_screenName ? '/' + opt_screenName : '';
    return `${origin}${base}/x/${this.getPluginName()}${tokenPart}`;
  };

  Plugin.prototype._send = function(method, url, opt_callback, opt_payload) {
    return send(method, this.url(url), opt_callback, opt_payload);
  };

  Plugin.prototype.get = function(url, opt_callback) {
    console.warn('.get() is deprecated! Use .restApi().get()');
    return this._send('GET', url, opt_callback);
  };

  Plugin.prototype.post = function(url, payload, opt_callback) {
    console.warn('.post() is deprecated! Use .restApi().post()');
    return this._send('POST', url, opt_callback, payload);
  };

  Plugin.prototype.put = function(url, payload, opt_callback) {
    console.warn('.put() is deprecated! Use .restApi().put()');
    return this._send('PUT', url, opt_callback, payload);
  };

  Plugin.prototype.delete = function(url, opt_callback) {
    return Gerrit.delete(this.url(url), opt_callback);
  };

  Plugin.prototype.annotationApi = function() {
    return new GrAnnotationActionsInterface(this);
  };

  Plugin.prototype.changeActions = function() {
    return new GrChangeActionsInterface(this,
      Plugin._sharedAPIElement.getElement(
          Plugin._sharedAPIElement.Element.CHANGE_ACTIONS));
  };

  Plugin.prototype.changeReply = function() {
    return new GrChangeReplyInterface(this,
      Plugin._sharedAPIElement.getElement(
          Plugin._sharedAPIElement.Element.REPLY_DIALOG));
  };

  Plugin.prototype.changeView = function() {
    return new GrChangeViewApi(this);
  };

  Plugin.prototype.theme = function() {
    return new GrThemeApi(this);
  };

  Plugin.prototype.project = function() {
    return new GrRepoApi(this);
  };

  Plugin.prototype.changeMetadata = function() {
    return new GrChangeMetadataApi(this);
  };

  Plugin.prototype.admin = function() {
    return new GrAdminApi(this);
  };

  Plugin.prototype.settings = function() {
    return new GrSettingsApi(this);
  };

  /**
   * To make REST requests for plugin-provided endpoints, use
   * @example
   * const pluginRestApi = plugin.restApi(plugin.url());
   *
   * @param {string} Base url for subsequent .get(), .post() etc requests.
   */
  Plugin.prototype.restApi = function(opt_prefix) {
    return new GrPluginRestApi(opt_prefix);
  };

  Plugin.prototype.attributeHelper = function(element) {
    return new GrAttributeHelper(element);
  };

  Plugin.prototype.eventHelper = function(element) {
    return new GrEventHelper(element);
  };

  Plugin.prototype.popup = function(moduleName) {
    if (typeof moduleName !== 'string') {
      console.error('.popup(element) deprecated, use .popup(moduleName)!');
      return;
    }
    const api = new GrPopupInterface(this, moduleName);
    return api.open();
  };

  Plugin.prototype.panel = function() {
    console.error('.panel() is deprecated! ' +
        'Use registerCustomComponent() instead.');
  };

  Plugin.prototype.settingsScreen = function() {
    console.error('.settingsScreen() is deprecated! ' +
        'Use .settings() instead.');
  };

  Plugin.prototype.screen = function(screenName, opt_moduleName) {
    if (opt_moduleName && typeof opt_moduleName !== 'string') {
      console.error('.screen(pattern, callback) deprecated, use ' +
          '.screen(screenName, opt_moduleName)!');
      return;
    }
    return this.registerCustomComponent(
        Gerrit._getPluginScreenName(this.getPluginName(), screenName),
        opt_moduleName);
  };

  const deprecatedAPI = {
    _loadedGwt: ()=> {},

    install() {
      console.log('Installing deprecated APIs is deprecated!');
      for (const method in this.deprecated) {
        if (method === 'install') continue;
        this[method] = this.deprecated[method];
      }
    },

    popup(el) {
      console.warn('plugin.deprecated.popup() is deprecated, ' +
          'use plugin.popup() insted!');
      if (!el) {
        throw new Error('Popup contents not found');
      }
      const api = new GrPopupInterface(this);
      api.open().then(api => api._getElement().appendChild(el));
      return api;
    },

    onAction(type, action, callback) {
      console.warn('plugin.deprecated.onAction() is deprecated,' +
          ' use plugin.changeActions() instead!');
      if (type !== 'change' && type !== 'revision') {
        console.warn(`${type} actions are not supported.`);
        return;
      }
      this.on('showchange', (change, revision) => {
        const details = this.changeActions().getActionDetails(action);
        if (!details) {
          console.warn(
              `${this.getPluginName()} onAction error: ${action} not found!`);
          return;
        }
        this.changeActions().addTapListener(details.__key, () => {
          callback(new GrPluginActionContext(this, details, change, revision));
        });
      });
    },

    screen(pattern, callback) {
      console.warn('plugin.deprecated.screen is deprecated,' +
          ' use plugin.screen instead!');
      if (pattern instanceof RegExp) {
        console.error('deprecated.screen() does not support RegExp. ' +
            'Please use strings for patterns.');
        return;
      }
      this.hook(Gerrit._getPluginScreenName(this.getPluginName(), pattern))
          .onAttached(el => {
            el.style.display = 'none';
            callback({
              body: el,
              token: el.token,
              onUnload: () => {},
              setTitle: () => {},
              setWindowTitle: () => {},
              show: () => {
                el.style.display = 'initial';
              },
            });
          });
    },

    settingsScreen(path, menu, callback) {
      console.warn('.settingsScreen() is deprecated! Use .settings() instead.');
      const hook = this.settings()
          .title(menu)
          .token(path)
          .module('div')
          .build();
      hook.onAttached(el => {
        el.style.display = 'none';
        const body = el.querySelector('div');
        callback({
          body,
          onUnload: () => {},
          setTitle: () => {},
          setWindowTitle: () => {},
          show: () => {
            el.style.display = 'initial';
          },
        });
      });
    },

    panel(extensionpoint, callback) {
      console.warn('.panel() is deprecated! ' +
          'Use registerCustomComponent() instead.');
      const endpoint = PANEL_ENDPOINTS_MAPPING[extensionpoint];
      if (!endpoint) {
        console.warn(`.panel ${extensionpoint} not supported!`);
        return;
      }
      this.hook(endpoint).onAttached(el => callback({
        body: el,
        p: {
          CHANGE_INFO: el.change,
          REVISION_INFO: el.revision,
        },
        onUnload: () => {},
      }));
    },
  };

  flushPreinstalls();

  const Gerrit = window.Gerrit || {};

  let _resolveAllPluginsLoaded = null;
  let _allPluginsPromise = null;

  Gerrit._endpoints = new GrPluginEndpoints();

  // Provide reset plugins function to clear installed plugins between tests.
  const app = document.querySelector('#app');
  if (!app) {
    // No gr-app found (running tests)
    Gerrit._installPreloadedPlugins = installPreloadedPlugins;
    Gerrit._flushPreinstalls = flushPreinstalls;
    Gerrit._resetPlugins = () => {
      _allPluginsPromise = null;
      _pluginsInstalled = [];
      _pluginsPending = {};
      _pluginsPendingCount = -1;
      _reporting = null;
      _resolveAllPluginsLoaded = null;
      _restAPI = null;
      Gerrit._endpoints = new GrPluginEndpoints();
      for (const k of Object.keys(_plugins)) {
        delete _plugins[k];
      }
    };
  }

  Gerrit.getPluginName = function() {
    console.warn('Gerrit.getPluginName is not supported in PolyGerrit.',
        'Please use plugin.getPluginName() instead.');
  };

  Gerrit.css = function(rulesStr) {
    if (!Gerrit._customStyleSheet) {
      const styleEl = document.createElement('style');
      document.head.appendChild(styleEl);
      Gerrit._customStyleSheet = styleEl.sheet;
    }

    const name = '__pg_js_api_class_' +
        Gerrit._customStyleSheet.cssRules.length;
    Gerrit._customStyleSheet.insertRule('.' + name + '{' + rulesStr + '}', 0);
    return name;
  };

  Gerrit.install = function(callback, opt_version, opt_src) {
    // HTML import polyfill adds __importElement pointing to the import tag.
    const script = document.currentScript &&
        (document.currentScript.__importElement || document.currentScript);
    const src = opt_src || (script && (script.src || script.baseURI));
    const name = getPluginNameFromUrl(src);

    if (opt_version && opt_version !== API_VERSION) {
      Gerrit._pluginInstallError(`Plugin ${name} install error: only version ` +
          API_VERSION + ' is supported in PolyGerrit. ' + opt_version +
          ' was given.');
      return;
    }

    const existingPlugin = _plugins[name];
    const plugin = existingPlugin || new Plugin(src);
    try {
      callback(plugin);
      if (name) {
        _plugins[name] = plugin;
      }
      if (!existingPlugin) {
        Gerrit._pluginInstalled(src);
      }
    } catch (e) {
      Gerrit._pluginInstallError(`${e.name}: ${e.message}`);
    }
  };

  Gerrit.getLoggedIn = function() {
    console.warn('Gerrit.getLoggedIn() is deprecated! ' +
        'Use plugin.restApi().getLoggedIn()');
    return document.createElement('gr-rest-api-interface').getLoggedIn();
  };

  Gerrit.get = function(url, callback) {
    console.warn('.get() is deprecated! Use plugin.restApi().get()');
    send('GET', url, callback);
  };

  Gerrit.post = function(url, payload, callback) {
    console.warn('.post() is deprecated! Use plugin.restApi().post()');
    send('POST', url, callback, payload);
  };

  Gerrit.put = function(url, payload, callback) {
    console.warn('.put() is deprecated! Use plugin.restApi().put()');
    send('PUT', url, callback, payload);
  };

  Gerrit.delete = function(url, opt_callback) {
    console.warn('.delete() is deprecated! Use plugin.restApi().delete()');
    return getRestAPI().send('DELETE', url).then(response => {
      if (response.status !== 204) {
        return response.text().then(text => {
          if (text) {
            return Promise.reject(text);
          } else {
            return Promise.reject(response.status);
          }
        });
      }
      if (opt_callback) {
        opt_callback(response);
      }
      return response;
    });
  };

  /**
   * Install "stepping stones" API for GWT-compiled plugins by default.
   * @deprecated best effort support, will be removed with GWT UI.
   */
  Gerrit.installGwt = function(url) {
    const name = getPluginNameFromUrl(url);
    let plugin;
    try {
      plugin = _plugins[name] || new Plugin(url);
      plugin.deprecated.install();
      Gerrit._pluginInstalled(url);
    } catch (e) {
      Gerrit._pluginInstallError(`${e.name}: ${e.message}`);
    }
    return plugin;
  };

  Gerrit.awaitPluginsLoaded = function() {
    if (!_allPluginsPromise) {
      if (Gerrit._arePluginsLoaded()) {
        _allPluginsPromise = Promise.resolve();
      } else {
        let timeoutId;
        _allPluginsPromise =
          Promise.race([
            new Promise(resolve => _resolveAllPluginsLoaded = resolve),
            new Promise(resolve => timeoutId = setTimeout(
                Gerrit._pluginLoadingTimeout, PLUGIN_LOADING_TIMEOUT_MS)),
          ]).then(() => clearTimeout(timeoutId));
      }
    }
    return _allPluginsPromise;
  };

  Gerrit._pluginLoadingTimeout = function() {
    console.error(`Failed to load plugins: ${Object.keys(_pluginsPending)}`);
    Gerrit._setPluginsPending([]);
  };

  Gerrit._setPluginsPending = function(plugins) {
    _pluginsPending = plugins.reduce((o, url) => {
      // TODO(viktard): Remove guard (@see Issue 8962)
      o[getPluginNameFromUrl(url) || UNKNOWN_PLUGIN] = url;
      return o;
    }, {});
    Gerrit._setPluginsCount(Object.keys(_pluginsPending).length);
  };

  Gerrit._setPluginsCount = function(count) {
    _pluginsPendingCount = count;
    if (Gerrit._arePluginsLoaded()) {
      getReporting().pluginsLoaded(_pluginsInstalled);
      if (_resolveAllPluginsLoaded) {
        _resolveAllPluginsLoaded();
      }
    }
  };

  Gerrit._pluginInstallError = function(message) {
    document.dispatchEvent(new CustomEvent('show-alert', {
      detail: {
        message: `Plugin install error: ${message}`,
      },
    }));
    console.info(`Plugin install error: ${message}`);
    Gerrit._setPluginsCount(_pluginsPendingCount - 1);
  };

  Gerrit._pluginInstalled = function(url) {
    const name = getPluginNameFromUrl(url) || UNKNOWN_PLUGIN;
    if (!_pluginsPending[name]) {
      console.warn(`Unexpected plugin ${name} installed from ${url}.`);
    } else {
      delete _pluginsPending[name];
      _pluginsInstalled.push(name);
      Gerrit._setPluginsCount(_pluginsPendingCount - 1);
      console.log(`Plugin ${name} installed.`);
    }
  };

  Gerrit._arePluginsLoaded = function() {
    return _pluginsPendingCount === 0;
  };

  Gerrit._getPluginScreenName = function(pluginName, screenName) {
    return `${pluginName}-screen-${screenName}`;
  };

  Gerrit._isPluginPreloaded = function(url) {
    const name = getPluginNameFromUrl(url);
    if (name && Gerrit._preloadedPlugins) {
      return name in Gerrit._preloadedPlugins;
    } else {
      return false;
    }
  };

  window.Gerrit = Gerrit;

  // Preloaded plugins should be installed after Gerrit.install() is set,
  // since plugin preloader substitutes Gerrit.install() temporarily.
  installPreloadedPlugins();
})(window);
