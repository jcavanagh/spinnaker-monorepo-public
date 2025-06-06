/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.front50.model.SqlStorageService
import com.netflix.spinnaker.kork.sql.config.DefaultSqlConfiguration
import com.netflix.spinnaker.kork.sql.config.SqlProperties
import java.time.Clock
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@Configuration
@ConditionalOnProperty("sql.enabled")
@EnableConfigurationProperties(Front50SqlProperties::class)
@Import(DefaultSqlConfiguration::class)
class SqlConfiguration {

  @Bean
  fun sqlStorageService(
    objectMapper: ObjectMapper,
    registry: Registry,
    jooq: DSLContext,
    sqlProperties: SqlProperties,
    front50SqlProperties: Front50SqlProperties
  ): SqlStorageService =
    SqlStorageService(
      objectMapper,
      registry,
      jooq,
      Clock.systemDefaultZone(),
      sqlProperties.retries,
      1000,
      if (sqlProperties.connectionPools.keys.size > 1)
        sqlProperties.connectionPools.filter { it.value.default }.keys.first() else sqlProperties.connectionPools.keys.first(),
      front50SqlProperties
    )

  @Bean
  @ConditionalOnProperty("sql.enabled", "sql.secondary.enabled")
  fun secondarySqlStorageService(
    objectMapper: ObjectMapper,
    registry: Registry,
    @Autowired(required = false) @Qualifier("secondaryJooq") secondaryJooq: DSLContext?,
    jooq: DSLContext,
    sqlProperties: SqlProperties,
    front50SqlProperties: Front50SqlProperties
  ): SqlStorageService {
    val effectiveJooq = secondaryJooq ?: jooq
    return SqlStorageService(
      objectMapper,
      registry,
      effectiveJooq,
      Clock.systemDefaultZone(),
      sqlProperties.retries,
      1000,
      sqlProperties.connectionPools.filter { !it.value.default }.keys.first(),
      front50SqlProperties
    )
  }
}
