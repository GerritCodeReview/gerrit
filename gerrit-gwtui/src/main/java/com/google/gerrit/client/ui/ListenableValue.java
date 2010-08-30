// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.client.ui;

import com.google.gwt.event.logical.shared.HasValueChangeHandlers;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HandlerManager;
import com.google.gwt.event.shared.HandlerRegistration;


public class ListenableValue<T> implements HasValueChangeHandlers<T> {

  private HandlerManager manager = new HandlerManager(this);

  private T value;

  public T get() {
    return value;
  }

  public void set(final T value) {
    this.value = value;
    fireEvent(new ValueChangeEvent<T>(value) {});
  }

  public void fireEvent(GwtEvent<?> event) {
    manager.fireEvent(event);
  }

  public HandlerRegistration addValueChangeHandler(
      ValueChangeHandler<T> handler) {
    return manager.addHandler(ValueChangeEvent.getType(), handler);
  }
}
