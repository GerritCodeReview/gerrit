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

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;

import java.lang.reflect.Method;

import org.eclipse.jgit.internal.storage.dfs.DfsRepository;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.FS;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.powermock.api.easymock.PowerMock;

import com.google.gerrit.server.git.PermissionAwareDfsRepository;

@RunWith(Parameterized.class)
public class PermissionAwareDfsRepositoryTest extends PermissionAwareRepositoryTestBase {
	
	private DfsRepository repository;

	public PermissionAwareDfsRepositoryTest(Method repositoryMethod, String repositoryMethodName) {
		super(repositoryMethod, repositoryMethodName);
	}
	
	@Override
	@Before
	public void setUp() {
		super.setUp();
		repository = PowerMock.createMock(DfsRepository.class);
	}
	
	
	@Override
	protected Repository buildPermissionAwareRepo() {
		  expect(repository.getFS()).andReturn(FS.DETECTED);
		    // DfsRepo requires null file system references
		    expect(repository.getDirectory()).andReturn(null);
		    expect(repository.getIndexFile()).andReturn(null);
		    expect(repository.getWorkTree()).andReturn(null);
		    expect(repository.isBare()).andReturn(false);

		    replay(repository);

		    PermissionAwareDfsRepository permissionAwareRepository =
		        new PermissionAwareDfsRepository(repository, forProject);

		    reset(repository);

		    return permissionAwareRepository;	
	}
	
	@Override
	protected Repository repository() {
		return repository;
	}
}
