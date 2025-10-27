/*
 * SPDX-FileCopyrightText: 2025 Neoteric OS
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.internal.util.evolution;

import android.security.keystore.KeyProperties;
import android.system.keystore2.KeyEntryResponse;
import android.system.keystore2.KeyMetadata;

import com.android.internal.org.bouncycastle.asn1.ASN1Sequence;
import com.android.internal.org.bouncycastle.asn1.ASN1Primitive;
import com.android.internal.org.bouncycastle.asn1.DERNull;
import com.android.internal.org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import com.android.internal.org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import com.android.internal.org.bouncycastle.asn1.pkcs.RSAPrivateKey;
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
import java.security.spec.RSAPrivateCrtKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @hide
 */
public class KeyboxUtils {

    public static byte[] decodePemOrBase64(String input) {
        String base64 = input
                .replaceAll("-----BEGIN [^-]+-----", "")
                .replaceAll("-----END [^-]+-----", "")
                .replaceAll("\\s+", "");
        return Base64.getDecoder().decode(base64);
    }

    public static PrivateKey parsePrivateKey(String encodedKey, String algorithm) throws Exception {
        byte[] keyBytes = decodePemOrBase64(encodedKey);
        ASN1Primitive primitive = ASN1Primitive.fromByteArray(keyBytes);
        if (KeyProperties.KEY_ALGORITHM_EC.equalsIgnoreCase(algorithm)) {
            try {
                // Try parsing as PKCS#8
                PrivateKeyInfo info = PrivateKeyInfo.getInstance(primitive);
                return KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_EC).generatePrivate(new PKCS8EncodedKeySpec(info.getEncoded()));
            } catch (Exception e) {
                // Possibly SEC1 / PKCS#1 EC
                ASN1Sequence seq = ASN1Sequence.getInstance(primitive);
                ECPrivateKey ecPrivateKey = ECPrivateKey.getInstance(seq);
                AlgorithmIdentifier algId = new AlgorithmIdentifier(X9ObjectIdentifiers.id_ecPublicKey, ecPrivateKey.getParameters());
                PrivateKeyInfo privInfo = new PrivateKeyInfo(algId, ecPrivateKey);
                PKCS8EncodedKeySpec pkcs8Spec = new PKCS8EncodedKeySpec(privInfo.getEncoded());
                return KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_EC).generatePrivate(pkcs8Spec);
            }
        } else if (KeyProperties.KEY_ALGORITHM_RSA.equalsIgnoreCase(algorithm)) {
            try {
                // Try parsing as PKCS#8
                return KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_RSA).generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
            } catch (Exception e) {
                // Parse as PKCS#1
                RSAPrivateKey rsaKey = RSAPrivateKey.getInstance(primitive);
                AlgorithmIdentifier algId = new AlgorithmIdentifier(PKCSObjectIdentifiers.rsaEncryption, DERNull.INSTANCE);
                PrivateKeyInfo privInfo = new PrivateKeyInfo(algId, rsaKey);
                PKCS8EncodedKeySpec pkcs8Spec = new PKCS8EncodedKeySpec(privInfo.getEncoded());
                return KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_RSA).generatePrivate(pkcs8Spec);
            }
        } else {
            throw new IllegalArgumentException("Unsupported algorithm: " + algorithm);
        }
    }

    public static byte[] getCertificateChain(String algorithm) throws Exception {
        String[] chain = KeyProperties.KEY_ALGORITHM_EC.equals(algorithm)
                ? KeyProviderManager.getProvider().getEcCertificateChain()
                : KeyProviderManager.getProvider().getRsaCertificateChain();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (String cert : chain) out.write(decodePemOrBase64(cert));
        return out.toByteArray();
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
        String cert = KeyProperties.KEY_ALGORITHM_EC.equals(algorithm)
                ? provider.getEcCertificateChain()[0]
                : provider.getRsaCertificateChain()[0];

        byte[] certBytes = decodePemOrBase64(cert);

        return new X509CertificateHolder(certBytes);
    }
}
