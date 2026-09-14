package com.uci.pkbe.webauthn;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import java.util.Base64;

/** Minimal ES256 platform authenticator for tests (attestation fmt=none). */
public final class SoftPasskey {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Base64.Encoder B64URL = Base64.getUrlEncoder().withoutPadding();

    private final KeyPair keyPair;
    private final byte[] credentialId;
    private final byte[] userHandle;
    private int signCount;

    public SoftPasskey(byte[] userHandle) {
        this.userHandle = userHandle.clone();
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"), new SecureRandom());
            this.keyPair = generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        this.credentialId = new byte[32];
        new SecureRandom().nextBytes(this.credentialId);
    }

    public String create(String challengeBase64Url, String origin, String rpId) {
        try {
            byte[] clientData = clientData("webauthn.create", challengeBase64Url, origin);
            byte[] cose = coseKey((ECPublicKey) keyPair.getPublic());
            byte[] authData = authenticatorData(rpId, true, cose);
            byte[] attestationObject = noneAttestation(authData);
            ObjectNode response = JSON.createObjectNode();
            response.put("clientDataJSON", B64URL.encodeToString(clientData));
            response.put("attestationObject", B64URL.encodeToString(attestationObject));
            ObjectNode credential = JSON.createObjectNode();
            credential.put("id", B64URL.encodeToString(credentialId));
            credential.put("rawId", B64URL.encodeToString(credentialId));
            credential.put("type", "public-key");
            credential.set("response", response);
            credential.set("clientExtensionResults", JSON.createObjectNode());
            return JSON.writeValueAsString(credential);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public String get(String challengeBase64Url, String origin, String rpId) {
        try {
            signCount += 1;
            byte[] clientData = clientData("webauthn.get", challengeBase64Url, origin);
            byte[] authData = authenticatorData(rpId, false, null);
            byte[] signature = signEs256(concat(authData, sha256(clientData)));
            ObjectNode response = JSON.createObjectNode();
            response.put("clientDataJSON", B64URL.encodeToString(clientData));
            response.put("authenticatorData", B64URL.encodeToString(authData));
            response.put("signature", B64URL.encodeToString(signature));
            response.put("userHandle", B64URL.encodeToString(userHandle));
            ObjectNode credential = JSON.createObjectNode();
            credential.put("id", B64URL.encodeToString(credentialId));
            credential.put("rawId", B64URL.encodeToString(credentialId));
            credential.put("type", "public-key");
            credential.set("response", response);
            credential.set("clientExtensionResults", JSON.createObjectNode());
            return JSON.writeValueAsString(credential);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private byte[] authenticatorData(String rpId, boolean attestation, byte[] cose) throws Exception {
        byte[] rpIdHash = sha256(rpId.getBytes(StandardCharsets.UTF_8));
        byte flags = 0x01 | 0x04;
        if (attestation) {
            flags |= 0x40;
        }
        int extra = attestation ? 18 + credentialId.length + cose.length : 0;
        ByteBuffer buffer = ByteBuffer.allocate(37 + extra);
        buffer.put(rpIdHash);
        buffer.put(flags);
        buffer.putInt(signCount);
        if (attestation) {
            buffer.put(new byte[16]);
            buffer.putShort((short) credentialId.length);
            buffer.put(credentialId);
            buffer.put(cose);
        }
        return buffer.array();
    }

    private static byte[] noneAttestation(byte[] authData) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0xA3);
        writeText(out, "fmt");
        writeText(out, "none");
        writeText(out, "attStmt");
        out.write(0xA0);
        writeText(out, "authData");
        writeBytes(out, authData);
        return out.toByteArray();
    }

    private static byte[] coseKey(ECPublicKey publicKey) {
        byte[] x = to32(publicKey.getW().getAffineX());
        byte[] y = to32(publicKey.getW().getAffineY());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0xA5);
        out.write(0x01);
        out.write(0x02);
        out.write(0x03);
        out.write(0x26);
        out.write(0x20);
        out.write(0x01);
        out.write(0x21);
        writeBytes(out, x);
        out.write(0x22);
        writeBytes(out, y);
        return out.toByteArray();
    }

    private static void writeText(ByteArrayOutputStream out, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        out.write(0x60 | bytes.length);
        out.writeBytes(bytes);
    }

    private static void writeBytes(ByteArrayOutputStream out, byte[] bytes) {
        if (bytes.length < 24) {
            out.write(0x40 | bytes.length);
        } else {
            out.write(0x58);
            out.write(bytes.length);
        }
        out.writeBytes(bytes);
    }

    private byte[] signEs256(byte[] payload) throws Exception {
        Signature signature = Signature.getInstance("SHA256withECDSA");
        signature.initSign(keyPair.getPrivate());
        signature.update(payload);
        return signature.sign();
    }

    private static byte[] clientData(String type, String challengeBase64Url, String origin) throws Exception {
        ObjectNode node = JSON.createObjectNode();
        node.put("type", type);
        node.put("challenge", challengeBase64Url);
        node.put("origin", origin);
        node.put("crossOrigin", false);
        return JSON.writeValueAsBytes(node);
    }

    private static byte[] sha256(byte[] data) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(data);
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static byte[] to32(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length == 32) {
            return bytes;
        }
        if (bytes.length == 33 && bytes[0] == 0) {
            return Arrays.copyOfRange(bytes, 1, 33);
        }
        byte[] out = new byte[32];
        int src = Math.max(0, bytes.length - 32);
        int dest = Math.max(0, 32 - bytes.length);
        System.arraycopy(bytes, src, out, dest, Math.min(32, bytes.length));
        return out;
    }
}
