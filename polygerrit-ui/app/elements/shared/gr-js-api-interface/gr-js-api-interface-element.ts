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
import {mixinBehaviors} from '@polymer/polymer/lib/legacy/class.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {PatchSetBehavior} from '../../../behaviors/gr-patch-set-behavior/gr-patch-set-behavior.js';
import {pluginLoader} from './gr-plugin-loader.js';

// Note: for new events, naming convention should be: `a-b`
const EventType = {
  HISTORY: 'history',
  LABEL_CHANGE: 'labelchange',
  SHOW_CHANGE: 'showchange',
  SUBMIT_CHANGE: 'submitchange',
  SHOW_REVISION_ACTIONS: 'show-revision-actions',
  COMMIT_MSG_EDIT: 'commitmsgedit',
  COMMENT: 'comment',
  REVERT: 'revert',
  REVERT_SUBMISSION: 'revert_submission',
  POST_REVERT: 'postrevert',
  ANNOTATE_DIFF: 'annotatediff',
  ADMIN_MENU_LINKS: 'admin-menu-links',
  HIGHLIGHTJS_LOADED: 'highlightjs-loaded',
};

const Element = {
  CHANGE_ACTIONS: 'changeactions',
  REPLY_DIALOG: 'replydialog',
};

/**
 * @extends PolymerElement
 */
class GrJsApiInterface extends mixinBehaviors( [
  PatchSetBehavior,
], GestureEventListeners(
    LegacyElementMixin(
        PolymerElement))) {
  static get is() { return 'gr-js-api-interface'; }

  constructor() {
    super();
    this.Element = Element;
    this.EventType = EventType;
  }

  static get properties() {
    return {
      _elements: {
        type: Object,
        value: {}, // Shared across all instances.
      },
      _eventCallbacks: {
        type: Object,
        value: {}, // Shared across all instances.
      },
    };
  }

  handleEvent(type, detail) {
    pluginLoader.awaitPluginsLoaded().then(() => {
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
        case EventType.SHOW_REVISION_ACTIONS:
          this._handleShowRevisionActions(detail);
          break;
        case EventType.HIGHLIGHTJS_LOADED:
          this._handleHighlightjsLoaded(detail);
          break;
        default:
          console.warn('handleEvent called with unsupported event type:',
              type);
          break;
      }
    });
  }

  addElement(key, el) {
    this._elements[key] = el;
  }

  getElement(key) {
    return this._elements[key];
  }

  addEventCallback(eventName, callback) {
    if (!this._eventCallbacks[eventName]) {
      this._eventCallbacks[eventName] = [];
    }
    this._eventCallbacks[eventName].push(callback);
  }

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
  }

  _removeEventCallbacks() {
    for (const k in EventType) {
      if (!EventType.hasOwnProperty(k)) { continue; }
      this._eventCallbacks[EventType[k]] = [];
    }
  }

  _handleHistory(detail) {
    for (const cb of this._getEventCallbacks(EventType.HISTORY)) {
      try {
        cb(detail.path);
      } catch (err) {
        console.error(err);
      }
    }
  }

  _handleShowChange(detail) {
    // Note (issue 8221) Shallow clone the change object and add a mergeable
    // getter with deprecation warning. This makes the change detail appear as
    // though SKIP_MERGEABLE was not set, so that plugins that expect it can
    // still access.
    //
    // This clone and getter can be removed after plugins migrate to use
    // info.mergeable.
    //
    // assign on getter with existing property will report error
    // see Issue: 12286
    const change = Object.assign({}, detail.change, {
      get mergeable() {
        console.warn('Accessing change.mergeable from SHOW_CHANGE is ' +
            'deprecated! Use info.mergeable instead.');
        return detail.info && detail.info.mergeable;
      },
    });
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
  }

  /**
   * @param {!{change: !Object, revisionActions: !Object}} detail
   */
  _handleShowRevisionActions(detail) {
    const registeredCallbacks = this._getEventCallbacks(
        EventType.SHOW_REVISION_ACTIONS
    );
    for (const cb of registeredCallbacks) {
      try {
        cb(detail.revisionActions, detail.change);
      } catch (err) {
        console.error(err);
      }
    }
  }

  handleCommitMessage(change, msg) {
    for (const cb of this._getEventCallbacks(EventType.COMMIT_MSG_EDIT)) {
      try {
        cb(change, msg);
      } catch (err) {
        console.error(err);
      }
    }
  }

  _handleComment(detail) {
    for (const cb of this._getEventCallbacks(EventType.COMMENT)) {
      try {
        cb(detail.node);
      } catch (err) {
        console.error(err);
      }
    }
  }

  _handleLabelChange(detail) {
    for (const cb of this._getEventCallbacks(EventType.LABEL_CHANGE)) {
      try {
        cb(detail.change);
      } catch (err) {
        console.error(err);
      }
    }
  }

  _handleHighlightjsLoaded(detail) {
    for (const cb of this._getEventCallbacks(EventType.HIGHLIGHTJS_LOADED)) {
      try {
        cb(detail.hljs);
      } catch (err) {
        console.error(err);
      }
    }
  }

  modifyRevertMsg(change, revertMsg, origMsg) {
    for (const cb of this._getEventCallbacks(EventType.REVERT)) {
      try {
        revertMsg = cb(change, revertMsg, origMsg);
      } catch (err) {
        console.error(err);
      }
    }
    return revertMsg;
  }

  modifyRevertSubmissionMsg(change, revertSubmissionMsg, origMsg) {
    for (const cb of this._getEventCallbacks(EventType.REVERT_SUBMISSION)) {
      try {
        revertSubmissionMsg = cb(change, revertSubmissionMsg, origMsg);
      } catch (err) {
        console.error(err);
      }
    }
    return revertSubmissionMsg;
  }

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
  }

  /**
   * Retrieves coverage data possibly provided by a plugin.
   *
   * Will wait for plugins to be loaded. If multiple plugins offer a coverage
   * provider, the first one is returned. If no plugin offers a coverage provider,
   * will resolve to null.
   *
   * @return {!Promise<?GrAnnotationActionsInterface>}
   */
  getCoverageAnnotationApi() {
    return pluginLoader.awaitPluginsLoaded()
        .then(() => this._getEventCallbacks(EventType.ANNOTATE_DIFF)
            .find(api => api.getCoverageProvider()));
  }

  getAdminMenuLinks() {
    const links = [];
    for (const adminApi of
      this._getEventCallbacks(EventType.ADMIN_MENU_LINKS)) {
      links.push(...adminApi.getMenuLinks());
    }
    return links;
  }

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
  }

  _getEventCallbacks(type) {
    return this._eventCallbacks[type] || [];
  }
}

customElements.define(GrJsApiInterface.is, GrJsApiInterface);
