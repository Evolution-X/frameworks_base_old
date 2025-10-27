/*
 * SPDX-FileCopyrightText: 2024 Paranoid Android
 * SPDX-FileCopyrightText: 2025 Neoteric OS
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.internal.util.evolution;

import android.app.ActivityThread;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.security.KeyChain;
import android.security.keystore.KeyProperties;
import android.system.keystore2.KeyEntryResponse;
import android.util.Base64;
import android.util.Log;

import com.android.internal.org.bouncycastle.asn1.ASN1Boolean;
import com.android.internal.org.bouncycastle.asn1.ASN1Encodable;
import com.android.internal.org.bouncycastle.asn1.ASN1EncodableVector;
import com.android.internal.org.bouncycastle.asn1.ASN1Enumerated;
import com.android.internal.org.bouncycastle.asn1.ASN1Integer;
import com.android.internal.org.bouncycastle.asn1.ASN1ObjectIdentifier;
import com.android.internal.org.bouncycastle.asn1.ASN1OctetString;
import com.android.internal.org.bouncycastle.asn1.ASN1Sequence;
import com.android.internal.org.bouncycastle.asn1.ASN1TaggedObject;
import com.android.internal.org.bouncycastle.asn1.DEROctetString;
import com.android.internal.org.bouncycastle.asn1.DERSequence;
import com.android.internal.org.bouncycastle.asn1.DERTaggedObject;
import com.android.internal.org.bouncycastle.asn1.x509.Extension;
import com.android.internal.org.bouncycastle.cert.X509CertificateHolder;
import com.android.internal.org.bouncycastle.cert.X509v3CertificateBuilder;
import com.android.internal.org.bouncycastle.operator.ContentSigner;
import com.android.internal.org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

/**
 * @hide
 */
public class KeyboxImitationHooks {

    private static final String TAG = "KeyboxImitationHooks";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private static final ASN1ObjectIdentifier KEY_ATTESTATION_OID = new ASN1ObjectIdentifier(
            "1.3.6.1.4.1.11129.2.1.17");

    public static KeyEntryResponse onGetKeyEntry(KeyEntryResponse response) {
        if (!KeyProviderManager.isKeyboxAvailable()) {
            dlog("Key attestation spoofing is disabled because no keybox is defined to spoof");
            return response;
        }

        if (response == null || response.metadata == null) return response;

        try {
            if (response.metadata.certificate == null) {
                Log.e(TAG, "Certificate is null, skipping modification");
                return response;
            }

            X509Certificate certificate = KeyChain.toCertificate(response.metadata.certificate);
            if (certificate.getExtensionValue(KEY_ATTESTATION_OID.getId()) == null) {
                Log.e(TAG, "Key attestation OID not found, skipping modification");
                return response;
            }

            String keyAlgorithm = certificate.getPublicKey().getAlgorithm();
            response.metadata.certificate = modifyLeafCertificate(certificate, keyAlgorithm);
            response.metadata.certificateChain = KeyboxUtils.getCertificateChain(keyAlgorithm);
        } catch (Exception e) {
            Log.e(TAG, "Error in onGetKeyEntry", e);
        }

        return response;
    }

    private static byte[] modifyLeafCertificate(X509Certificate leafCertificate,
            String keyAlgorithm) throws Exception {
        X509CertificateHolder certificateHolder = new X509CertificateHolder(
                leafCertificate.getEncoded());
        Extension keyAttestationExtension = certificateHolder.getExtension(KEY_ATTESTATION_OID);
        ASN1Sequence keyAttestationSequence = ASN1Sequence.getInstance(
                keyAttestationExtension.getExtnValue().getOctets());
        ASN1Encodable[] keyAttestationEncodables = keyAttestationSequence.toArray();
        ASN1Sequence teeEnforcedSequence = (ASN1Sequence) keyAttestationEncodables[7];
        ASN1EncodableVector teeEnforcedVector = new ASN1EncodableVector();

        for (ASN1Encodable teeEnforcedEncodable : teeEnforcedSequence) {
            ASN1TaggedObject taggedObject = (ASN1TaggedObject) teeEnforcedEncodable;
            int tag = taggedObject.getTagNo();
            if (tag == 704 || tag == 705 || tag == 706 || tag == 718 || tag == 719) {
                continue;
            }
            teeEnforcedVector.add(teeEnforcedEncodable);
        }
        
        PrivateKey privateKey = KeyboxUtils.getPrivateKey(keyAlgorithm);
        X509CertificateHolder providerCertHolder = KeyboxUtils.getCertificateHolder(keyAlgorithm);

        X509v3CertificateBuilder certificateBuilder = new X509v3CertificateBuilder(
                providerCertHolder.getSubject(),
                certificateHolder.getSerialNumber(),
                certificateHolder.getNotBefore(),
                certificateHolder.getNotAfter(),
                certificateHolder.getSubject(),
                certificateHolder.getSubjectPublicKeyInfo()
        );

        ContentSigner contentSigner = new JcaContentSignerBuilder(
                leafCertificate.getSigAlgName()).build(privateKey);

        Context context = ActivityThread.currentApplication();
        if (context == null) {
            Log.e(TAG, "Context is null in modifyLeafCertificate");
            return null;
        }
        SecureRandom secureRandom = new SecureRandom();

        String key = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.VBOOT_KEY);
        byte[] verifiedBootKey;
        if (key == null) {
            byte[] randomBytes = new byte[32];
            secureRandom.nextBytes(randomBytes);
            String encoded = Base64.encodeToString(randomBytes, Base64.NO_WRAP);
            Settings.Secure.putString(context.getContentResolver(), Settings.Secure.VBOOT_KEY, encoded);
            verifiedBootKey = randomBytes;
        } else {
            verifiedBootKey = Base64.decode(key, Base64.NO_WRAP);
        }

        String hash = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.VBOOT_HASH);
        byte[] verifiedBootHash;
        if (hash == null) {
            byte[] randomBytes = new byte[32];
            secureRandom.nextBytes(randomBytes);
            String encoded = Base64.encodeToString(randomBytes, Base64.NO_WRAP);
            Settings.Secure.putString(context.getContentResolver(), Settings.Secure.VBOOT_HASH, encoded);
            verifiedBootHash = randomBytes;
        } else {
            verifiedBootHash = Base64.decode(hash, Base64.NO_WRAP);
        }

        ASN1Encodable[] rootOfTrustEncodables = {
                new DEROctetString(verifiedBootKey),
                ASN1Boolean.TRUE,
                new ASN1Enumerated(0),
                new DEROctetString(verifiedBootHash)
        };

        ASN1Sequence newRootOfTrustSequence = new DERSequence(rootOfTrustEncodables);
        ASN1TaggedObject rootOfTrustTaggedObject = new DERTaggedObject(704, newRootOfTrustSequence);
        teeEnforcedVector.add(rootOfTrustTaggedObject);
        teeEnforcedVector.add(new DERTaggedObject(705,
                new ASN1Integer(getOsVersion())));
        teeEnforcedVector.add(new DERTaggedObject(706,
                new ASN1Integer(getPatchLevel())));
        teeEnforcedVector.add(new DERTaggedObject(718,
                new ASN1Integer(getPatchLevelLong())));
        teeEnforcedVector.add(new DERTaggedObject(719,
                new ASN1Integer(getPatchLevelLong())));

        ASN1Sequence newTeeEnforcedSequence = new DERSequence(teeEnforcedVector);
        keyAttestationEncodables[7] = newTeeEnforcedSequence;
        ASN1Sequence newKeyAttestationSequence = new DERSequence(keyAttestationEncodables);
        ASN1OctetString newKeyAttestationOctetString = new DEROctetString(
                newKeyAttestationSequence);
        Extension newKeyAttestationExtension = new Extension(KEY_ATTESTATION_OID, false,
                newKeyAttestationOctetString);

        certificateBuilder.addExtension(newKeyAttestationExtension);

        for (ASN1ObjectIdentifier extensionOID :
                certificateHolder.getExtensions().getExtensionOIDs()) {
            if (KEY_ATTESTATION_OID.getId().equals(extensionOID.getId())) continue;
            certificateBuilder.addExtension(certificateHolder.getExtension(extensionOID));
        }

        return certificateBuilder.build(contentSigner).getEncoded();
    }

    private static int getOsVersion() {
        String release = Build.VERSION.RELEASE;
        int major = 0, minor = 0, patch = 0;

        String[] parts = release.split("\\.");
        if (parts.length > 0) major = Integer.parseInt(parts[0]);
        if (parts.length > 1) minor = Integer.parseInt(parts[1]);
        if (parts.length > 2) patch = Integer.parseInt(parts[2]);

        return major * 10000 + minor * 100 + patch;
    }

    private static int getPatchLevel() {
        return convertPatchLevel(Build.VERSION.SECURITY_PATCH, false);
    }

    private static int getPatchLevelLong() {
        return convertPatchLevel(Build.VERSION.SECURITY_PATCH, true);
    }

    private static int convertPatchLevel(String patchLevel, boolean longFormat) {
        try {
            String[] parts = patchLevel.split("-");
            int year = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            if (longFormat) {
                int day = Integer.parseInt(parts[2]);
                return year * 10000 + month * 100 + day;
            } else {
                return year * 100 + month;
            }
        } catch (Exception e) {
            Log.e(TAG, "Invalid patch level: " + patchLevel, e);
            return 202404;
        }
    }

    private static void dlog(String msg) {
        if (DEBUG) Log.d(TAG, msg);
    }
}
