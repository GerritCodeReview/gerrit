/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {ChangeInfo} from './rest-api';

export declare interface LabelsChangedDetail {
  name: string;
  value: string;
}
export declare interface ValueChangedDetail {
  value: string;
}
export type ReplyChangedCallback = (text: string, change?: ChangeInfo) => void;
export type LabelsChangedCallback = (
  detail: LabelsChangedDetail,
  change?: ChangeInfo
) => void;

export declare interface ChangeReplyPluginApi {
  getLabelValue(label: string): string | number | undefined;

  setLabelValue(label: string, value: string): void;

  addReplyTextChangedCallback(handler: ReplyChangedCallback): void;

  addLabelValuesChangedCallback(handler: LabelsChangedCallback): void;

  showMessage(message: string): void;
}
