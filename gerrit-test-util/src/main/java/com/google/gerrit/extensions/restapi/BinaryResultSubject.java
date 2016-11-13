// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.extensions.restapi;

import static com.google.common.truth.Truth.assertAbout;

import com.google.common.truth.FailureStrategy;
import com.google.common.truth.PrimitiveByteArraySubject;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;
import com.google.common.truth.Truth;
import com.google.gerrit.truth.OptionalSubject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;

public class BinaryResultSubject extends Subject<BinaryResultSubject, BinaryResult> {

  private static final SubjectFactory<BinaryResultSubject, BinaryResult>
      BINARY_RESULT_SUBJECT_FACTORY =
          new SubjectFactory<BinaryResultSubject, BinaryResult>() {
            @Override
            public BinaryResultSubject getSubject(
                FailureStrategy failureStrategy, BinaryResult binaryResult) {
              return new BinaryResultSubject(failureStrategy, binaryResult);
            }
          };

  public static BinaryResultSubject assertThat(BinaryResult binaryResult) {
    return assertAbout(BINARY_RESULT_SUBJECT_FACTORY).that(binaryResult);
  }

  public static OptionalSubject<BinaryResultSubject, BinaryResult> assertThat(
      Optional<BinaryResult> binaryResultOptional) {
    return OptionalSubject.assertThat(binaryResultOptional, BinaryResultSubject::assertThat);
  }

  private BinaryResultSubject(FailureStrategy failureStrategy, BinaryResult binaryResult) {
    super(failureStrategy, binaryResult);
  }

  public PrimitiveByteArraySubject bytes() throws IOException {
    isNotNull();
    // We shouldn't close the BinaryResult within this method as it might still
    // be used afterwards. Besides, closing it doesn't have an effect for most
    // implementations of a BinaryResult.
    BinaryResult binaryResult = actual();
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    binaryResult.writeTo(byteArrayOutputStream);
    byte[] bytes = byteArrayOutputStream.toByteArray();
    return Truth.assertThat(bytes);
  }
}
