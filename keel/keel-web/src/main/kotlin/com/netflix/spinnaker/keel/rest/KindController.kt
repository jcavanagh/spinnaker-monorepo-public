package com.netflix.spinnaker.keel.rest

import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.support.ExtensionRegistry
import com.netflix.spinnaker.keel.api.support.extensionsOf
import com.netflix.spinnaker.keel.yaml.APPLICATION_YAML_VALUE
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(path = ["/kinds"])
class KindController(
  val extensionRegistry: ExtensionRegistry
) {
  @GetMapping(produces = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE])
  fun get(): Set<String> =
    extensionRegistry
      .extensionsOf<ResourceSpec>()
      .entries
      .map { it.key }
      .toSortedSet()
}
