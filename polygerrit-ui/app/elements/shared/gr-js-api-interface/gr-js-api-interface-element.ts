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
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {getPluginLoader} from './gr-plugin-loader';
import {patchNumEquals} from '../../../utils/patch-set-util';
import {customElement} from '@polymer/decorators';
import {
  ChangeInfo,
  LabelNameToValuesMap,
  RevisionInfo,
} from '../../../types/common';
import {GrAnnotationActionsInterface} from './gr-annotation-actions-js-api';
import {GrAdminApi} from '../../plugins/gr-admin-api/gr-admin-api';
import {
  JsApiService,
  EventCallback,
  ShowChangeDetail,
  ShowRevisionActionsDetail,
} from './gr-js-api-types';
import {EventType, TargetElement} from '../../plugins/gr-plugin-types';
import {DiffLayer, HighlightJS} from '../../../types/types';

const elements: {[key: string]: HTMLElement} = {};
const eventCallbacks: {[key: string]: EventCallback[]} = {};

@customElement('gr-js-api-interface')
export class GrJsApiInterface
  extends GestureEventListeners(LegacyElementMixin(PolymerElement))
  implements JsApiService {
  handleEvent(type: EventType, detail: any) {
    getPluginLoader()
      .awaitPluginsLoaded()
      .then(() => {
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
            console.warn(
              'handleEvent called with unsupported event type:',
              type
            );
            break;
        }
      });
  }

  addElement(key: TargetElement, el: HTMLElement) {
    elements[key] = el;
  }

  getElement(key: TargetElement) {
    return elements[key];
  }

  addEventCallback(eventName: EventType, callback: EventCallback) {
    if (!eventCallbacks[eventName]) {
      eventCallbacks[eventName] = [];
    }
    eventCallbacks[eventName].push(callback);
  }

  canSubmitChange(change: ChangeInfo, revision?: RevisionInfo | null) {
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

  /** For testing only. */
  _removeEventCallbacks() {
    for (const type of Object.values(EventType)) {
      eventCallbacks[type] = [];
    }
  }

  // TODO(TS): The HISTORY event and its handler seem unused.
  _handleHistory(detail: {path: string}) {
    for (const cb of this._getEventCallbacks(EventType.HISTORY)) {
      try {
        cb(detail.path);
      } catch (err) {
        console.error(err);
      }
    }
  }

  _handleShowChange(detail: ShowChangeDetail) {
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
    const change = {
      ...detail.change,
      get mergeable() {
        console.warn(
          'Accessing change.mergeable from SHOW_CHANGE is ' +
            'deprecated! Use info.mergeable instead.'
        );
        return detail.info && detail.info.mergeable;
      },
    };
    const patchNum = detail.patchNum;
    const info = detail.info;

    let revision;
    for (const rev of Object.values(change.revisions || {})) {
      if (patchNumEquals(rev._number, patchNum)) {
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

  _handleShowRevisionActions(detail: ShowRevisionActionsDetail) {
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

  handleCommitMessage(change: ChangeInfo, msg: string) {
    for (const cb of this._getEventCallbacks(EventType.COMMIT_MSG_EDIT)) {
      try {
        cb(change, msg);
      } catch (err) {
        console.error(err);
      }
    }
  }

  // TODO(TS): The COMMENT event and its handler seem unused.
  _handleComment(detail: {node: Node}) {
    for (const cb of this._getEventCallbacks(EventType.COMMENT)) {
      try {
        cb(detail.node);
      } catch (err) {
        console.error(err);
      }
    }
  }

  _handleLabelChange(detail: {change: ChangeInfo}) {
    for (const cb of this._getEventCallbacks(EventType.LABEL_CHANGE)) {
      try {
        cb(detail.change);
      } catch (err) {
        console.error(err);
      }
    }
  }

  _handleHighlightjsLoaded(detail: {hljs: HighlightJS}) {
    for (const cb of this._getEventCallbacks(EventType.HIGHLIGHTJS_LOADED)) {
      try {
        cb(detail.hljs);
      } catch (err) {
        console.error(err);
      }
    }
  }

  modifyRevertMsg(change: ChangeInfo, revertMsg: string, origMsg: string) {
    for (const cb of this._getEventCallbacks(EventType.REVERT)) {
      try {
        revertMsg = cb(change, revertMsg, origMsg) as string;
      } catch (err) {
        console.error(err);
      }
    }
    return revertMsg;
  }

  modifyRevertSubmissionMsg(
    change: ChangeInfo,
    revertSubmissionMsg: string,
    origMsg: string
  ) {
    for (const cb of this._getEventCallbacks(EventType.REVERT_SUBMISSION)) {
      try {
        revertSubmissionMsg = cb(
          change,
          revertSubmissionMsg,
          origMsg
        ) as string;
      } catch (err) {
        console.error(err);
      }
    }
    return revertSubmissionMsg;
  }

  getDiffLayers(path: string, changeNum: number) {
    const layers: DiffLayer[] = [];
    for (const cb of this._getEventCallbacks(EventType.ANNOTATE_DIFF)) {
      const annotationApi = (cb as unknown) as GrAnnotationActionsInterface;
      try {
        const layer = annotationApi.getLayer(path, changeNum);
        layers.push(layer);
      } catch (err) {
        console.error(err);
      }
    }
    return layers;
  }

  disposeDiffLayers(path: string) {
    for (const cb of this._getEventCallbacks(EventType.ANNOTATE_DIFF)) {
      try {
        const annotationApi = (cb as unknown) as GrAnnotationActionsInterface;
        annotationApi.disposeLayer(path);
      } catch (err) {
        console.error(err);
      }
    }
  }

  /**
   * Retrieves coverage data possibly provided by a plugin.
   *
   * Will wait for plugins to be loaded. If multiple plugins offer a coverage
   * provider, the first one is returned. If no plugin offers a coverage provider,
   * will resolve to null.
   */
  getCoverageAnnotationApi(): Promise<
    GrAnnotationActionsInterface | undefined
  > {
    return getPluginLoader()
      .awaitPluginsLoaded()
      .then(
        () =>
          this._getEventCallbacks(EventType.ANNOTATE_DIFF).find(cb => {
            const annotationApi = (cb as unknown) as GrAnnotationActionsInterface;
            return annotationApi.getCoverageProvider();
          }) as GrAnnotationActionsInterface | undefined
      );
  }

  getAdminMenuLinks() {
    const links = [];
    for (const cb of this._getEventCallbacks(EventType.ADMIN_MENU_LINKS)) {
      const adminApi = (cb as unknown) as GrAdminApi;
      links.push(...adminApi.getMenuLinks());
    }
    return links;
  }

  getLabelValuesPostRevert(change?: ChangeInfo): LabelNameToValuesMap {
    let labels: LabelNameToValuesMap = {};
    for (const cb of this._getEventCallbacks(EventType.POST_REVERT)) {
      try {
        labels = cb(change);
      } catch (err) {
        console.error(err);
      }
    }
    return labels;
  }

  _getEventCallbacks(type: EventType) {
    return eventCallbacks[type] || [];
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-js-api-interface': JsApiService & Element;
  }
}
