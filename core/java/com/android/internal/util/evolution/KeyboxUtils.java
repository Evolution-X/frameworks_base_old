/*
 * SPDX-FileCopyrightText: 2025 Neoteric OS
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.internal.util.evolution;

import android.security.keystore.KeyProperties;
import android.system.keystore2.KeyEntryResponse;
import android.system.keystore2.KeyMetadata;
import android.os.SystemProperties;
import android.util.Log;

import com.android.internal.org.bouncycastle.asn1.ASN1Sequence;
import com.android.internal.org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import com.android.internal.org.bouncycastle.asn1.sec.ECPrivateKey;
import com.android.internal.org.bouncycastle.asn1.x9.X9ObjectIdentifiers;
import com.android.internal.org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import com.android.internal.org.bouncycastle.cert.X509CertificateHolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @hide
 */
public class KeyboxUtils {

    private static final String TAG = "KeyboxUtils";
    private static final Map<Key, KeyEntryResponse> response = new HashMap<>();
    public static record Key(int uid, String alias) {}

    private static void dlog(String msg) {
        if (SystemProperties.getBoolean("persist.sys.keybox_debug", false)) {
            Log.d(TAG, msg);
        }
    }

    public static PrivateKey parsePrivateKey(String encodedKey, String algorithm) throws Exception {
        // TODO: bug: InvalidKeySpecException - convert priv keys to pkcs8 here. we're using a script in playintegrity fix repo to convert keybox priv keys to pksc8
        byte[] keyBytes = null;
        try {
            keyBytes = Base64.getDecoder().decode(encodedKey);
            PKCS8EncodedKeySpec pkcs8Spec = new PKCS8EncodedKeySpec(keyBytes);
            PrivateKey privateKey = KeyFactory.getInstance(algorithm).generatePrivate(pkcs8Spec);
            dlog("Private key parsed successfully.");
            dlog("Algorithm: " + privateKey.getAlgorithm());
            dlog("Format: " + privateKey.getFormat());
            dlog("Key class: " + privateKey.getClass().getName());
            return privateKey;
        } catch (Exception e) {
            dlog("Failed to parse private key.");
            dlog("Algorithm: " + algorithm);
            if (encodedKey != null) {
                dlog("Encoded key (first 100 chars): " +
                        encodedKey.substring(0, Math.min(encodedKey.length(), 100)));
            }
            if (keyBytes != null) {
                String keySnippet = Base64.getEncoder().encodeToString(keyBytes);
                dlog("Decoded key bytes (first 64 base64 chars): " +
                        keySnippet.substring(0, Math.min(keySnippet.length(), 64)));
            }
            throw e;
        }
    }

    public static X509Certificate parseCertificate(String encodedCert) throws Exception {
        byte[] certBytes = null;
        try {
            certBytes = Base64.getDecoder().decode(encodedCert);
            return (X509Certificate) CertificateFactory
                    .getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(certBytes));
        } catch (Exception e) {
            dlog("Failed to parse certificate. Encoded input (first 100 chars): "
                    + encodedCert.substring(0, Math.min(encodedCert.length(), 100)));
            if (certBytes != null) {
                dlog("Base64-decoded cert (first 64 bytes): "
                        + Base64.getEncoder().encodeToString(certBytes).substring(0, 64));
            }
            throw e;
        }
    }

    public static List<Certificate> getCertificateChain(String algorithm) throws Exception {
        IKeyboxProvider provider = KeyProviderManager.getProvider();
        String[] certChainPem = KeyProperties.KEY_ALGORITHM_EC.equals(algorithm)
                ? provider.getEcCertificateChain()
                : provider.getRsaCertificateChain();

        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        List<Certificate> certs = new ArrayList<>();

        for (String certPem : certChainPem) {
            certs.add(parseCertificate(certPem));
        }

        return certs;
    }

    public static void putCertificateChain(KeyEntryResponse response, Certificate[] chain) throws Exception {
        putCertificateChain(response.metadata, chain);
    }

    public static void putCertificateChain(KeyMetadata metadata, Certificate[] chain) throws Exception {
        metadata.certificate = chain[0].getEncoded();
        var output = new ByteArrayOutputStream();
        for (int i = 1; i < chain.length; i++) {
            output.write(chain[i].getEncoded());
        }
        metadata.certificateChain = output.toByteArray();
    }

    public static X509Certificate getCertificateFromHolder(X509CertificateHolder holder) throws Exception {
        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
        ByteArrayInputStream in = new ByteArrayInputStream(holder.getEncoded());
        return (X509Certificate) certFactory.generateCertificate(in);
    }

    public static PrivateKey getPrivateKey(String algorithm) throws Exception {
        IKeyboxProvider provider = KeyProviderManager.getProvider();
        String privateKeyEncoded = KeyProperties.KEY_ALGORITHM_EC.equals(algorithm)
                ? provider.getEcPrivateKey()
                : provider.getRsaPrivateKey();

        return parsePrivateKey(privateKeyEncoded, algorithm);
    }

    public static X509CertificateHolder getCertificateHolder(String algorithm) throws Exception {
        IKeyboxProvider provider = KeyProviderManager.getProvider();
        String certPem = KeyProperties.KEY_ALGORITHM_EC.equals(algorithm)
                ? provider.getEcCertificateChain()[0]
                : provider.getRsaCertificateChain()[0];

        X509Certificate parsedCert = parseCertificate(certPem);
        return new X509CertificateHolder(parsedCert.getEncoded());
    }

    public static void append(int uid, String a, KeyEntryResponse c) {
        response.put(new Key(uid, a), c);
    }

    public static KeyEntryResponse retrieve(int uid, String a) {
        return response.get(new Key(uid, a));
    }
}
