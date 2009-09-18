// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.scala;

import java.util.Collection;

import scala.Function1;
import scala.Function1$class;

public class ScalaUtil {

  public static <T,R> Function1<T,R> fun1(final JFunction1<T,R> f) {
    return new Function1<T,R>() {

      @SuppressWarnings("unchecked")
      @Override
      public <A> Function1<T, A> andThen(Function1<R, A> g) {
        return Function1$class.andThen(this, g);
      }

      @Override
      public R apply(T x) {
        try {
          return f.apply(x);
        } catch (Throwable e) {
          throw new RuntimeException(e);
        }
      }

      @SuppressWarnings("unchecked")
      @Override
      public <A> Function1<A, R> compose(Function1<A, T> g) {
        return Function1$class.compose(this, g);
      }

      @Override
      public int $tag() {
        return 0;
      }

    };
  }

  @SuppressWarnings("unchecked")
  public static <T> scala.List<T> toScalaList(Collection<T> xs) {
    //Java sucks as there is no way to create a generic Array so casting is needed
    return scala.List$.MODULE$.fromArray(xs.toArray( (T[])new Object[xs.size()] ));
  }

  public static <T,U> scala.Tuple2<T,U> tuple2(T x, U y) {
    return new scala.Tuple2<T,U>(x, y);
  }

}
