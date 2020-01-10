package org.eclipse.lsp4xml.extensions.maven;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4xml.dom.DOMDocument;
import org.eclipse.lsp4xml.dom.DOMElement;
import org.eclipse.lsp4xml.dom.DOMNode;
import org.eclipse.lsp4xml.services.extensions.diagnostics.IDiagnosticsParticipant;
import org.eclipse.lsp4xml.utils.XMLPositionUtility;

public class MavenDiagnosticParticipant implements IDiagnosticsParticipant {

	@Override
	public void doDiagnostics(DOMDocument xmlDocument, List<Diagnostic> diagnostics, CancelChecker monitor) {
		DOMElement documentElement = xmlDocument.getDocumentElement();
		HashMap<String, Function<DiagnosticRequest, Diagnostic>> tagDiagnostics = configureDiagnosticFunctions(
				xmlDocument);

		Deque<DOMNode> nodes = new ArrayDeque<>();
		for (DOMNode node : documentElement.getChildren()) {
			nodes.push(node);
		}
		while (!nodes.isEmpty()) {
			DOMNode node = nodes.pop();
			for (String tagToValidate : tagDiagnostics.keySet()) {
				if (node.getLocalName() != null && node.getLocalName().equals(tagToValidate)) {
					Diagnostic diagnostic = null;
					try {
						diagnostic = tagDiagnostics.get(tagToValidate)
								.apply(new DiagnosticRequest(node, xmlDocument, diagnostics));
					} catch (Exception e) {
						// TODO: Use plug-in error logger
						e.printStackTrace();
					}

					if (diagnostic != null) {
						diagnostics.add(diagnostic);
					}
				}
			}
			if (node.hasChildNodes()) {
				for (DOMNode childNode : node.getChildren()) {
					nodes.push(childNode);
				}
			}
		}
	}

	private HashMap<String, Function<DiagnosticRequest, Diagnostic>> configureDiagnosticFunctions(
			DOMDocument xmlDocument) {
		Function<DiagnosticRequest, Diagnostic> versionFunc = VersionValidator::validateVersion;
		// Below is a mock Diagnostic function which creates a warning between inside
		// <configuration> tags
		Function<DiagnosticRequest, Diagnostic> configFunc = diagnosticReq -> new Diagnostic(diagnosticReq.getRange(),
				"Configuration Error", DiagnosticSeverity.Warning, xmlDocument.getDocumentURI(), "XML");

		HashMap<String, Function<DiagnosticRequest, Diagnostic>> tagDiagnostics = new HashMap<>();
		tagDiagnostics.put("version", versionFunc);
		tagDiagnostics.put("configuration", configFunc);
		return tagDiagnostics;
	}

}