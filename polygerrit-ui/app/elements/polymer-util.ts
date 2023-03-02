/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {IronA11yAnnouncer} from '@polymer/iron-a11y-announcer/iron-a11y-announcer';

export interface FixIronA11yAnnouncer extends IronA11yAnnouncer {
  requestAvailability(): void;
}

export function ironAnnouncerRequestAvailability() {
  (IronA11yAnnouncer as unknown as FixIronA11yAnnouncer).requestAvailability();
}
