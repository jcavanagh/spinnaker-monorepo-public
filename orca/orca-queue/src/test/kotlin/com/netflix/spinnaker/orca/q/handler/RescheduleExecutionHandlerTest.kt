/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.q.handler

import com.netflix.spinnaker.orca.TaskResolver
import com.netflix.spinnaker.orca.api.pipeline.Task
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.test.pipeline
import com.netflix.spinnaker.orca.api.test.stage
import com.netflix.spinnaker.orca.api.test.task
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.RescheduleExecution
import com.netflix.spinnaker.orca.q.RunTask
import com.netflix.spinnaker.orca.q.TasksProvider
import com.netflix.spinnaker.q.Queue
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyNoMoreInteractions
import com.nhaarman.mockito_kotlin.whenever
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.lifecycle.CachingMode
import org.jetbrains.spek.subject.SubjectSpek
import java.time.Duration

object RescheduleExecutionHandlerTest : SubjectSpek<RescheduleExecutionHandler>({

  val queue: Queue = mock()
  val repository: ExecutionRepository = mock()
  val taskResolver = TaskResolver(TasksProvider(emptyList()))

  subject(CachingMode.GROUP) {
    RescheduleExecutionHandler(queue, repository, taskResolver)
  }

  fun resetMocks() = reset(queue, repository)

  describe("reschedule an execution") {
    val pipeline = pipeline {
      application = "spinnaker"
      status = ExecutionStatus.RUNNING
      stage {
        refId = "1"
        status = ExecutionStatus.SUCCEEDED
      }
      stage {
        refId = "2a"
        requisiteStageRefIds = listOf("1")
        status = ExecutionStatus.RUNNING
        task {
          id = "4"
          status = ExecutionStatus.RUNNING
        }
      }
      stage {
        refId = "2b"
        requisiteStageRefIds = listOf("1")
        status = ExecutionStatus.RUNNING
        task {
          id = "5"
          status = ExecutionStatus.RUNNING
        }
      }
      stage {
        refId = "3"
        requisiteStageRefIds = listOf("2a", "2b")
        status = ExecutionStatus.NOT_STARTED
      }
    }
    val message = RescheduleExecution(pipeline.type, pipeline.id, pipeline.application)

    beforeGroup {
      whenever(repository.retrieve(pipeline.type, pipeline.id)) doReturn pipeline
    }

    afterGroup(::resetMocks)

    action("the handler receives a message") {
      subject.handle(message)
    }

    @Suppress("UNCHECKED_CAST")
    it("it updates the time for each running task") {
      val stage2a = pipeline.stageByRef("2a")
      val stage2b = pipeline.stageByRef("2b")
      val task4 = stage2a.taskById("4")
      val task5 = stage2b.taskById("5")

      val messageTask4 = RunTask(message, stage2a.id, task4.id, Class.forName(task4.implementingClass) as Class<out Task>)
      val messageTask5 = RunTask(message, stage2b.id, task5.id, Class.forName(task5.implementingClass) as Class<out Task>)

      verify(queue).ensure(messageTask4, Duration.ZERO)
      verify(queue).reschedule(messageTask4)
      verify(queue).ensure(messageTask5, Duration.ZERO)
      verify(queue).reschedule(messageTask5)
      verifyNoMoreInteractions(queue)
    }
  }
})
