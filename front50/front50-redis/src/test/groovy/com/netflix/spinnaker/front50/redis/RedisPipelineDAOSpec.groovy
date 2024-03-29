/*
 * Copyright 2016 Pivotal, Inc.
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

package com.netflix.spinnaker.front50.redis

import com.netflix.spinnaker.kork.web.exceptions.NotFoundException

import com.netflix.spinnaker.front50.api.model.pipeline.Pipeline;
import com.netflix.spinnaker.front50.pipeline.PipelineDAOSpec
import com.netflix.spinnaker.front50.redis.config.EmbeddedRedisConfig
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource

@SpringBootTest(classes = [RedisConfig, EmbeddedRedisConfig])
@TestPropertySource(properties = ["spinnaker.redis.enabled = true"])
class RedisPipelineDAOSpec extends PipelineDAOSpec<RedisPipelineDAO> {
  @Autowired
  RedisPipelineDAO redisPipelineDAO

  @Override
  RedisPipelineDAO getInstance() {
    return redisPipelineDAO
  }

  void cleanup() {
    deleteAll()
  }

  def "should support standard create/refresh/findAll/delete behaviors"() {
    given:
    def pipeline = new Pipeline([
      application: "app1",
      name       : "pipeline1"
    ])
    def newPipeline = redisPipelineDAO.create("app1", pipeline)

    when:
    def foundPipelineId = redisPipelineDAO.getPipelineId("app1", "pipeline1")
    redisPipelineDAO.findById(foundPipelineId).id == foundPipelineId

    then:
    foundPipelineId == newPipeline.id

    when:
    def foundPipelineByName = redisPipelineDAO.getPipelineByName("app1", "pipeline1", true)

    then:
    foundPipelineByName.id == pipeline.id
    foundPipelineByName.createTs == pipeline.createTs
    foundPipelineByName.email == pipeline.email
    foundPipelineByName.lastModified == pipeline.lastModified
    foundPipelineByName.updateTs == pipeline.updateTs

    when:
    redisPipelineDAO.getPipelineByName("app1", "does-not-exist", true)

    then:
    thrown NotFoundException

    when:
    def findPipelines = redisPipelineDAO.all()

    then:
    findPipelines.size() == 1
    findPipelines[0].id == pipeline.id
    findPipelines[0].createTs == pipeline.createTs
    findPipelines[0].email == pipeline.email
    findPipelines[0].lastModified == pipeline.lastModified
    findPipelines[0].updateTs == pipeline.updateTs

    when:
    def pipelinesByApplication = redisPipelineDAO.getPipelinesByApplication('app1')

    then:
    pipelinesByApplication.size() == 1
    pipelinesByApplication[0].id == pipeline.id
    pipelinesByApplication[0].createTs == pipeline.createTs
    pipelinesByApplication[0].email == pipeline.email
    pipelinesByApplication[0].lastModified == pipeline.lastModified
    pipelinesByApplication[0].updateTs == pipeline.updateTs

    when:
    def updatedPipeline = pipelinesByApplication[0]
    updatedPipeline.name = "foo"
    redisPipelineDAO.update(foundPipelineId, updatedPipeline)

    then:
    redisPipelineDAO.findById(foundPipelineId).name == updatedPipeline.name

    when:
    redisPipelineDAO.delete(foundPipelineId)
    redisPipelineDAO.findById(foundPipelineId)

    then:
    thrown(NotFoundException)

    then:
    redisPipelineDAO.all().isEmpty()

    when:
    redisPipelineDAO.bulkImport([
      new Pipeline([
        name       : "app1",
        email      : "greg@example.com",
        application: 'testapp'
      ]),
      new Pipeline([
        name       : "app2",
        email      : "mark@example.com",
        application: 'testapp'
      ])
    ])

    then:
    def appPipelines = redisPipelineDAO.all()
    appPipelines.size() == 2
    appPipelines.collect { it.name }.containsAll(['app1', 'app2'])
    appPipelines.collect {
      it.email
    }.containsAll(['greg@example.com', 'mark@example.com'])

    expect:
    redisPipelineDAO.healthy == true
  }


  void deleteAll() {
    redisPipelineDAO.redisTemplate.delete(RedisPipelineDAO.BOOK_KEEPING_KEY)
  }

}
