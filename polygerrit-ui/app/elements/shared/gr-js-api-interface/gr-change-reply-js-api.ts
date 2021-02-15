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

/**
 * GrChangeReplyInterface, provides a set of handy methods on reply dialog.
 */
export class GrChangeReplyInterface implements ChangeReplyPluginApi {
  constructor(
    readonly plugin: PluginApi,
    readonly sharedApiElement: JsApiService
  ) {}

  get _el(): GrReplyDialog {
    return (this.sharedApiElement.getElement(
      TargetElement.REPLY_DIALOG
    ) as unknown) as GrReplyDialog;
  }

  getLabelValue(label: string): string {
    return this._el.getLabelValue(label);
  }

  setLabelValue(label: string, value: string) {
    this._el.setLabelValue(label, value);
  }

  send(includeComments?: boolean) {
    this._el.send(includeComments);
  }

  addReplyTextChangedCallback(handler: ReplyChangedCallback) {
    const hookApi = this.plugin.hook('reply-text');
    const registeredHandler = (e: Event) => {
      const ce = e as CustomEvent<ValueChangedDetail>;
      handler(ce.detail.value);
    };
    hookApi.onAttached(el => {
      if (!el.content) {
        return;
      }
      el.content.addEventListener('value-changed', registeredHandler);
    });
    hookApi.onDetached(el => {
      if (!el.content) {
        return;
      }
      el.content.removeEventListener('value-changed', registeredHandler);
    });
  }

  addLabelValuesChangedCallback(handler: LabelsChangedCallback) {
    const hookApi = this.plugin.hook('reply-label-scores');
    const registeredHandler = (e: Event) => {
      const ce = e as CustomEvent<LabelsChangedDetail>;
      handler(ce.detail);
    };
    hookApi.onAttached(el => {
      if (!el.content) {
        return;
      }
      el.content.addEventListener('labels-changed', registeredHandler);
    });

    hookApi.onDetached(el => {
      if (!el.content) {
        return;
      }
      el.content.removeEventListener('labels-changed', registeredHandler);
    });
  }

  showMessage(message: string) {
    this._el.setPluginMessage(message);
  }
}
