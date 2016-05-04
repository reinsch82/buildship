/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.eclipse.buildship.ui.view.task

import com.gradleware.tooling.toolingmodel.repository.FetchStrategy

import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.widgets.Item
import org.eclipse.swt.widgets.Widget;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotView;
import org.eclipse.ui.IWorkbenchPage
import org.eclipse.ui.PlatformUI;

import org.eclipse.buildship.ui.test.fixtures.ProjectSynchronizationSpecification
import org.eclipse.buildship.ui.test.fixtures.SwtBotSpecification;
import org.eclipse.buildship.ui.util.workbench.WorkbenchUtils;

class TaskViewContentSpec extends ProjectSynchronizationSpecification {

    TaskView view
    TreeViewer tree

    void setup() {
        PlatformUI.workbench.display.syncExec {
            view = WorkbenchUtils.showView(TaskView.ID, null, IWorkbenchPage.VIEW_ACTIVATE)
            tree = view.treeViewer
        }
    }

    def "Task are grouped by default"() {
        when:
        def project = dir("root") {
            file 'build.gradle', """
                task foo() {
                    group = 'custom'
                }
            """
        }
        importAndWait(project)

        then:
        taskTree.root.custom.contains('foo')
    }

    def "Task selectors are aggregated from subprojects"() {
        when:
        def project = dir("root") {
            file 'settings.gradle', "include 'a'"
            a {
                file 'build.gradle', """
                    task foo() {
                        group = 'custom'
                    }
                """
            }
        }
        importAndWait(project)

        then:
        taskTree.root.custom.contains('foo')
        taskTree.a.custom.contains('foo')
    }

    def "If one project has errors, tasks from other projects are still visible"() {
        given:
        def first = dir("a") {
            file 'build.gradle'
        }
        def second = dir("b") {
            file 'build.gradle'
        }
        importAndWait(first)
        importAndWait(second)
        fileTree(first) {
            file 'build.gradle', "error"
        }

        when:
        reloadTaskView()

        then:
        !taskTree.a
        taskTree.b
    }

    def "The task view is refreshed when projects are added/deleted"() {
        given:
        def first = dir("a") {
            file 'build.gradle'
        }
        def second = dir("b") {
            file 'build.gradle'
        }
        importAndWait(first)
        importAndWait(second)
        fileTree(first) {
            file 'build.gradle', "error"
        }

        when:
        findProject("b").delete(true, true, null)
        waitForGradleJobsToFinish()

        then:
        !taskTree.a
        !taskTree.b
    }
    private def getTaskTree() {
        def taskTree
        PlatformUI.workbench.display.syncExec {
            tree.expandAll()
            def root = tree.tree
            taskTree = getChildren(root)
        }
        return taskTree
    }

    private def getChildren(Widget item) {
        Item[] children = tree.getChildren(item)
        if (children.every { tree.getChildren(it).length == 0 }) {
            return children.collect { it.text }
        } else {
            return children.collectEntries {
                [(it.text) : getChildren(it) ]
            }
        }
    }

    private def reloadTaskView() {
        PlatformUI.workbench.display.syncExec {
            view.reload(FetchStrategy.FORCE_RELOAD)
        }
        waitForGradleJobsToFinish()
    }
}
