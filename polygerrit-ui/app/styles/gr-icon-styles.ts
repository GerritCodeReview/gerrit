/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css} from 'lit';

export const iconStyles = css`
  iron-icon {
    display: inline-block;
    vertical-align: top;
    width: 20px;
    height: 20px;
  }
  /* expected to be used in a 20px line-height inline context */
  iron-icon.size-16 {
    width: 16px;
    height: 16px;
    position: relative;
    top: 2px;
  }
`;
