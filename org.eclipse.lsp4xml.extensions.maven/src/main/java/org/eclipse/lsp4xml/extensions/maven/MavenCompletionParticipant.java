/*******************************************************************************
 * Copyright (c) 2019-2020 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4xml.extensions.maven;

import java.io.File;
import java.io.IOException;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4xml.commons.BadLocationException;
import org.eclipse.lsp4xml.commons.TextDocument;
import org.eclipse.lsp4xml.commons.snippets.SnippetRegistry;
import org.eclipse.lsp4xml.dom.DOMDocument;
import org.eclipse.lsp4xml.dom.DOMElement;
import org.eclipse.lsp4xml.extensions.maven.searcher.ArtifactSearcherManager;
import org.eclipse.lsp4xml.extensions.maven.searcher.LocalSubModuleSearcher;
import org.eclipse.lsp4xml.extensions.maven.searcher.ParentSearcher;
import org.eclipse.lsp4xml.services.extensions.CompletionParticipantAdapter;
import org.eclipse.lsp4xml.services.extensions.ICompletionRequest;
import org.eclipse.lsp4xml.services.extensions.ICompletionResponse;
import org.eclipse.lsp4xml.utils.XMLPositionUtility;

public class MavenCompletionParticipant extends CompletionParticipantAdapter {

	private boolean snippetsLoaded;
	private MavenProjectCache cache;

	public MavenCompletionParticipant(MavenProjectCache cache) {
		this.cache = cache;
	}

	@Override
	public void onXMLContent(ICompletionRequest request, ICompletionResponse response) throws Exception {
		DOMElement parent = request.getParentElement();

		if (parent == null || parent.getLocalName() == null) {
			return;
		}
		//TODO: These two switch cases should be combined into one
		switch (parent.getParentElement().getLocalName()) {
		case "parent":
			collectParentCompletion(request, response);
			break;
		case "plugin":
			break;
		default:
			break;
		}
		switch (parent.getLocalName()) {
		case "scope":
			collectScopeCompletion(request, response);
			break;
		case "groupId":
			if (!parent.getParentElement().getLocalName().equals("parent")){
				collectGroupIdCompletion(request, response);
			}
			break;
		case "module":
			collectSubModuleCompletion(request, response);
			if (!parent.getParentElement().getLocalName().equals("parent")){
				collectGroupIdCompletion(request, response);
			}
			break;
		default:
			initSnippets();
			TextDocument document = parent.getOwnerDocument().getTextDocument();
			int completionOffset = request.getOffset();
			boolean canSupportMarkdown = true; // request.canSupportMarkupKind(MarkupKind.MARKDOWN);
			SnippetRegistry.getInstance()
					.getCompletionItems(document, completionOffset, canSupportMarkdown, context -> {
						if (!"pom.xml".equals(context.getType())) {
							return false;
						}
						return parent.getLocalName().equals(context.getValue());
					}).forEach(completionItem -> response.addCompletionItem(completionItem));
		}
		if (request.getNode().isText()) {
			completeProperties(request, response);
		}
	}

	private void completeProperties(ICompletionRequest request, ICompletionResponse response) {
		DOMDocument xmlDocument = request.getXMLDocument();
		String documentText = xmlDocument.getText();
		int initialPropertyOffset = request.getOffset();
		for (int i = request.getOffset() - 1; i >= request.getNode().getStart(); i--) {
			char currentChar = documentText.charAt(i);
			if (currentChar == '}') {
				// properties area ended, return all properties
				break;
			} else if (currentChar == '$') {
				initialPropertyOffset = i;
				break;
			}
		}
		MavenProject project = cache.getLastSuccessfulMavenProject(request.getXMLDocument());
		if (project != null && project.getProperties() != null) {
			for (Entry<Object, Object> property : project.getProperties().entrySet()) {
				CompletionItem item = new CompletionItem();
				item.setLabel("${" + property.getKey() + '}');
				item.setDocumentation("Default Value: " + property.getValue());
				try {
					TextEdit textEdit = new TextEdit();
					textEdit.setNewText(item.getLabel());
					textEdit.setRange(new Range(xmlDocument.positionAt(initialPropertyOffset), xmlDocument.positionAt(request.getOffset())));
					item.setTextEdit(textEdit);
				} catch (BadLocationException e) {
					e.printStackTrace();
					item.setInsertText(item.getLabel());
				}
				response.addCompletionItem(item, false);
			}
		}
		// TODO add static properties
	}

	private void collectSubModuleCompletion(ICompletionRequest request, ICompletionResponse response) {
		DOMElement node = request.getParentElement();
		DOMDocument doc = request.getXMLDocument();

		Range range = XMLPositionUtility.createRange(node.getStartTagCloseOffset() + 1, node.getEndTagOpenOffset(),
				doc);

		try {
			//TODO: Get the File properly without using substring
			LocalSubModuleSearcher subModuleSearcher = LocalSubModuleSearcher.getInstance();
			subModuleSearcher.setPomFile(new File(doc.getDocumentURI().substring(5)));
			for (String module : subModuleSearcher.getSubModules()) {
				String label = module;
				CompletionItem item = new CompletionItem();
				item.setLabel(label);
				String insertText = label;
				item.setKind(CompletionItemKind.Property);
				item.setDocumentation(Either.forLeft(""));
				item.setFilterText(insertText);
				item.setTextEdit(new TextEdit(range, insertText));
				item.setInsertTextFormat(InsertTextFormat.PlainText);
				response.addCompletionItem(item);
			}
		} catch (IOException | XmlPullParserException e) {
			e.printStackTrace();
		}

	}

	private void initSnippets() {
		if (snippetsLoaded) {
			return;
		}
		try {
			try {
				SnippetRegistry.getInstance()
						.load(MavenCompletionParticipant.class.getResourceAsStream("pom-snippets.json"));
			} catch (IOException e) {
				e.printStackTrace();
			}
		} finally {
			snippetsLoaded = true;
		}

	}

	private void collectParentCompletion(ICompletionRequest request, ICompletionResponse response) {
		DOMElement node = request.getParentElement();
		DOMDocument doc = request.getXMLDocument();
		Range range = XMLPositionUtility.createRange(node.getStartTagCloseOffset() + 1, node.getEndTagOpenOffset(),
				doc);
		try {
			ParentSearcher.getInstance().setPomFile(new java.io.File(doc.getDocumentURI().substring(5)));
		} catch (IOException | XmlPullParserException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		switch (node.getLocalName()) {
		case "artifactId":
			response.addCompletionItem(getParentArtifactId(doc, range));
			break;
		case "groupId":
			response.addCompletionItem(getParentGroupID(doc, range));
			break;
		case "version":
			response.addCompletionItem(getParentVersion(doc, range));
			break;
		default:
			//TODO: Make a snippet that autocompletes the entire parent (artifact, groupid and version)
			break;
		}

	}

	private CompletionItem getParentGroupID(DOMDocument doc, Range range) {
		String label = ParentSearcher.getInstance().getParentGroupId();
		CompletionItem item = new CompletionItem();
		item.setLabel(label);
		String insertText = label;
		item.setKind(CompletionItemKind.Property);
		item.setDocumentation(Either.forLeft("The groupId of the parent maven module."));
		item.setFilterText(insertText);
		item.setTextEdit(new TextEdit(range, insertText));
		item.setInsertTextFormat(InsertTextFormat.PlainText);
		return item;
	}

	private CompletionItem getParentVersion(DOMDocument doc, Range range) {
		String label = ParentSearcher.getInstance().getParentVersion();
		CompletionItem item = new CompletionItem();
		item.setLabel(label);
		String insertText = label;
		item.setKind(CompletionItemKind.Property);
		item.setDocumentation(Either.forLeft("The version of the parent maven module."));
		item.setFilterText(insertText);
		item.setTextEdit(new TextEdit(range, insertText));
		item.setInsertTextFormat(InsertTextFormat.PlainText);
		return item;
	}

	private CompletionItem getParentArtifactId(DOMDocument doc, Range range) {
		String label = ParentSearcher.getInstance().getParentArtifactId();
		CompletionItem item = new CompletionItem();
		item.setLabel(label);
		String insertText = label;
		item.setKind(CompletionItemKind.Property);
		item.setDocumentation(Either.forLeft("The artifactId of the parent maven module."));
		item.setFilterText(insertText);
		item.setTextEdit(new TextEdit(range, insertText));
		item.setInsertTextFormat(InsertTextFormat.PlainText);
		return item;
	}

	private void collectScopeCompletion(ICompletionRequest request, ICompletionResponse response) {
		DOMElement node = request.getParentElement();
		DOMDocument doc = request.getXMLDocument();
		Range range = XMLPositionUtility.createRange(node.getStartTagCloseOffset() + 1, node.getEndTagOpenOffset(),
				doc);

		for (DependencyScope scope : DependencyScope.values()) {
			String label = scope.getName();
			CompletionItem item = new CompletionItem();
			item.setLabel(label);
			String insertText = label;
			item.setKind(CompletionItemKind.Property);
			item.setDocumentation(Either.forLeft(scope.getDescription()));
			item.setFilterText(insertText);
			item.setTextEdit(new TextEdit(range, insertText));
			item.setInsertTextFormat(InsertTextFormat.PlainText);
			response.addCompletionItem(item);
		}
	}

	private void collectGroupIdCompletion(ICompletionRequest request, ICompletionResponse response) {
		DOMElement groupIdElt = request.getParentElement();
		DOMDocument doc = request.getXMLDocument();
		Range range = XMLPositionUtility.createRange(groupIdElt.getStartTagCloseOffset() + 1,
				groupIdElt.getEndTagOpenOffset(), doc);

		// Local
		Set<String> groupIds = ArtifactSearcherManager.getInstance().searchLocalGroupIds(null);
		for (String groupId : groupIds) {

			String label = groupId;
			CompletionItem item = new CompletionItem();
			item.setLabel(label);
			String insertText = label;
			item.setKind(CompletionItemKind.Property);
			// item.setDocumentation(Either.forLeft(scope.getDescription()));
			item.setFilterText(insertText);
			item.setTextEdit(new TextEdit(range, insertText));
			item.setInsertTextFormat(InsertTextFormat.PlainText);
			response.addCompletionItem(item);

		}

		// Central

		// Index

	}
}
