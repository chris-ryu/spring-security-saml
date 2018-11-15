/*
 * Copyright 2002-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.springframework.security.saml.spi.keycloak;

import java.io.Reader;
import java.io.StringReader;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.X509Certificate;

import org.springframework.security.saml.SamlException;
import org.springframework.security.saml.saml2.SignableSaml2Object;
import org.springframework.security.saml.saml2.authentication.Assertion;
import org.springframework.security.saml.saml2.authentication.AuthenticationRequest;
import org.springframework.security.saml.saml2.authentication.Response;
import org.springframework.security.saml.saml2.key.KeyData;
import org.springframework.security.saml.saml2.metadata.Metadata;
import org.springframework.security.saml.saml2.signature.CanonicalizationMethod;
import org.springframework.security.saml.spi.SamlKeyStoreProvider;

import org.keycloak.saml.common.constants.JBossSAMLConstants;
import org.keycloak.saml.common.constants.JBossSAMLURIConstants;
import org.keycloak.saml.processing.core.util.SignatureUtilTransferObject;
import org.keycloak.saml.processing.core.util.XMLSignatureUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import static org.keycloak.saml.common.constants.JBossSAMLConstants.ASSERTION;
import static org.keycloak.saml.common.constants.JBossSAMLConstants.ISSUER;
import static org.keycloak.saml.common.constants.JBossSAMLURIConstants.ASSERTION_NSURI;
import static org.keycloak.saml.common.util.DocumentUtil.getDocument;
import static org.keycloak.saml.common.util.DocumentUtil.getDocumentAsString;
import static org.keycloak.saml.processing.api.saml.v2.sig.SAML2Signature.configureIdAttribute;

class KeycloakSigner {

	private final SamlKeyStoreProvider provider;

	KeycloakSigner(SamlKeyStoreProvider provider) {
		this.provider = provider;
	}

	private String sign(Response response, Document document) {
		try {
			//sign each assertion
			for (Assertion a : response.getAssertions()) {
				Element e = findAssertionById(a.getId(), document);
				SignatureUtilTransferObject sig = getSignatureObject(a);
				sig.setDocumentToBeSigned(document);
				sig.setNextSibling(getIssuerSibling(e));
				document = XMLSignatureUtil.sign(sig, CanonicalizationMethod.ALGO_ID_C14N_EXCL_OMIT_COMMENTS.toString());
			}
			//sign the response itself
			if (response.getSigningKey() != null) {
				SignatureUtilTransferObject sig = getSignatureObject(response);
				sig.setDocumentToBeSigned(document);
				sig.setNextSibling(getIssuerSibling(document));
				document = XMLSignatureUtil.sign(sig, CanonicalizationMethod.ALGO_ID_C14N_EXCL_OMIT_COMMENTS.toString());
			}
			return getDocumentAsString(document);
		} catch (Exception e) {
			throw new SamlException(e);
		}
	}

	String sign(SignableSaml2Object signable, String xml) {
		try {

			Reader xmlReader = new StringReader(xml);
			Document document = getDocument(xmlReader);
			configureIdAttribute(document);
			if (signable instanceof Metadata) {
			}
			else if (signable instanceof AuthenticationRequest) {
			}
			else if (signable instanceof Assertion) {
			}
			else if (signable instanceof Response) {
				return sign((Response)signable, document);
			}
			else {
				throw new UnsupportedOperationException("Unable to sign class:" + signable.getClass());
			}
			SignatureUtilTransferObject sig = getSignatureObject(signable);
			sig.setDocumentToBeSigned(document);
			sig.setNextSibling(getIssuerSibling(document));
			document = XMLSignatureUtil.sign(sig, CanonicalizationMethod.ALGO_ID_C14N_EXCL_OMIT_COMMENTS.toString());
			return getDocumentAsString(document);
		} catch (Exception e) {
			throw new SamlException(e);
		}
	}

	private SignatureUtilTransferObject getSignatureObject(SignableSaml2Object so)
		throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException {
		SignatureUtilTransferObject sig = new SignatureUtilTransferObject();
		sig.setDigestMethod(so.getDigest().toString());
		sig.setReferenceURI("#"+so.getId());
		sig.setSignatureMethod(so.getAlgorithm().toString());
		KeyInfo info = new KeyInfo(so.getSigningKey());
		sig.setX509Certificate(info.getCertificate());
		sig.setKeyPair(info.getKeyPair());
		sig.setKeyName(so.getSigningKey().getName());
		return sig;
	}

	private Element findAssertionById(String id, Document doc) {
		NodeList nl = doc.getElementsByTagNameNS(ASSERTION_NSURI.get(), ASSERTION.get());
		for (int i=0; i<nl.getLength(); i++) {
			Element n = (Element)nl.item(i);
			if (n.getAttribute("ID").equals(id)) {
				return n;
			}
		}
		return null;
	}

	private Node getIssuerSibling(Document doc) {
		// Find the sibling of Issuer
		NodeList nl = doc.getElementsByTagNameNS(JBossSAMLURIConstants.ASSERTION_NSURI.get(), JBossSAMLConstants.ISSUER.get());
		if (nl.getLength() > 0) {
			Node issuer = nl.item(0);
			return issuer.getNextSibling();
		}
		return null;
	}

	private Node getIssuerSibling(Element node) {
		// Find the sibling of Issuer
		NodeList nl = node.getElementsByTagNameNS(ASSERTION_NSURI.get(), ISSUER.get());
		if (nl.getLength() > 0) {
			Node issuer = nl.item(0);
			return issuer.getNextSibling();
		}
		return null;
	}

	private class KeyInfo {
		private final KeyPair keyPair;
		private X509Certificate certificate;

		KeyInfo(KeyData key) throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException {
			KeyStore keyStore = provider.getKeyStore(key, key.getName().toCharArray());
			PrivateKey privateKey = (PrivateKey)keyStore.getKey(key.getName(), key.getPassphrase().toCharArray());
			certificate = (X509Certificate) keyStore.getCertificate(key.getName());
			keyPair = new KeyPair(certificate.getPublicKey(), privateKey);
		}
		KeyPair getKeyPair() {
			return keyPair;
		}

		X509Certificate getCertificate() {
			return certificate;
		}
	}
}
