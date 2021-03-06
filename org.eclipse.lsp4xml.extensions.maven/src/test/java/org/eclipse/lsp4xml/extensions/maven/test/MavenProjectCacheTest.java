/*******************************************************************************
 * Copyright (c) 2019-2020 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4xml.extensions.maven.test;

import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.net.URI;

import org.apache.commons.io.FileUtils;
import org.apache.maven.project.MavenProject;
import org.eclipse.lsp4xml.commons.TextDocument;
import org.eclipse.lsp4xml.dom.DOMDocument;
import org.eclipse.lsp4xml.extensions.maven.MavenPlugin;
import org.eclipse.lsp4xml.extensions.maven.MavenProjectCache;
import org.junit.Test;

public class MavenProjectCacheTest {

	@Test
	public void testSimpleProjectIsParsed() throws Exception {
		URI uri = getClass().getResource("/pom-with-properties.xml").toURI();
		String content = FileUtils.readFileToString(new File(uri), "UTF-8");
		DOMDocument doc = new DOMDocument(new TextDocument(content, uri.toString()), null);
		MavenProjectCache cache = new MavenProjectCache(MavenPlugin.newPlexusContainer());
		MavenProject project = cache.getLastSuccessfulMavenProject(doc);
		assertNotNull(project);
	}
}
