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
import {ChangeInfo} from './rest-api';

export declare interface LabelsChangedDetail {
  name: string;
  value: string;
  change?: ChangeInfo;
}
export declare interface ValueChangedDetail {
  value: string;
}
export type ReplyChangedCallback = (text: string) => void;
export type LabelsChangedCallback = (detail: LabelsChangedDetail) => void;

export declare interface ChangeReplyPluginApi {
  getLabelValue(label: string): string;

  setLabelValue(label: string, value: string): void;

  addReplyTextChangedCallback(handler: ReplyChangedCallback): void;

  addLabelValuesChangedCallback(handler: LabelsChangedCallback): void;

  showMessage(message: string): void;
}
