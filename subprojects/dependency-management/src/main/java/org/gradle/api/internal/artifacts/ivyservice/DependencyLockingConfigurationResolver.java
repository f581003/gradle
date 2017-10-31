/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.artifacts.ivyservice;

import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.internal.artifacts.ConfigurationResolver;
import org.gradle.api.internal.artifacts.ResolverResults;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.internal.dependencylock.DependencyLockManager;

public class DependencyLockingConfigurationResolver implements ConfigurationResolver {

    private final DefaultConfigurationResolver delegate;
    private final ProjectFinder projectFinder;
    private final boolean dependencyLockEnabled;
    private final DependencyLockManager dependencyLockManager;

    public DependencyLockingConfigurationResolver(DefaultConfigurationResolver delegate,
                                                  ProjectFinder projectFinder,
                                                  boolean dependencyLockEnabled,
                                                  DependencyLockManager dependencyLockManager) {
        this.delegate = delegate;
        this.projectFinder = projectFinder;
        this.dependencyLockEnabled = dependencyLockEnabled;
        this.dependencyLockManager = dependencyLockManager;
    }

    @Override
    public void resolveBuildDependencies(ConfigurationInternal configuration, ResolverResults result) {
        delegate.resolveBuildDependencies(configuration, result);
    }

    @Override
    public void resolveGraph(ConfigurationInternal configuration, ResolverResults results) throws ResolveException {
        lockConfiguration(configuration);
        delegate.resolveGraph(configuration, results);
    }

    @Override
    public void resolveArtifacts(ConfigurationInternal configuration, ResolverResults results) throws ResolveException {
        delegate.resolveArtifacts(configuration, results);
    }

    private void lockConfiguration(ConfigurationInternal configuration) {
        if (dependencyLockEnabled) {
            ProjectInternal project = projectFinder.getProject(configuration.getModule().getProjectPath());
            dependencyLockManager.lockResolvedDependencies(project, configuration);
        }
    }
}