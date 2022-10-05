// Copyright (C) 2022 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.metrics;

import java.time.Duration;

/** Configuration of the Metrics' reservoir type and size. */
public interface MetricsReservoirConfig {

  /** Returns the reservoir type. */
  ReservoirType reservoirType();

  /** Returns the reservoir window duration. */
  Duration reservoirWindow();

  /** Returns the number of samples that the reservoir can contain */
  int reservoirSize();

  /** Returns the alpha parameter of the ExponentiallyDecaying reservoir */
  double reservoirAlpha();
}
