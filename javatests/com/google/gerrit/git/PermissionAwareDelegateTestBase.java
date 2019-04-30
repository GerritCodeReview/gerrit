
package com.google.gerrit.git;

import static com.google.common.truth.Truth.assertThat;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;
import static org.easymock.EasyMock.verify;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.commons.lang.ArrayUtils;
import org.easymock.EasyMockSupport;
import org.easymock.IExpectationSetters;
import org.easymock.internal.AssertionErrorWrapper;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;

import com.google.common.base.Defaults;
import com.google.common.collect.ImmutableList;

public abstract class PermissionAwareDelegateTestBase<T> extends EasyMockSupport {

	protected abstract Set<String> methodsWithSpecificTest();
	
	protected abstract Object[] additionalMockObjectsForDelegateCheck();
	
	protected abstract void setExpectationsForAdditionalMockObjects();
	
	protected abstract T delegate();
	
	protected Ref aRef(String name) {
		return new SimpleRef(name);
	}
	
	@SuppressWarnings("unchecked")
	protected void shouldDelegateAllPublicMethods(Supplier<T> wrapperProvider) {	
		//Cannot simply do delegate().getClass since we would receive the EasyMock subclass
		Class<T> delegateClass = (Class<T>)
                ((ParameterizedType)getClass()
                .getGenericSuperclass())
                .getActualTypeArguments()[0];
		
		final List<String> notDelegatedMethods = 
				Arrays.stream(delegateClass.getMethods())
		 .filter(method ->  
			 !(method.getDeclaringClass() != delegateClass ||
				Modifier.isStatic(method.getModifiers()) ||
				Modifier.isFinal(method.getModifiers()) ||
				hasSpecialTest(method)
			 )
		)
		.filter(method -> !isDelegated(wrapperProvider, method))
		.map(Method::getName)
		.collect(Collectors.toList());
		
		assertThat(notDelegatedMethods).isEqualTo(ImmutableList.of());
	}
	
	private boolean isDelegated(Supplier<T> wrapperBuilder, Method repositoryMethod) {
		try {
			T wrapper = wrapperBuilder.get();

			Object[] mocks = ArrayUtils.add(additionalMockObjectsForDelegateCheck(), delegate());
			
			reset(mocks);
			
			List<Object> parametersValue = 
					Arrays.stream(repositoryMethod.getParameters())
					.map(parameter -> defaultValueFor(parameter.getType()))
					.collect(Collectors.toList());

			Object expected = defaultValueFor(repositoryMethod.getReturnType());
			
			IExpectationSetters<Object> expectDelegateCalled = 
					expect(repositoryMethod.invoke(delegate(), parametersValue.toArray()));
			if(repositoryMethod.getReturnType() != Void.TYPE) {
				expectDelegateCalled.andReturn(expected);
			}  
			
			setExpectationsForAdditionalMockObjects();
			
			replay(mocks);
			
			if(repositoryMethod.getReturnType() != Void.TYPE) {
				assertThat(repositoryMethod.invoke(wrapper, parametersValue.toArray())).isEqualTo(expected);
			} else {
				repositoryMethod.invoke(wrapper, parametersValue.toArray());
			}
			
			verify(mocks);
			
			return true;
		} catch(AssertionError | IllegalStateException | AssertionErrorWrapper methodNotDelegated) {
			return false;
		}  catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			throw new RuntimeException("Cannot invoke method " + repositoryMethod.getName() + " via reflection", e);
		}
	}
	
	private boolean hasSpecialTest(Method repositoryMethod) {
		return methodsWithSpecificTest().contains(repositoryMethod.getName());
	}
	
	private Object defaultValueFor(Class<?> type) {
		if(type == String.class) return "";
		
		if(type == File.class) return new File("");
		
		return Defaults.defaultValue(type);
	}
	
	public static class SimpleRef implements Ref {
	    private final String name;

	    SimpleRef(String name) {
	      this.name = name;
	    }

	    @Override
	    public String getName() {
	      return name;
	    }

	    @Override
	    public boolean isSymbolic() {
	      return false;
	    }

	    @Override
	    public Ref getLeaf() {
	      return null;
	    }

	    @Override
	    public Ref getTarget() {
	      return null;
	    }

	    @Override
	    public ObjectId getObjectId() {
	      return ObjectId.zeroId();
	    }

	    @Override
	    public ObjectId getPeeledObjectId() {
	      return ObjectId.zeroId();
	    }

	    @Override
	    public boolean isPeeled() {
	      return false;
	    }

	    @Override
	    public Storage getStorage() {
	      return Storage.NETWORK;
	    }
	    
	    @Override
	    public String toString() { return String.format("SimpleRef(%s)", name); }
	  }
}
