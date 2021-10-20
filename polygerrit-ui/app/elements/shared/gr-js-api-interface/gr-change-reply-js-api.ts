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

import {GrReplyDialog} from '../../../services/gr-rest-api/gr-rest-api';
import {PluginApi, TargetElement} from '../../../api/plugin';
import {JsApiService} from './gr-js-api-types';
import {
  ChangeReplyPluginApi,
  LabelsChangedCallback,
  LabelsChangedDetail,
  ReplyChangedCallback,
  ValueChangedDetail,
} from '../../../api/change-reply';
import {appContext} from '../../../services/app-context';
import {PluginElement} from '../../../api/hook';

/**
 * GrChangeReplyInterface, provides a set of handy methods on reply dialog.
 */
export class GrChangeReplyInterface implements ChangeReplyPluginApi {
  private readonly reporting = appContext.reportingService;

  constructor(
    readonly plugin: PluginApi,
    readonly sharedApiElement: JsApiService
  ) {
    this.reporting.trackApi(this.plugin, 'reply', 'constructor');
  }

  get _el(): GrReplyDialog {
    return this.sharedApiElement.getElement(
      TargetElement.REPLY_DIALOG
    ) as unknown as GrReplyDialog;
  }

  getLabelValue(label: string): string {
    this.reporting.trackApi(this.plugin, 'reply', 'getLabelValue');
    return this._el.getLabelValue(label);
  }

  setLabelValue(label: string, value: string) {
    this.reporting.trackApi(this.plugin, 'reply', 'setLabelValue');
    this._el.setLabelValue(label, value);
  }

  addReplyTextChangedCallback(handler: ReplyChangedCallback) {
    this.reporting.trackApi(this.plugin, 'reply', 'addReplyTextChangedCb');
    const hookApi = this.plugin.hook<PluginElement>('reply-text');
    const registeredHandler = (e: Event) => {
      const ce = e as CustomEvent<ValueChangedDetail>;
      handler(ce.detail.value);
    };
    hookApi.onAttached(el => {
      el.content?.addEventListener('value-changed', registeredHandler);
    });
    hookApi.onDetached(el => {
      el.content?.removeEventListener('value-changed', registeredHandler);
    });
  }

  addLabelValuesChangedCallback(handler: LabelsChangedCallback) {
    this.reporting.trackApi(this.plugin, 'reply', 'addLabelValuesChangedCb');
    const hookApi = this.plugin.hook<PluginElement>('reply-label-scores');
    let registeredHandler: (e: Event) => void;
    hookApi.onAttached(el => {
      registeredHandler = (e: Event) => {
        const ce = e as CustomEvent<LabelsChangedDetail>;
        handler({...ce.detail, change: el.change});
      };
      el.content?.addEventListener('labels-changed', registeredHandler);
    });
    hookApi.onDetached(el => {
      if (registeredHandler) {
        el.content?.removeEventListener('labels-changed', registeredHandler);
      }
    });
  }

  showMessage(message: string) {
    this.reporting.trackApi(this.plugin, 'reply', 'showMessage');
    this._el.setPluginMessage(message);
  }
}
