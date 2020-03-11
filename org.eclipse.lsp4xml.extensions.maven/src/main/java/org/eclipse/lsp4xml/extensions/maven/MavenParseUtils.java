/*******************************************************************************
 * Copyright (c) 2020 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4xml.extensions.maven;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.model.Dependency;
import org.eclipse.lsp4xml.dom.DOMNode;

public class MavenParseUtils {

	public static Dependency parseArtifact(DOMNode node) {
		Dependency res = new Dependency();
		try {
			for (DOMNode tag : node.getParentElement().getChildren()) {
				if (tag != null && tag.hasChildNodes() && !tag.getChild(0).getNodeValue().trim().isEmpty()) {
					String value = tag.getChild(0).getNodeValue(); 
					switch (tag.getLocalName()) {
					case "groupId":
						res.setGroupId(value);
						break;
					case "artifactId":
						res.setArtifactId(value);
						break;
					case "version":
						res.setVersion(value);
						break;
					case "scope":
						res.setScope(value);
						break;
					case "type":
						res.setType(value);
						break;
					case "classifier":
						res.setClassifier(value);
						break;
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Error parsing Artifact");
		}
		return res;
	}
}
