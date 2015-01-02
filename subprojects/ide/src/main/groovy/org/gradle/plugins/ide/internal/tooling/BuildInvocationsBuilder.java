/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.plugins.ide.internal.tooling;

import com.google.common.base.Strings;
import com.google.common.collect.*;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.internal.project.ProjectTaskLister;
import org.gradle.tooling.internal.consumer.converters.TaskNameComparator;
import org.gradle.tooling.internal.impl.DefaultBuildInvocations;
import org.gradle.tooling.internal.impl.LaunchableGradleTask;
import org.gradle.tooling.internal.impl.LaunchableGradleTaskSelector;
import org.gradle.tooling.model.internal.ProjectSensitiveToolingModelBuilder;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;

public class BuildInvocationsBuilder extends ProjectSensitiveToolingModelBuilder {
    private final ProjectTaskLister taskLister;

    public BuildInvocationsBuilder(ProjectTaskLister taskLister) {
        this.taskLister = taskLister;
    }

    public boolean canBuild(String modelName) {
        return modelName.equals("org.gradle.tooling.model.gradle.BuildInvocations");
    }

    public DefaultBuildInvocations buildAll(String modelName, Project project) {
        if (!canBuild(modelName)) {
            throw new GradleException("Unknown model name " + modelName);
        }
        List<LaunchableGradleTaskSelector> selectors = Lists.newArrayList();
        Set<String> aggregatedTasks = Sets.newLinkedHashSet();
        Set<String> visibleTasks = Sets.newLinkedHashSet();
        TreeBasedTable<String, String, String> taskDescriptions = TreeBasedTable.create(Ordering.usingToString(), new TaskNameComparator());
        findTasks(project, aggregatedTasks, visibleTasks, taskDescriptions);
        for (String selectorName : aggregatedTasks) {
            SortedMap<String, String> descriptionsFromAllPaths = taskDescriptions.row(selectorName);
            selectors.add(new LaunchableGradleTaskSelector().
                    setName(selectorName).
                    setTaskName(selectorName).
                    setProjectPath(project.getPath()).
                    setDescription(descriptionsFromAllPaths.get(descriptionsFromAllPaths.firstKey())).
                    setDisplayName(String.format("%s in %s and subprojects.", selectorName, project.toString())).
                    setPublic(visibleTasks.contains(selectorName)));
        }
        return new DefaultBuildInvocations()
                .setSelectors(selectors)
                .setTasks(tasks(project));
    }

    public DefaultBuildInvocations buildAll(String modelName, Project project, boolean implicitProject) {
        return buildAll(modelName, implicitProject ? project.getRootProject() : project);
    }

    // build tasks without project reference
    private List<LaunchableGradleTask> tasks(Project project) {
        List<LaunchableGradleTask> tasks = Lists.newArrayList();
        for (Task task : taskLister.listProjectTasks(project)) {
            tasks.add(new LaunchableGradleTask()
                    .setPath(task.getPath())
                    .setName(task.getName())
                    .setDisplayName(task.toString())
                    .setDescription(task.getDescription())
                    .setPublic(!Strings.isNullOrEmpty(task.getGroup())));
        }
        return tasks;
    }

    private void findTasks(Project project, Collection<String> aggregatedTasks, Collection<String> visibleTasks, Table<String, String, String> taskDescriptions) {
        for (Project child : project.getChildProjects().values()) {
            findTasks(child, aggregatedTasks, visibleTasks, taskDescriptions);
        }
        for (Task task : taskLister.listProjectTasks(project)) {
            aggregatedTasks.add(task.getName());

            // visible tasks are specified as those that have a non-empty group
            if (!Strings.isNullOrEmpty(task.getGroup())) {
                visibleTasks.add(task.getName());
            }

            // store the description first by task name and then by path
            // this allows to later fish out the description of the task whose name matches the selector name and
            // whose path is the smallest for the given task name (the first entry of the table column)
            // store null description as empty string to avoid that Guava chokes
            taskDescriptions.put(task.getName(), task.getPath(), Strings.nullToEmpty(task.getDescription()));
        }
    }

}
