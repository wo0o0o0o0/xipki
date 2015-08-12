/*
 * Copyright (c) 2015 Lijun Liao
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 *
 */

package org.xipki.scep4j.serveremulator;

import java.io.IOException;
import java.math.BigInteger;
import java.security.PrivateKey;
import java.security.cert.CertificateEncodingException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.pkcs.CertificationRequest;
import org.bouncycastle.asn1.pkcs.CertificationRequestInfo;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.CRLReason;
import org.bouncycastle.asn1.x509.Certificate;
import org.bouncycastle.asn1.x509.CertificateList;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CRLHolder;
import org.bouncycastle.cert.X509v2CRLBuilder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.jce.X509KeyUsage;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.util.Arrays;
import org.xipki.scep4j.crypto.HashAlgoType;
import org.xipki.scep4j.util.ParamChecker;
import org.xipki.scep4j.util.ScepUtil;

/**
 * @author Lijun Liao
 */

public class CAEmulator
{
    public static final long MIN_IN_MS = 60L * 1000;
    public static final long DAY_IN_MS = 24L * 60 * MIN_IN_MS;

    private final PrivateKey cAKey;
    private final Certificate cACert;
    private final X500Name cASubject;
    private final byte[] cACertBytes;
    private final boolean generateCRL;

    private final Map<BigInteger, Certificate> serialCertMap = new HashMap<BigInteger, Certificate>();
    private final Map<X500Name, Certificate> reqSubjectCertMap = new HashMap<X500Name, Certificate>();
    private final AtomicLong serialNumber = new AtomicLong(2);
    private final AtomicLong crlNumber = new AtomicLong(2);
    private CertificateList crl;

    public CAEmulator(
            final PrivateKey cAKey,
            final Certificate cACert,
            final boolean generateCRL)
    throws CertificateEncodingException
    {
        ParamChecker.assertNotNull("cAKey", cAKey);
        ParamChecker.assertNotNull("cACert", cACert);

        this.cAKey = cAKey;
        this.cACert = cACert;
        this.cASubject = cACert.getSubject();
        this.generateCRL = generateCRL;
        try
        {
            this.cACertBytes = cACert.getEncoded();
        } catch (IOException e)
        {
            throw new CertificateEncodingException(e.getMessage(), e);
        }
    }

    public PrivateKey getCAKey()
    {
        return cAKey;
    }

    public Certificate getCACert()
    {
        return cACert;
    }

    public byte[] getCACertBytes()
    {
        return Arrays.clone(cACertBytes);
    }

    public boolean isGenerateCRL()
    {
        return generateCRL;
    }

    public Certificate generateCert(
            final CertificationRequest p10ReqInfo)
    throws Exception
    {
        // TODO: verify the PKCS#10 request
        CertificationRequestInfo reqInfo = p10ReqInfo.getCertificationRequestInfo();
        return generateCert(reqInfo.getSubjectPublicKeyInfo(), reqInfo.getSubject());
    }

    public Certificate generateCert(
            final SubjectPublicKeyInfo pubKeyInfo,
            final X500Name subjectDN)
    throws Exception
    {
        return generateCert(pubKeyInfo, subjectDN,
                new Date(System.currentTimeMillis() - 10 * CAEmulator.MIN_IN_MS));
    }

    public Certificate generateCert(
            final SubjectPublicKeyInfo pubKeyInfo,
            final X500Name subjectDN,
            final Date notBefore)
    throws Exception
    {
        Date notAfter = new Date(notBefore.getTime() + 730 * DAY_IN_MS);

        BigInteger _serialNumber = BigInteger.valueOf(serialNumber.getAndAdd(1));
        X509v3CertificateBuilder certGenerator = new X509v3CertificateBuilder(
                cASubject,
                _serialNumber,
                notBefore,
                notAfter,
                subjectDN,
                pubKeyInfo);

        X509KeyUsage ku = new X509KeyUsage(
                    X509KeyUsage.digitalSignature | X509KeyUsage.dataEncipherment |
                    X509KeyUsage.keyAgreement | X509KeyUsage.keyEncipherment);
        certGenerator.addExtension(Extension.keyUsage, true, ku);
        BasicConstraints bc = new BasicConstraints(false);
        certGenerator.addExtension(Extension.basicConstraints, true, bc);

        String signatureAlgorithm = ScepUtil.getSignatureAlgorithm(cAKey, HashAlgoType.SHA256);
        ContentSigner contentSigner = new JcaContentSignerBuilder(signatureAlgorithm).build(cAKey);
        Certificate asn1Cert = certGenerator.build(contentSigner).toASN1Structure();

        serialCertMap.put(_serialNumber, asn1Cert);
        reqSubjectCertMap.put(subjectDN, asn1Cert);
        return asn1Cert;
    }

    public Certificate getCert(
            final X500Name issuer,
            final BigInteger serialNumber)
    {
        if(cASubject.equals(issuer) == false)
        {
            return null;
        }

        return serialCertMap.get(serialNumber);
    }

    public Certificate pollCert(
            final X500Name issuer,
            final X500Name subject)
    {
        if(cASubject.equals(issuer) == false)
        {
            return null;
        }

        return reqSubjectCertMap.get(subject);
    }

    public synchronized CertificateList getCRL(
            final X500Name issuer,
            final BigInteger serialNumber)
    throws Exception
    {
        if(crl != null)
        {
            return crl;
        }

        Date thisUpdate = new Date();
        X509v2CRLBuilder crlBuilder = new X509v2CRLBuilder(cASubject, thisUpdate);
        Date nextUpdate = new Date(thisUpdate.getTime() + 30 * DAY_IN_MS);
        crlBuilder.setNextUpdate(nextUpdate);
        Date cAStartTime = cACert.getTBSCertificate().getStartDate().getDate();
        Date revocationTime = new Date(cAStartTime.getTime() + 1);
        if(revocationTime.after(thisUpdate))
        {
            revocationTime = cAStartTime;
        }
        crlBuilder.addCRLEntry(BigInteger.valueOf(2), revocationTime, CRLReason.keyCompromise);
        crlBuilder.addExtension(Extension.cRLNumber, false, new ASN1Integer(crlNumber.getAndAdd(1)));

        String signatureAlgorithm = ScepUtil.getSignatureAlgorithm(cAKey, HashAlgoType.SHA256);
        ContentSigner contentSigner = new JcaContentSignerBuilder(signatureAlgorithm).build(cAKey);
        X509CRLHolder _crl = crlBuilder.build(contentSigner);
        crl = _crl.toASN1Structure();
        return crl;
    }

}