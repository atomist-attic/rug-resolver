package com.atomist.rug.resolver;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureList;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.jcajce.JcaPGPObjectFactory;
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentVerifierBuilderProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.Provider;
import java.security.Security;

/**
 * Verify gpg signature of files
 */
public class GpgSignatureVerifier {

    private PGPPublicKeyRingCollection pgpPubRingCollection;

    public GpgSignatureVerifier(InputStream publicKey) throws IOException, PGPException {
        Provider provider = new BouncyCastleProvider();
        Security.addProvider(provider);
        pgpPubRingCollection = new PGPPublicKeyRingCollection(PGPUtil.getDecoderStream(publicKey), new JcaKeyFingerprintCalculator());
    }

    public boolean verify(File data, File signature) throws Exception {
        return verify(new FileInputStream(data), new FileInputStream(signature));
    }

    /**
     * Verify a signature. Only return true if signature matches calculated signature of signedData
     * and if it was signed by the publicKey
     *
     * @param signedData
     * @param signature
     * @return
     */
    public boolean verify(InputStream signedData, InputStream signature) {
        try {
            signature = PGPUtil.getDecoderStream(signature);
            JcaPGPObjectFactory pgpFact = new JcaPGPObjectFactory(signature);
            PGPSignature sig = ((PGPSignatureList) pgpFact.nextObject()).get(0);
            PGPPublicKey key = pgpPubRingCollection.getPublicKey(sig.getKeyID());
            sig.init(new JcaPGPContentVerifierBuilderProvider().setProvider("BC"), key);
            byte[] buff = new byte[1024];
            int read = 0;
            while ((read = signedData.read(buff)) != -1) {
                sig.update(buff, 0, read);
            }
            signedData.close();
            return sig.verify();
        } catch (Exception ex) {
            //can we put a logger here please?
            return false;
        }
    }
}
