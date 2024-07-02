/*
 * Spinnaker API
 *
 * No description provided (generated by Swagger Codegen https://github.com/swagger-api/swagger-codegen)
 *
 * API version: 1.0.0
 * Generated by: Swagger Codegen (https://github.com/swagger-api/swagger-codegen.git)
 */

package swagger

type SpinnakerPluginDescriptor struct {
	Dependencies []PluginDependency `json:"dependencies,omitempty"`
	License string `json:"license,omitempty"`
	PluginClass string `json:"pluginClass,omitempty"`
	PluginDescription string `json:"pluginDescription,omitempty"`
	PluginId string `json:"pluginId,omitempty"`
	Provider string `json:"provider,omitempty"`
	Requires string `json:"requires,omitempty"`
	Unsafe bool `json:"unsafe"`
	Version string `json:"version,omitempty"`
}
