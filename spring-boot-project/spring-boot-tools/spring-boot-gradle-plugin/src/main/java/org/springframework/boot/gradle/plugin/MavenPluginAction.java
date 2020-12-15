/*
 * Copyright 2012-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.gradle.plugin;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.tasks.Upload;

/**
 * {@link Action} that is executed in response to the
 * {@link org.gradle.api.plugins.MavenPlugin} being applied.
 *
 * @author Andy Wilkinson
 */
final class MavenPluginAction implements PluginApplicationAction {

	private final String uploadTaskName;

	MavenPluginAction(String uploadTaskName) {
		this.uploadTaskName = uploadTaskName;
	}

	@Override
	public String getPluginId() {
		return "maven";
	}

	@Override
	public void execute(Project project) {
		project.getTasks().withType(Upload.class, (upload) -> {
			if (this.uploadTaskName.equals(upload.getName())) {
				project.afterEvaluate((evaluated) -> clearConfigurationMappings(upload));
			}
		});
	}

	@SuppressWarnings("deprecation")
	private void clearConfigurationMappings(Upload upload) {
		upload.getRepositories().withType(org.gradle.api.artifacts.maven.MavenResolver.class,
				(resolver) -> resolver.getPom().getScopeMappings().getMappings().clear());
	}

}
