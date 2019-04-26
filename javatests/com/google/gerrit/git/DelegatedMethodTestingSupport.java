// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.git;

import static com.google.common.truth.Truth.assertThat;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;
import static org.easymock.EasyMock.verify;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang.ArrayUtils;
import org.easymock.IExpectationSetters;

import com.google.common.base.Defaults;

public class DelegatedMethodTestingSupport {
	public static <T> Stream<Method> methodsForDelegationTest(Class<T> forClass) {
		return Arrays.stream(forClass.getMethods())
                .filter(method ->
                            !(method.getDeclaringClass() != forClass
                                || Modifier.isStatic(method.getModifiers())
                                || Modifier.isFinal(method.getModifiers())) 
                 );
	}
	
	
	public static <T> void assertMethodIsDelegated(Method method, T wrapper, T delegateMock, Object[] additionalMocks) throws Exception {
		  Object[] mocks = ArrayUtils.add(additionalMocks, delegateMock);
	      
		  reset(mocks);

	      List<Object> parametersValue =
	          Arrays.stream(method.getParameters())
	              .map(parameter -> defaultValueFor(parameter.getType()))
	              .collect(Collectors.toList());

	      Object expected = defaultValueFor(method.getReturnType());

	      IExpectationSetters<Object> expectDelegateCalled =
	          expect(method.invoke(wrapper, parametersValue.toArray()));
	      if (method.getReturnType() != Void.TYPE) {
	        expectDelegateCalled.andReturn(expected);
	      }

	      replay(mocks);

	      if (method.getReturnType() != Void.TYPE) {
	        assertThat(method.invoke(wrapper, parametersValue.toArray())).isEqualTo(expected);
	      } else {
	        method.invoke(wrapper, parametersValue.toArray());
	      }

	      verify(mocks);
	  }

	  private static Object defaultValueFor(Class<?> type) {
	    if (type == String.class) return "";

	    if (type == File.class) return new File("");

	    return Defaults.defaultValue(type);
	  }

}
