/*
 *
 * Copyright (c) 2013 - 2017 Lijun Liao
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3
 * as published by the Free Software Foundation with the addition of the
 * following permission added to Section 15 as permitted in Section 7(a):
 *
 * FOR ANY PART OF THE COVERED WORK IN WHICH THE COPYRIGHT IS OWNED BY
 * THE AUTHOR LIJUN LIAO. LIJUN LIAO DISCLAIMS THE WARRANTY OF NON INFRINGEMENT
 * OF THIRD PARTY RIGHTS.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 * The interactive user interfaces in modified source and object code versions
 * of this program must display Appropriate Legal Notices, as required under
 * Section 5 of the GNU Affero General Public License.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license. Buying such a license is mandatory as soon as you
 * develop commercial activities involving the XiPKI software without
 * disclosing the source code of your own applications.
 *
 * For more information, please contact Lijun Liao at this
 * address: lijun.liao@gmail.com
 */

package org.xipki.pki.ocsp.server.impl;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.bouncycastle.asn1.ASN1GeneralizedTime;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.isismtt.ISISMTTObjectIdentifiers;
import org.bouncycastle.asn1.isismtt.ocsp.CertHash;
import org.bouncycastle.asn1.ocsp.OCSPObjectIdentifiers;
import org.bouncycastle.asn1.ocsp.OCSPResponse;
import org.bouncycastle.asn1.ocsp.RevokedInfo;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.ocsp.BasicOCSPResp;
import org.bouncycastle.cert.ocsp.BasicOCSPRespBuilder;
import org.bouncycastle.cert.ocsp.CertificateID;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPException;
import org.bouncycastle.cert.ocsp.OCSPReq;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.cert.ocsp.OCSPRespBuilder;
import org.bouncycastle.cert.ocsp.Req;
import org.bouncycastle.cert.ocsp.RespID;
import org.bouncycastle.cert.ocsp.RevokedStatus;
import org.bouncycastle.cert.ocsp.UnknownStatus;
import org.bouncycastle.operator.ContentVerifierProvider;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xipki.audit.AuditEvent;
import org.xipki.audit.AuditLevel;
import org.xipki.audit.AuditServiceRegister;
import org.xipki.audit.AuditStatus;
import org.xipki.audit.PciAuditEvent;
import org.xipki.common.HealthCheckResult;
import org.xipki.common.InvalidConfException;
import org.xipki.common.ObjectCreationException;
import org.xipki.common.TripleState;
import org.xipki.common.util.CollectionUtil;
import org.xipki.common.util.IoUtil;
import org.xipki.common.util.LogUtil;
import org.xipki.common.util.ParamUtil;
import org.xipki.common.util.RandomUtil;
import org.xipki.common.util.StringUtil;
import org.xipki.common.util.XmlUtil;
import org.xipki.datasource.DataSourceFactory;
import org.xipki.datasource.DataSourceWrapper;
import org.xipki.datasource.springframework.dao.DataAccessException;
import org.xipki.http.servlet.ServletURI;
import org.xipki.password.PasswordResolverException;
import org.xipki.pki.ocsp.api.CertStatus;
import org.xipki.pki.ocsp.api.CertStatusInfo;
import org.xipki.pki.ocsp.api.CertprofileOption;
import org.xipki.pki.ocsp.api.OcspMode;
import org.xipki.pki.ocsp.api.OcspStore;
import org.xipki.pki.ocsp.api.OcspStoreException;
import org.xipki.pki.ocsp.api.OcspStoreFactoryRegister;
import org.xipki.pki.ocsp.server.impl.OcspRespWithCacheInfo.ResponseCacheInfo;
import org.xipki.pki.ocsp.server.impl.jaxb.AuditOptionType;
import org.xipki.pki.ocsp.server.impl.jaxb.CertprofileOptionType;
import org.xipki.pki.ocsp.server.impl.jaxb.DatasourceType;
import org.xipki.pki.ocsp.server.impl.jaxb.EmbedCertsMode;
import org.xipki.pki.ocsp.server.impl.jaxb.FileOrPlainValueType;
import org.xipki.pki.ocsp.server.impl.jaxb.FileOrValueType;
import org.xipki.pki.ocsp.server.impl.jaxb.OCSPServer;
import org.xipki.pki.ocsp.server.impl.jaxb.ObjectFactory;
import org.xipki.pki.ocsp.server.impl.jaxb.RequestOptionType;
import org.xipki.pki.ocsp.server.impl.jaxb.ResponderType;
import org.xipki.pki.ocsp.server.impl.jaxb.ResponseCacheType;
import org.xipki.pki.ocsp.server.impl.jaxb.ResponseOptionType;
import org.xipki.pki.ocsp.server.impl.jaxb.SignerType;
import org.xipki.pki.ocsp.server.impl.jaxb.StoreType;
import org.xipki.pki.ocsp.server.impl.store.crl.CrlDbCertStatusStore;
import org.xipki.pki.ocsp.server.impl.store.db.DbCertStatusStore;
import org.xipki.security.AlgorithmCode;
import org.xipki.security.CertRevocationInfo;
import org.xipki.security.CertpathValidationModel;
import org.xipki.security.ConcurrentContentSigner;
import org.xipki.security.CrlReason;
import org.xipki.security.HashAlgoType;
import org.xipki.security.ObjectIdentifiers;
import org.xipki.security.SecurityFactory;
import org.xipki.security.SignerConf;
import org.xipki.security.exception.NoIdleSignerException;
import org.xipki.security.util.X509Util;
import org.xml.sax.SAXException;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

public class OcspServer {

    private static class SizeComparableString implements Comparable<SizeComparableString> {

        private String str;

        public SizeComparableString(String str) {
            this.str = ParamUtil.requireNonNull("str", str);
        }

        @Override
        public int compareTo(SizeComparableString obj) {
            if (str.length() == obj.str.length()) {
                return 0;
            }

            return (str.length() > obj.str.length()) ? 1 : -1;
        }

    }

    private static class OcspRespControl {
        boolean canCacheInfo;
        boolean includeExtendedRevokeExtension;
        long cacheThisUpdate;
        long cacheNextUpdate;

        public OcspRespControl() {
            includeExtendedRevokeExtension = false;
            cacheThisUpdate = 0;
            cacheNextUpdate = Long.MAX_VALUE;
        }
    }

    public static final long DFLT_CACHE_MAX_AGE = 60; // 1 minute

    private static final Logger LOG = LoggerFactory.getLogger(OcspServer.class);

    private static final byte[] DERNullBytes = new byte[]{5, 0};

    private static final Map<OcspResponseStatus, OcspRespWithCacheInfo> unsuccesfulOCSPRespMap;

    private final DataSourceFactory datasourceFactory;

    private SecurityFactory securityFactory;

    private String confFile;

    private boolean master;

    private AuditServiceRegister auditServiceRegister;

    private ResponseCacher responseCacher;

    private OcspStoreFactoryRegister ocspStoreFactoryRegister;

    private Map<String, Responder> responders = new HashMap<>();

    private Map<String, ResponderSigner> signers = new HashMap<>();

    private Map<String, RequestOption> requestOptions = new HashMap<>();

    private Map<String, ResponseOption> responseOptions = new HashMap<>();

    private Map<String, AuditOption> auditOptions = new HashMap<>();

    private Map<String, CertprofileOption> certprofileOptions = new HashMap<>();

    private Map<String, OcspStore> stores = new HashMap<>();

    private List<String> servletPaths = new ArrayList<>();

    private Map<String, Responder> path2responderMap = new HashMap<>();

    private AtomicBoolean initialized = new AtomicBoolean(false);

    static {
        unsuccesfulOCSPRespMap = new HashMap<>(10);
        for (OcspResponseStatus status : OcspResponseStatus.values()) {
            if (status == OcspResponseStatus.successful) {
                continue;
            }
            OCSPResp resp = new OCSPResp(new OCSPResponse(
                    new org.bouncycastle.asn1.ocsp.OCSPResponseStatus(status.status()), null));
            byte[] encoded;
            try {
                encoded = resp.getEncoded();
            } catch (IOException ex) {
                throw new ExceptionInInitializerError(
                        "could not encode OCSPResp for status " + status + ": " + ex.getMessage());
            }
            unsuccesfulOCSPRespMap.put(status, new OcspRespWithCacheInfo(encoded, null));
        }
    }
    public OcspServer() {
        this.datasourceFactory = new DataSourceFactory();
    }

    public void setSecurityFactory(final SecurityFactory securityFactory) {
        this.securityFactory = securityFactory;
    }

    public void setConfFile(final String confFile) {
        this.confFile = confFile;
    }

    Responder getResponder(final ServletURI servletUri) throws UnsupportedEncodingException {
        String path = servletUri.path();
        for (String servletPath : servletPaths) {
            if (path.startsWith(servletPath)) {
                return path2responderMap.get(servletPath);
            }
        }
        return null;
    }

    Object[] getServletPathAndResponder(final ServletURI servletUri)
            throws UnsupportedEncodingException {
        String path = servletUri.path();
        for (String servletPath : servletPaths) {
            if (path.startsWith(servletPath)) {
                return new Object[]{servletPath, path2responderMap.get(servletPath)};
            }
        }
        return null;
    }

    public Responder getResponder(final String name) {
        ParamUtil.requireNonBlank("name", name);
        return responders.get(name);
    }

    public boolean isInitialized() {
        return initialized.get();
    }

    public void init() throws InvalidConfException, PasswordResolverException, DataAccessException {
        LOG.info("starting OCSPResponder server ...");
        if (initialized.get()) {
            LOG.info("already started, skipping ...");
            return;
        }

        try {
            doInit();
            initialized.set(true);
        } finally {
            if (initialized.get()) {
                LOG.info("started OCSPResponder server");
            } else {
                LOG.error("could not start OCSPResponder server");
            }
            auditLogPciEvent(initialized.get(), "START");
        }
    }

    private void doInit()
            throws InvalidConfException, DataAccessException, PasswordResolverException {
        if (confFile == null) {
            throw new IllegalStateException("confFile is not set");
        }
        if (datasourceFactory == null) {
            throw new IllegalStateException("datasourceFactory is not set");
        }
        if (securityFactory == null) {
            throw new IllegalStateException("securityFactory is not set");
        }

        OCSPServer conf = parseConf(confFile);

        //-- check the duplication names
        Set<String> set = new HashSet<>();

        // Duplication name check: responder
        for (ResponderType m : conf.getResponders().getResponder()) {
            String name = m.getName();
            if (set.contains(name)) {
                throw new InvalidConfException(
                        "duplicated definition of responder named '" + name + "'");
            }

            if (StringUtil.isBlank(name)) {
                throw new InvalidConfException("responder name must not be empty");
            }

            for (int i = 0; i < name.length(); i++) {
                char ch = name.charAt(i);
                if (!((ch >= '0' && ch <= '9') || (ch >= 'A' && ch <= 'Z')
                        || (ch >= 'a' && ch <= 'z'))) {
                    throw new InvalidConfException("invalid OCSP responder name '" + name + "'");
                }
            } // end for
            set.add(name);
        } // end for

        // Duplication name check: signer
        set.clear();
        for (SignerType m : conf.getSigners().getSigner()) {
            String name = m.getName();
            if (set.contains(name)) {
                throw new InvalidConfException(
                        "duplicated definition of signer option named '" + name + "'");
            }
            set.add(name);
        }

        // Duplication name check: requests
        set.clear();
        for (RequestOptionType m : conf.getRequestOptions().getRequestOption()) {
            String name = m.getName();
            if (set.contains(name)) {
                throw new InvalidConfException(
                        "duplicated definition of request option named '" + name + "'");
            }
            set.add(name);
        }

        // Duplication name check: response
        set.clear();
        for (ResponseOptionType m : conf.getResponseOptions().getResponseOption()) {
            String name = m.getName();
            if (set.contains(name)) {
                throw new InvalidConfException(
                        "duplicated definition of response option named '" + name + "'");
            }
            set.add(name);
        }

        // Duplication name check: audit
        set.clear();
        if (conf.getAuditOptions() != null) {
            for (AuditOptionType m : conf.getAuditOptions().getAuditOption()) {
                String name = m.getName();
                if (set.contains(name)) {
                    throw new InvalidConfException(
                            "duplicated definition of audit option named '" + name + "'");
                }
                set.add(name);
            }
        }

        // Duplication name check: store
        set.clear();
        for (StoreType m : conf.getStores().getStore()) {
            String name = m.getName();
            if (set.contains(name)) {
                throw new InvalidConfException(
                        "duplicated definition of store named '" + name + "'");
            }
        }

        // Duplication name check: certprofile
        set.clear();
        if (conf.getCertprofileOptions() != null) {
            for (CertprofileOptionType m : conf.getCertprofileOptions().getCertprofileOption()) {
                String name = m.getName();
                if (set.contains(name)) {
                    throw new InvalidConfException(
                            "duplicated definition of certprofile option named '" + name + "'");
                }
                set.add(name);
            }
        }

        // Duplication name check: datasource
        set.clear();
        if (conf.getDatasources() != null) {
            for (DatasourceType m : conf.getDatasources().getDatasource()) {
                String name = m.getName();
                if (set.contains(name)) {
                    throw new InvalidConfException(
                            "duplicated definition of datasource named '" + name + "'");
                }
                set.add(name);
            }
        }

        this.master = conf.isMaster();

        // Response Cache
        ResponseCacheType cacheType = conf.getResponseCache();
        if (cacheType != null) {
            DatasourceType cacheSourceConf = cacheType.getDatasource();
            DataSourceWrapper datasource;
            InputStream dsStream = null;
            try {
                dsStream = getInputStream(cacheSourceConf.getConf());
                datasource = datasourceFactory.createDataSource(cacheSourceConf.getName(),
                                dsStream, securityFactory.getPasswordResolver());
            } catch (IOException ex) {
                throw new InvalidConfException(ex.getMessage(), ex);
            } finally {
                close(dsStream);
            }
            responseCacher = new ResponseCacher(datasource, master, cacheType.getValidity());
            responseCacher.init();
        }

        //-- initializes the responders
        // signers
        for (SignerType m : conf.getSigners().getSigner()) {
            ResponderSigner signer = initSigner(m);
            signers.put(m.getName(), signer);
        }

        // requests
        for (RequestOptionType m : conf.getRequestOptions().getRequestOption()) {
            RequestOption option = new RequestOption(m);
            requestOptions.put(m.getName(), option);
        }

        // responses
        for (ResponseOptionType m : conf.getResponseOptions().getResponseOption()) {
            ResponseOption option = new ResponseOption(m);
            responseOptions.put(m.getName(), option);
        }

        // audits
        if (conf.getAuditOptions() != null) {
            for (AuditOptionType m : conf.getAuditOptions().getAuditOption()) {
                AuditOption option = new AuditOption(m);
                auditOptions.put(m.getName(), option);
            }
        }

        // certprofiles
        if (conf.getCertprofileOptions() != null) {
            for (CertprofileOptionType m : conf.getCertprofileOptions().getCertprofileOption()) {
                CertprofileOption option = new CertprofileOption(
                        m.getIncludes().getInclude(), m.getExcludes().getExclude());
                certprofileOptions.put(m.getName(), option);
            }
        }

        // datasources
        Map<String, DataSourceWrapper> datasources = new HashMap<>();
        if (conf.getDatasources() != null) {
            for (DatasourceType m : conf.getDatasources().getDatasource()) {
                String name = m.getName();
                DataSourceWrapper datasource;
                InputStream dsStream = null;
                try {
                    dsStream = getInputStream(m.getConf());
                    datasource = datasourceFactory.createDataSource(name,
                                    dsStream, securityFactory.getPasswordResolver());
                } catch (IOException ex) {
                    throw new InvalidConfException(ex.getMessage(), ex);
                } finally {
                    close(dsStream);
                }
                datasources.put(name, datasource);
            } // end for
        } // end if

        // responders
        Map<String, Set<HashAlgoType>> storeCertHashAlgoSet = new HashMap<>();

        Map<String, ResponderOption> responderOptions = new HashMap<>();

        for (ResponderType m : conf.getResponders().getResponder()) {
            ResponderOption option = new ResponderOption(m);
            String optName = option.auditOptionName();
            if (optName != null && !auditOptions.containsKey(optName)) {
                throw new InvalidConfException("no auditOption named '" + optName + "' is defined");
            }

            optName = option.certprofileOptionName();
            if (optName != null && !certprofileOptions.containsKey(optName)) {
                throw new InvalidConfException("no certprofileOption named '" + optName
                        + "' is defined");
            }

            optName = option.signerName();
            if (!signers.containsKey(optName)) {
                throw new InvalidConfException("no signer named '" + optName + "' is defined");
            }

            String reqOptName = option.requestOptionName();
            if (!requestOptions.containsKey(reqOptName)) {
                throw new InvalidConfException(
                        "no requestOption named '" + reqOptName + "' is defined");
            }

            String respOptName = option.responseOptionName();
            if (!responseOptions.containsKey(respOptName)) {
                throw new InvalidConfException(
                        "no responseOption named '" + respOptName + "' is defined");
            }

            // required HashAlgorithms for certificate
            ResponseOption respOpt = responseOptions.get(respOptName);
            Set<HashAlgoType> certHashAlgos = new HashSet<>(5);
            if (respOpt.isIncludeCerthash()) {
                if (respOpt.certHashAlgo() != null) {
                    certHashAlgos.add(respOpt.certHashAlgo());
                } else {
                    RequestOption reqOpt = requestOptions.get(reqOptName);
                    Set<HashAlgoType> algs = reqOpt.hashAlgos();
                    if (!CollectionUtil.isEmpty(algs)) {
                        certHashAlgos.addAll(algs);
                    } else {
                        HashAlgoType[] hashAlgos = new HashAlgoType[]{HashAlgoType.SHA1,
                            HashAlgoType.SHA224, HashAlgoType.SHA256, HashAlgoType.SHA384,
                            HashAlgoType.SHA512};
                        for (HashAlgoType hashAlgo : hashAlgos) {
                            certHashAlgos.add(hashAlgo);
                        }
                    }
                }
            }

            List<String> names = option.storeNames();

            List<StoreType> storeDefs = conf.getStores().getStore();
            Set<String> storeNames = new HashSet<>(storeDefs.size());
            for (StoreType storeDef : storeDefs) {
                storeNames.add(storeDef.getName());
            }

            for (String name : names) {
                if (!storeNames.contains(name)) {
                    throw new InvalidConfException("no store named '" + name + "' is defined");
                }

                Set<HashAlgoType> hashAlgoSet = storeCertHashAlgoSet.get(name);
                if (hashAlgoSet == null) {
                    hashAlgoSet = new HashSet<>(5);
                    storeCertHashAlgoSet.put(name, hashAlgoSet);
                }

                hashAlgoSet.addAll(certHashAlgos);
            }

            responderOptions.put(m.getName(), option);
        } // end for

        // stores
        for (StoreType m : conf.getStores().getStore()) {
            OcspStore store = newStore(m, datasources,
                    storeCertHashAlgoSet.get(m.getName()));
            stores.put(m.getName(), store);
        }

        // responders
        for (String name : responderOptions.keySet()) {
            ResponderOption option = responderOptions.get(name);
            String aoName = option.auditOptionName();
            String cfoName = option.certprofileOptionName();

            List<OcspStore> statusStores = new ArrayList<>(option.storeNames().size());
            for (String storeName : option.storeNames()) {
                statusStores.add(stores.get(storeName));
            }

            AuditOption auditOption = (aoName == null) ? null : auditOptions.get(aoName);

            CertprofileOption certprofileOption = (cfoName == null) ? null
                    : certprofileOptions.get(cfoName);

            ResponseOption responseOption = responseOptions.get(option.responseOptionName());
            ResponderSigner signer = signers.get(option.signerName());
            if (signer.isMacSigner()) {
                if (responseOption.isResponderIdByName()) {
                    throw new InvalidConfException(
                            "could not use ResponderIdByName for signer "
                            + option.signerName());
                }

                if (EmbedCertsMode.NONE != responseOption.embedCertsMode()) {
                    throw new InvalidConfException(
                            "could not embed certifcate in response for signer "
                            + option.signerName());
                }
            }

            Responder responder = new Responder(option,
                    requestOptions.get(option.requestOptionName()),
                    responseOption, auditOption, certprofileOption, signer, statusStores);
            responders.put(name, responder);
        } // end for

        // servlet paths
        List<SizeComparableString> tmpList = new LinkedList<>();
        for (String name : responderOptions.keySet()) {
            Responder responder = responders.get(name);
            ResponderOption option = responderOptions.get(name);
            List<String> strs = option.servletPaths();
            for (String path : strs) {
                tmpList.add(new SizeComparableString(path));
                path2responderMap.put(path, responder);
            }
        }

        // Sort the servlet paths according to the length of path. The first one is the
        // longest, and the last one is the shortest.
        Collections.sort(tmpList);
        List<String> list2 = new ArrayList<>(tmpList.size());
        for (SizeComparableString m : tmpList) {
            list2.add(m.str);
        }
        this.servletPaths = list2;
    } // method doInit

    public void shutdown() {
        LOG.info("stopped OCSP Responder");
        if (responseCacher != null) {
            responseCacher.shutdown();
        }

        for (OcspStore store : stores.values()) {
            try {
                store.shutdown();
            } catch (Exception ex) {
                LogUtil.warn(LOG, ex, "shutdown store " + store.name());
            }
        }

        auditLogPciEvent(true, "SHUTDOWN");
    }

    public OcspRespWithCacheInfo answer(final Responder responder, final OCSPReq request,
            final boolean viaGet, final AuditEvent event) {
        boolean audit = (event != null);

        RequestOption reqOpt = responder.requestOption();
        ResponderSigner signer = responder.signer();
        ResponseOption repOpt = responder.responseOption();

        String msgId = null;
        if (audit) {
            msgId = RandomUtil.nextHexLong();
            event.addEventData(OcspAuditConstants.NAME_mid, msgId);
        }

        // BC returns 1 for v1(0) instead the real value 0.
        int version = request.getVersionNumber() - 1;
        if (!reqOpt.isVersionAllowed(version)) {
            String message = "invalid request version " + version;
            LOG.warn(message);
            if (audit) {
                fillAuditEvent(event, AuditLevel.INFO, AuditStatus.FAILED, message);
            }
            return unsuccesfulOCSPRespMap.get(OcspResponseStatus.malformedRequest);
        }

        try {
            OcspRespWithCacheInfo resp = checkSignature(request, reqOpt, event);
            if (resp != null) {
                return resp;
            }

            List<Extension> responseExtensions = new ArrayList<>(2);

            Req[] requestList = request.getRequestList();
            int requestsSize = requestList.length;
            if (requestsSize > reqOpt.maxRequestListCount()) {
                String message = requestsSize + " entries in RequestList, but maximal "
                        + reqOpt.maxRequestListCount() + " is allowed";
                LOG.warn(message);
                if (audit) {
                    fillAuditEvent(event, AuditLevel.INFO, AuditStatus.FAILED, message);
                }
                return unsuccesfulOCSPRespMap.get(OcspResponseStatus.malformedRequest);
            }

            Set<ASN1ObjectIdentifier> criticalExtensionOids = new HashSet<>();
            Set<?> tmp = request.getCriticalExtensionOIDs();
            if (tmp != null) {
                for (Object oid : tmp) {
                    criticalExtensionOids.add((ASN1ObjectIdentifier) oid);
                }
            }

            OcspRespControl repControl = new OcspRespControl();
            repControl.canCacheInfo = true;

            RespID respId = signer.getResponder(repOpt.isResponderIdByName());
            BasicOCSPRespBuilder basicOcspBuilder = new BasicOCSPRespBuilder(respId);
            ASN1ObjectIdentifier extensionType = OCSPObjectIdentifiers.id_pkix_ocsp_nonce;
            criticalExtensionOids.remove(extensionType);
            Extension nonceExtn = request.getExtension(extensionType);
            if (nonceExtn != null) {
                if (reqOpt.nonceOccurrence() == TripleState.FORBIDDEN) {
                    String message = "nonce forbidden, but is present in the request";
                    LOG.warn(message);
                    if (audit) {
                        fillAuditEvent(event, AuditLevel.INFO, AuditStatus.FAILED, message);
                    }
                    return unsuccesfulOCSPRespMap.get(OcspResponseStatus.malformedRequest);
                }

                byte[] nonce = nonceExtn.getExtnValue().getOctets();
                int len = nonce.length;
                int min = reqOpt.nonceMinLen();
                int max = reqOpt.nonceMaxLen();

                if (len < min || len > max) {
                    LOG.warn("length of nonce {} not within [{},{}]", len, min, max);
                    StringBuilder sb = new StringBuilder(50);
                    sb.append("length of nonce ").append(len);
                    sb.append(" not within [").append(min).append(", ").append(max).append("]");
                    if (audit) {
                        fillAuditEvent(event, AuditLevel.INFO, AuditStatus.FAILED, sb.toString());
                    }
                    return unsuccesfulOCSPRespMap.get(OcspResponseStatus.malformedRequest);
                }

                repControl.canCacheInfo = false;
                responseExtensions.add(nonceExtn);
            } else {
                if (reqOpt.nonceOccurrence() == TripleState.REQUIRED) {
                    String message = "nonce required, but is not present in the request";
                    LOG.warn(message);
                    if (audit) {
                        fillAuditEvent(event, AuditLevel.INFO, AuditStatus.FAILED, message);
                    }
                    return unsuccesfulOCSPRespMap.get(OcspResponseStatus.malformedRequest);
                }
            }

            ConcurrentContentSigner concurrentSigner = null;
            if (responder.responderOption().mode() != OcspMode.RFC2560) {
                extensionType = ObjectIdentifiers.id_pkix_ocsp_prefSigAlgs;
                criticalExtensionOids.remove(extensionType);
                Extension ext = request.getExtension(extensionType);
                if (ext != null) {
                    ASN1Sequence preferredSigAlgs = ASN1Sequence.getInstance(ext.getParsedValue());
                    concurrentSigner = signer.getSignerForPreferredSigAlgs(preferredSigAlgs);
                }
            }

            if (CollectionUtil.isNonEmpty(criticalExtensionOids)) {
                return unsuccesfulOCSPRespMap.get(OcspResponseStatus.malformedRequest);
            }

            if (concurrentSigner == null) {
                concurrentSigner = signer.firstSigner();
            }

            AlgorithmCode cacheDbSigAlgCode = null;
            AlgorithmCode cacheDbCertHashAlgCode = null;
            BigInteger cacheDbSerialNumber = null;
            Integer cacheDbIssuerId = null;

            boolean canCacheDb = responseCacher != null && responseCacher.isOnService()
                    && nonceExtn == null && requestsSize == 1;
            if (canCacheDb) {
                // try to find the cached response
                CertificateID certId = requestList[0].getCertID();
                String certIdHashAlgo = certId.getHashAlgOID().getId();
                HashAlgoType reqHashAlgo = HashAlgoType.getHashAlgoType(certIdHashAlgo);
                if (reqHashAlgo == null) {
                    LOG.warn("unknown CertID.hashAlgorithm {}", certIdHashAlgo);
                    if (audit) {
                        fillAuditEvent(event, AuditLevel.INFO, AuditStatus.FAILED,
                                "unknown CertID.hashAlgorithm " + certIdHashAlgo);
                    }
                    return unsuccesfulOCSPRespMap.get(OcspResponseStatus.malformedRequest);
                } else if (!reqOpt.allows(reqHashAlgo)) {
                    LOG.warn("CertID.hashAlgorithm {} not allowed", certIdHashAlgo);
                    if (audit) {
                        fillAuditEvent(event, AuditLevel.INFO, AuditStatus.FAILED,
                                "not allowed CertID.hashAlgorithm " + certIdHashAlgo);
                    }
                    return unsuccesfulOCSPRespMap.get(OcspResponseStatus.malformedRequest);
                }

                HashAlgoType certHashAlgo = repOpt.certHashAlgo();
                if (certHashAlgo == null) {
                    certHashAlgo = reqHashAlgo;
                }
                cacheDbCertHashAlgCode = certHashAlgo.algorithmCode();

                cacheDbSigAlgCode = concurrentSigner.algorithmCode();

                byte[] nameHash = certId.getIssuerNameHash();
                byte[] keyHash = certId.getIssuerKeyHash();
                cacheDbIssuerId = responseCacher.getIssuerId(reqHashAlgo, nameHash, keyHash);
                cacheDbSerialNumber = certId.getSerialNumber();

                if (cacheDbIssuerId != null) {
                    OcspRespWithCacheInfo cachedResp = responseCacher.getOcspResponse(
                            cacheDbIssuerId.intValue(), cacheDbSerialNumber, cacheDbSigAlgCode,
                            cacheDbCertHashAlgCode);
                    if (cachedResp != null) {
                        LOG.debug("use cached response for (cacheIssuer={} and serial={})",
                                cacheDbIssuerId, cacheDbSerialNumber);
                        return cachedResp;
                    } else {
                        LOG.debug("found no cached response for (cacheIssuer={} and serial={})",
                                cacheDbIssuerId, cacheDbSerialNumber);
                    }
                } else if (master) {
                    // store the issuer certificate in cache database.
                    X509Certificate issuerCert = null;
                    for (OcspStore store : responder.stores()) {
                        issuerCert = store.getIssuerCert(reqHashAlgo, nameHash, keyHash);
                        if (issuerCert != null) {
                            break;
                        }
                    }

                    if (issuerCert != null) {
                        cacheDbIssuerId = responseCacher.storeIssuer(issuerCert);
                    }
                }

                if (cacheDbIssuerId == null) {
                    canCacheDb = false;
                }
            }

            for (int i = 0; i < requestsSize; i++) {
                AuditEvent singleEvent = null;
                if (audit) {
                    singleEvent = new AuditEvent(new Date());
                    singleEvent.setApplicationName(OcspAuditConstants.APPNAME);
                    singleEvent.setName(OcspAuditConstants.NAME_PERF);
                    singleEvent.addEventData(OcspAuditConstants.NAME_mid, msgId);
                }

                OcspRespWithCacheInfo failureOcspResp = null;
                try {
                    failureOcspResp = processCertReq(requestList[i], basicOcspBuilder, responder,
                            reqOpt, repOpt, repControl, singleEvent);
                } finally {
                    if (singleEvent != null) {
                        singleEvent.finish();
                        auditServiceRegister.getAuditService().logEvent(singleEvent);
                    }
                }

                if (failureOcspResp != null) {
                    return failureOcspResp;
                }
            }

            if (repControl.includeExtendedRevokeExtension) {
                responseExtensions.add(
                        new Extension(ObjectIdentifiers.id_pkix_ocsp_extendedRevoke, true,
                                Arrays.copyOf(DERNullBytes, DERNullBytes.length)));
            }

            if (CollectionUtil.isNonEmpty(responseExtensions)) {
                basicOcspBuilder.setResponseExtensions(
                        new Extensions(responseExtensions.toArray(new Extension[0])));
            }

            X509CertificateHolder[] certsInResp;
            EmbedCertsMode certsMode = repOpt.embedCertsMode();
            if (certsMode == null || certsMode == EmbedCertsMode.SIGNER) {
                certsInResp = new X509CertificateHolder[]{signer.bcCertificate()};
            } else if (certsMode == EmbedCertsMode.SIGNER_AND_CA) {
                certsInResp = signer.bcCertificateChain();
            } else {
                // NONE
                certsInResp = null;
            }

            BasicOCSPResp basicOcspResp;
            try {
                basicOcspResp = concurrentSigner.build(basicOcspBuilder, certsInResp, new Date());
            } catch (NoIdleSignerException ex) {
                return unsuccesfulOCSPRespMap.get(OcspResponseStatus.tryLater);
            } catch (OCSPException ex) {
                LogUtil.error(LOG, ex, "answer() basicOcspBuilder.build");
                if (audit) {
                    fillAuditEvent(event, AuditLevel.ERROR, AuditStatus.FAILED,
                            "BasicOCSPRespBuilder.build() with OCSPException");
                }
                return unsuccesfulOCSPRespMap.get(OcspResponseStatus.internalError);
            }

            OCSPRespBuilder ocspRespBuilder = new OCSPRespBuilder();
            try {
                OCSPResp ocspResp = ocspRespBuilder.build(
                        OcspResponseStatus.successful.status(), basicOcspResp);

                // cache response in database
                if (canCacheDb && repControl.canCacheInfo) {
                    // Don't cache the response with status UNKNOWN, since this may result in DDoS
                    // of storage
                    responseCacher.storeOcspResponse(cacheDbIssuerId.intValue(),
                            cacheDbSerialNumber, repControl.cacheThisUpdate,
                            repControl.cacheNextUpdate, cacheDbSigAlgCode, cacheDbCertHashAlgCode,
                            ocspResp);
                }

                byte[] encoded = ocspResp.getEncoded();

                if (viaGet && repControl.canCacheInfo) {
                    ResponseCacheInfo cacheInfo = new ResponseCacheInfo(repControl.cacheThisUpdate);
                    if (repControl.cacheNextUpdate != Long.MAX_VALUE) {
                        cacheInfo.setNextUpdate(repControl.cacheNextUpdate);
                    }
                    return new OcspRespWithCacheInfo(encoded, cacheInfo);
                } else {
                    return new OcspRespWithCacheInfo(encoded, null);
                }
            } catch (OCSPException ex) {
                LogUtil.error(LOG, ex, "answer() ocspRespBuilder.build");
                if (audit) {
                    fillAuditEvent(event, AuditLevel.ERROR, AuditStatus.FAILED,
                            "OCSPRespBuilder.build() with OCSPException");
                }
                return unsuccesfulOCSPRespMap.get(OcspResponseStatus.internalError);
            }
        } catch (Throwable th) {
            LogUtil.error(LOG, th);
            if (audit) {
                fillAuditEvent(event, AuditLevel.ERROR, AuditStatus.FAILED, "internal error");
            }
            return unsuccesfulOCSPRespMap.get(OcspResponseStatus.internalError);
        }
    } // method ask

    private OcspRespWithCacheInfo processCertReq(Req req, BasicOCSPRespBuilder builder,
            Responder responder, RequestOption reqOpt, ResponseOption repOpt,
            OcspRespControl repControl, AuditEvent event) throws IOException {
        CertificateID certId = req.getCertID();
        String certIdHashAlgo = certId.getHashAlgOID().getId();
        HashAlgoType reqHashAlgo = HashAlgoType.getHashAlgoType(certIdHashAlgo);
        if (reqHashAlgo == null) {
            LOG.warn("unknown CertID.hashAlgorithm {}", certIdHashAlgo);
            if (event != null) {
                fillAuditEvent(event, AuditLevel.INFO, AuditStatus.FAILED,
                        "unknown CertID.hashAlgorithm " + certIdHashAlgo);
            }
            return unsuccesfulOCSPRespMap.get(OcspResponseStatus.malformedRequest);
        } else if (!reqOpt.allows(reqHashAlgo)) {
            LOG.warn("CertID.hashAlgorithm {} not allowed", certIdHashAlgo);
            if (event != null) {
                fillAuditEvent(event, AuditLevel.INFO, AuditStatus.FAILED,
                        "not allowed CertID.hashAlgorithm " + certIdHashAlgo);
            }
            return unsuccesfulOCSPRespMap.get(OcspResponseStatus.malformedRequest);
        }

        if (event != null) {
            event.addEventData(OcspAuditConstants.NAME_serial, certId.getSerialNumber());
        }

        CertStatusInfo certStatusInfo = null;
        OcspStore answeredStore = null;
        boolean exceptionOccurs = false;

        Date now = new Date();
        for (OcspStore store : responder.stores()) {
            try {
                certStatusInfo = store.getCertStatus(now, reqHashAlgo, certId.getIssuerNameHash(),
                        certId.getIssuerKeyHash(), certId.getSerialNumber(),
                        repOpt.isIncludeCerthash(), repOpt.certHashAlgo(),
                        responder.certprofileOption());
                if (certStatusInfo != null
                        && certStatusInfo.certStatus() != CertStatus.ISSUER_UNKNOWN) {
                    answeredStore = store;
                    break;
                }
            } catch (OcspStoreException ex) {
                exceptionOccurs = true;
                LogUtil.error(LOG, ex, "getCertStatus() of CertStatusStore " + store.name());
            } // end try
        } // end for

        if (certStatusInfo == null) {
            if (exceptionOccurs) {
                fillAuditEvent(event, AuditLevel.INFO, AuditStatus.FAILED,
                        "no CertStatusStore can answer the request");
                return unsuccesfulOCSPRespMap.get(OcspResponseStatus.tryLater);
            } else {
                certStatusInfo = CertStatusInfo.getIssuerUnknownCertStatusInfo(new Date(), null);
            }
        } else if (answeredStore != null
                && responder.responderOption().isInheritCaRevocation()) {
            CertRevocationInfo caRevInfo = answeredStore.getCaRevocationInfo(
                    reqHashAlgo, certId.getIssuerNameHash(), certId.getIssuerKeyHash());
            if (caRevInfo != null) {
                CertStatus certStatus = certStatusInfo.certStatus();
                boolean replaced = false;
                if (certStatus == CertStatus.GOOD || certStatus == CertStatus.UNKNOWN) {
                    replaced = true;
                } else if (certStatus == CertStatus.REVOKED) {
                    if (certStatusInfo.revocationInfo().revocationTime().after(
                            caRevInfo.revocationTime())) {
                        replaced = true;
                    }
                }

                if (replaced) {
                    CertRevocationInfo newRevInfo;
                    if (caRevInfo.reason() == CrlReason.CA_COMPROMISE) {
                        newRevInfo = caRevInfo;
                    } else {
                        newRevInfo = new CertRevocationInfo(CrlReason.CA_COMPROMISE,
                                caRevInfo.revocationTime(), caRevInfo.invalidityTime());
                    }
                    certStatusInfo = CertStatusInfo.getRevokedCertStatusInfo(newRevInfo,
                            certStatusInfo.certHashAlgo(), certStatusInfo.certHash(),
                            certStatusInfo.thisUpdate(), certStatusInfo.nextUpdate(),
                            certStatusInfo.certprofile());
                } // end if(replaced)
            } // end if
        } // end if

        if (event != null) {
            String certprofile = certStatusInfo.certprofile();
            String auditCertType;
            if (certprofile != null) {
                auditCertType = responder.auditOption().certprofileMapping().get(certprofile);
                if (auditCertType == null) {
                    auditCertType = certprofile;
                }
            } else {
                auditCertType = "UNKNOWN";
            }

            event.addEventData(OcspAuditConstants.NAME_type, auditCertType);
        }

        // certStatusInfo must not be null in any case, since at least one store
        // is configured
        Date thisUpdate = certStatusInfo.thisUpdate();
        if (thisUpdate == null) {
            thisUpdate = new Date();
        }
        Date nextUpdate = certStatusInfo.nextUpdate();

        List<Extension> extensions = new LinkedList<>();
        boolean unknownAsRevoked = false;
        CertificateStatus bcCertStatus;
        switch (certStatusInfo.certStatus()) {
        case GOOD:
            bcCertStatus = null;
            break;

        case ISSUER_UNKNOWN:
            repControl.canCacheInfo = false;
            bcCertStatus = new UnknownStatus();
            break;

        case UNKNOWN:
        case IGNORE:
            repControl.canCacheInfo = false;
            if (responder.responderOption().mode() == OcspMode.RFC2560) {
                bcCertStatus = new UnknownStatus();
            } else { // (ocspMode == OCSPMode.RFC6960)
                unknownAsRevoked = true;
                repControl.includeExtendedRevokeExtension = true;
                bcCertStatus = new RevokedStatus(new Date(0L),
                        CrlReason.CERTIFICATE_HOLD.code());
            }
            break;
        case REVOKED:
            CertRevocationInfo revInfo = certStatusInfo.revocationInfo();
            ASN1GeneralizedTime revTime = new ASN1GeneralizedTime(
                    revInfo.revocationTime());
            org.bouncycastle.asn1.x509.CRLReason tmpReason = null;
            if (repOpt.isIncludeRevReason()) {
                tmpReason = org.bouncycastle.asn1.x509.CRLReason.lookup(
                        revInfo.reason().code());
            }
            RevokedInfo tmpRevInfo = new RevokedInfo(revTime, tmpReason);
            bcCertStatus = new RevokedStatus(tmpRevInfo);

            Date invalidityDate = revInfo.invalidityTime();
            if (repOpt.isIncludeInvalidityDate() && invalidityDate != null
                    && !invalidityDate.equals(revInfo.revocationTime())) {
                Extension extension = new Extension(Extension.invalidityDate,
                        false, new ASN1GeneralizedTime(invalidityDate).getEncoded());
                extensions.add(extension);
            }
            break;
        default:
            throw new RuntimeException(
                    "unknown CertificateStatus:" + certStatusInfo.certStatus());
        } // end switch

        byte[] certHash = certStatusInfo.certHash();
        if (certHash != null) {
            ASN1ObjectIdentifier hashAlgOid = certStatusInfo.certHashAlgo().oid();
            AlgorithmIdentifier hashAlgId = new AlgorithmIdentifier(hashAlgOid, DERNull.INSTANCE);
            CertHash bcCertHash = new CertHash(hashAlgId, certHash);

            byte[] encodedCertHash;
            try {
                encodedCertHash = bcCertHash.getEncoded();
            } catch (IOException ex) {
                LogUtil.error(LOG, ex, "answer() bcCertHash.getEncoded");
                if (event != null) {
                    fillAuditEvent(event, AuditLevel.ERROR, AuditStatus.FAILED,
                            "CertHash.getEncoded() with IOException");
                }
                return unsuccesfulOCSPRespMap.get(OcspResponseStatus.internalError);
            }

            Extension extension = new Extension(
                    ISISMTTObjectIdentifiers.id_isismtt_at_certHash,
                    false, encodedCertHash);

            extensions.add(extension);
        } // end if(certHash != null)

        if (certStatusInfo.archiveCutOff() != null) {
            Extension extension = new Extension(OCSPObjectIdentifiers.id_pkix_ocsp_archive_cutoff,
                    false, new ASN1GeneralizedTime(certStatusInfo.archiveCutOff()).getEncoded());
            extensions.add(extension);
        }

        String certStatusText;
        if (bcCertStatus instanceof UnknownStatus) {
            certStatusText = "unknown";
        } else if (bcCertStatus instanceof RevokedStatus) {
            certStatusText = unknownAsRevoked ? "unknown_as_revoked" : "revoked";
        } else if (bcCertStatus == null) {
            certStatusText = "good";
        } else {
            certStatusText = "should-not-happen";
        }

        if (event != null) {
            event.setLevel(AuditLevel.INFO);
            event.setStatus(AuditStatus.SUCCESSFUL);
            event.addEventData(OcspAuditConstants.NAME_status, certStatusText);
        }

        if (LOG.isDebugEnabled()) {
            StringBuilder sb = new StringBuilder(250);
            sb.append("certHashAlgo: ").append(certId.getHashAlgOID().getId()).append(", ");
            sb.append("issuerNameHash: ").append(
                    Hex.toHexString(certId.getIssuerNameHash()).toUpperCase()).append(", ");
            sb.append("issuerKeyHash: ") .append(
                    Hex.toHexString(certId.getIssuerKeyHash()) .toUpperCase()).append(", ");
            sb.append("serialNumber: ").append(LogUtil.formatCsn(certId.getSerialNumber()))
                    .append(", ");
            sb.append("certStatus: ").append(certStatusText).append(", ");
            sb.append("thisUpdate: ").append(thisUpdate).append(", ");
            sb.append("nextUpdate: ").append(nextUpdate);
            if (certHash != null) {
                sb.append(", certHash: ").append(Hex.toHexString(certHash).toUpperCase());
            }
            LOG.debug(sb.toString());
        }

        Extensions extns = null;
        if (CollectionUtil.isNonEmpty(extensions)) {
            extns = new Extensions(extensions.toArray(new Extension[0]));
        }

        builder.addResponse(certId, bcCertStatus, thisUpdate, nextUpdate, extns);
        repControl.cacheThisUpdate = Math.max(repControl.cacheThisUpdate, thisUpdate.getTime());
        if (nextUpdate != null) {
            repControl.cacheNextUpdate = Math.min(repControl.cacheNextUpdate, nextUpdate.getTime());
        }

        return null;
    }

    public HealthCheckResult healthCheck(final Responder responder) {
        HealthCheckResult result = new HealthCheckResult("OCSPResponder");
        boolean healthy = true;

        for (OcspStore store : responder.stores()) {
            boolean storeHealthy = store.isHealthy();
            healthy &= storeHealthy;

            HealthCheckResult storeHealth = new HealthCheckResult(
                    "CertStatusStore." + store.name());
            storeHealth.setHealthy(storeHealthy);
            result.addChildCheck(storeHealth);
        }

        boolean signerHealthy = responder.signer().isHealthy();
        healthy &= signerHealthy;

        HealthCheckResult signerHealth = new HealthCheckResult("Signer");
        signerHealth.setHealthy(signerHealthy);
        result.addChildCheck(signerHealth);

        result.setHealthy(healthy);
        return result;
    } // method healthCheck

    public void setAuditServiceRegister(final AuditServiceRegister auditServiceRegister) {
        this.auditServiceRegister = auditServiceRegister;
        for (OcspStore store : stores.values()) {
            store.setAuditServiceRegister(auditServiceRegister);
        }
    }

    public void setOcspStoreFactoryRegister(
            final OcspStoreFactoryRegister ocspStoreFactoryRegister) {
        this.ocspStoreFactoryRegister = ocspStoreFactoryRegister;
    }

    private void auditLogPciEvent(final boolean successful, final String eventType) {
        PciAuditEvent event = new PciAuditEvent(new Date());
        event.setUserId("OCSP-SYSTEM");
        event.setEventType(eventType);
        event.setAffectedResource("CORE");
        if (successful) {
            event.setStatus(AuditStatus.SUCCESSFUL.name());
            event.setLevel(AuditLevel.INFO);
        } else {
            event.setStatus(AuditStatus.FAILED.name());
            event.setLevel(AuditLevel.ERROR);
        }
        auditServiceRegister.getAuditService().logEvent(event);
    }

    private ResponderSigner initSigner(final SignerType signerType) throws InvalidConfException {
        X509Certificate[] explicitCertificateChain = null;

        X509Certificate explicitResponderCert = null;
        if (signerType.getCert() != null) {
            explicitResponderCert = parseCert(signerType.getCert());
        }

        if (explicitResponderCert != null) {
            Set<X509Certificate> caCerts = null;
            if (signerType.getCaCerts() != null) {
                caCerts = new HashSet<>();

                for (FileOrValueType certConf : signerType.getCaCerts().getCaCert()) {
                    caCerts.add(parseCert(certConf));
                }
            }

            explicitCertificateChain = X509Util.buildCertPath(explicitResponderCert, caCerts);
        }

        String responderSignerType = signerType.getType();
        String responderKeyConf = signerType.getKey();

        List<String> sigAlgos = signerType.getAlgorithms().getAlgorithm();
        List<ConcurrentContentSigner> singleSigners = new ArrayList<>(sigAlgos.size());
        for (String sigAlgo : sigAlgos) {
            try {
                ConcurrentContentSigner requestorSigner = securityFactory.createSigner(
                        responderSignerType,
                        new SignerConf("algo=" + sigAlgo + "," + responderKeyConf),
                        explicitCertificateChain);
                singleSigners.add(requestorSigner);
            } catch (ObjectCreationException ex) {
                throw new InvalidConfException(ex.getMessage(), ex);
            }
        }

        try {
            return new ResponderSigner(singleSigners);
        } catch (CertificateException | IOException ex) {
            throw new InvalidConfException(ex.getMessage(), ex);
        }
    } // method initSigner

    private OcspStore newStore(final StoreType conf,
            final Map<String, DataSourceWrapper> datasources, final Set<HashAlgoType> certHashAlgos)
            throws InvalidConfException {
        OcspStore store;
        String type = conf.getSource().getType();
        if ("CRL".equalsIgnoreCase(type)) {
            store = new CrlDbCertStatusStore();
        } else if ("XIPKI-DB".equals(type)) {
            store = new DbCertStatusStore();
        } else {
            try {
                store = ocspStoreFactoryRegister.newOcspStore(conf.getSource().getType());
            } catch (ObjectCreationException ex) {
                throw new InvalidConfException("ObjectCreationException of store " + conf.getName()
                        + ":" + ex.getMessage(), ex);
            }
        }
        store.setName(conf.getName());
        store.setAuditServiceRegister(auditServiceRegister);
        Integer interval = conf.getRetentionInterval();
        int retentionInterva = (interval == null) ? -1 : interval.intValue();
        store.setRetentionInterval(retentionInterva);
        store.setUnknownSerialAsGood(getBoolean(conf.isUnknownSerialAsGood(), false));

        store.setIncludeArchiveCutoff(getBoolean(conf.isIncludeArchiveCutoff(), true));
        store.setIncludeCrlId(getBoolean(conf.isIncludeCrlID(), true));

        store.setIgnoreExpiredCert(getBoolean(conf.isIgnoreExpiredCert(), true));
        store.setIgnoreNotYetValidCert(getBoolean(conf.isIgnoreNotYetValidCert(), true));

        String datasourceName = conf.getSource().getDatasource();
        DataSourceWrapper datasource = null;
        if (datasourceName != null) {
            datasource = datasources.get(datasourceName);
            if (datasource == null) {
                throw new InvalidConfException("datasource named '" + datasourceName
                        + "' not defined");
            }
        }
        try {
            store.init(conf.getSource().getConf(), datasource, certHashAlgos);
        } catch (OcspStoreException ex) {
            throw new InvalidConfException("CertStatusStoreException of store " + conf.getName()
                    + ":" + ex.getMessage(), ex);
        }

        return store;
    } // method initStore

    private OcspRespWithCacheInfo checkSignature(final OCSPReq request,
            final RequestOption requestOption, final AuditEvent event)
            throws OCSPException, CertificateParsingException, InvalidAlgorithmParameterException,
                OcspResponderException {
        if (!request.isSigned()) {
            if (!requestOption.isSignatureRequired()) {
                return null;
            }

            String message = "signature in request required";
            LOG.warn(message);
            fillAuditEvent(event, AuditLevel.INFO, AuditStatus.FAILED, message);
            return unsuccesfulOCSPRespMap.get(OcspResponseStatus.sigRequired);
        }

        if (!requestOption.isValidateSignature()) {
            return null;
        }

        X509CertificateHolder[] certs = request.getCerts();
        if (certs == null || certs.length < 1) {
            String message = "no certificate found in request to verify the signature";
            LOG.warn(message);
            fillAuditEvent(event, AuditLevel.INFO, AuditStatus.FAILED, message);
            return unsuccesfulOCSPRespMap.get(OcspResponseStatus.unauthorized);
        }

        ContentVerifierProvider cvp;
        try {
            cvp = securityFactory.getContentVerifierProvider(certs[0]);
        } catch (InvalidKeyException ex) {
            LOG.warn("securityFactory.getContentVerifierProvider, InvalidKeyException: {}",
                    ex.getMessage());
            fillAuditEvent(event, AuditLevel.ERROR, AuditStatus.FAILED, ex.getMessage());
            return unsuccesfulOCSPRespMap.get(OcspResponseStatus.unauthorized);
        }

        boolean sigValid = request.isSignatureValid(cvp);
        if (!sigValid) {
            String message = "request signature is invalid";
            LOG.warn(message);
            fillAuditEvent(event, AuditLevel.INFO, AuditStatus.FAILED, message);
            return unsuccesfulOCSPRespMap.get(OcspResponseStatus.unauthorized);
        }

        // validate the certPath
        Date referenceTime = new Date();
        if (canBuildCertpath(certs, requestOption, referenceTime)) {
            return null;
        }

        String message = "could not build certpath for the request's signer certificate";
        LOG.warn(message);
        fillAuditEvent(event, AuditLevel.INFO, AuditStatus.FAILED, message);
        return unsuccesfulOCSPRespMap.get(OcspResponseStatus.unauthorized);
    } // method checkSignature

    private static boolean canBuildCertpath(final X509CertificateHolder[] certsInReq,
            final RequestOption requestOption, final Date referenceTime) {
        X509Certificate target;
        try {
            target = X509Util.toX509Cert(certsInReq[0].toASN1Structure());
        } catch (CertificateException ex) {
            return false;
        }

        Set<Certificate> certstore = new HashSet<>();

        Set<CertWithEncoded> trustAnchors = requestOption.trustAnchors();
        for (CertWithEncoded m : trustAnchors) {
            certstore.add(m.certificate());
        }

        final int n = certsInReq.length;
        if (n > 1) {
            for (int i = 1; i < n; i++) {
                Certificate cert;
                try {
                    cert = X509Util.toX509Cert(certsInReq[i].toASN1Structure());
                } catch (CertificateException ex) {
                    continue;
                }
                certstore.add(cert);
            }
        }

        Set<X509Certificate> configuredCerts = requestOption.certs();
        if (CollectionUtil.isNonEmpty(configuredCerts)) {
            certstore.addAll(requestOption.certs());
        }

        X509Certificate[] certpath = X509Util.buildCertPath(target, certstore);
        CertpathValidationModel model = requestOption.certpathValidationModel();

        Date now = new Date();
        if (model == null || model == CertpathValidationModel.PKIX) {
            for (X509Certificate m : certpath) {
                if (m.getNotBefore().after(now) || m.getNotAfter().before(now)) {
                    return false;
                }
            }
        } else if (model == CertpathValidationModel.CHAIN) {
            // do nothing
        } else {
            throw new RuntimeException("invalid CertpathValidationModel " + model.name());
        }

        for (int i = certpath.length - 1; i >= 0; i--) {
            X509Certificate targetCert = certpath[i];
            for (CertWithEncoded m : trustAnchors) {
                if (m.equalsCert(targetCert)) {
                    return true;
                }
            }
        }

        return false;
    } // method canBuildCertpath

    private static void fillAuditEvent(final AuditEvent event, final AuditLevel level,
            final AuditStatus status, final String message) {
        if (event == null) {
            return;
        }

        if (level != null) {
            event.setLevel(level);
        }

        if (status != null) {
            event.setStatus(status);
        }

        if (message != null) {
            event.addEventData(OcspAuditConstants.NAME_message, message);
        }
    }

    private static boolean getBoolean(final Boolean bo, final boolean defaultValue) {
        return (bo == null) ? defaultValue : bo.booleanValue();
    }

    private static InputStream getInputStream(final FileOrValueType conf) throws IOException {
        return (conf.getFile() != null)
                ? new FileInputStream(IoUtil.expandFilepath(conf.getFile()))
                : new ByteArrayInputStream(conf.getValue());
    }

    private static InputStream getInputStream(final FileOrPlainValueType conf) throws IOException {
        return (conf.getFile() != null)
                ? new FileInputStream(IoUtil.expandFilepath(conf.getFile()))
                : new ByteArrayInputStream(conf.getValue().getBytes());
    }

    private static void close(final InputStream stream) {
        if (stream == null) {
            return;
        }

        try {
            stream.close();
        } catch (IOException ex) {
            LOG.warn("could not close stream: {}", ex.getMessage());
        }
    }

    private static X509Certificate parseCert(final FileOrValueType certConf)
            throws InvalidConfException {
        InputStream is = null;
        try {
            is = getInputStream(certConf);
            return X509Util.parseCert(is);
        } catch (IOException | CertificateException ex) {
            String msg = "could not parse certificate";
            if (certConf.getFile() != null) {
                msg += " from file " + certConf.getFile();
            }
            throw new InvalidConfException(msg);
        } finally {
            close(is);
        }
    }

    private static OCSPServer parseConf(final String confFilename) throws InvalidConfException {
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(ObjectFactory.class);
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            SchemaFactory schemaFact = SchemaFactory.newInstance(
                    javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI);
            Schema schema = schemaFact.newSchema(
                    OcspServer.class.getResource("/xsd/ocsp-conf.xsd"));
            unmarshaller.setSchema(schema);
            return (OCSPServer) unmarshaller.unmarshal(
                    new File(IoUtil.expandFilepath(confFilename)));
        } catch (SAXException ex) {
            throw new InvalidConfException("parse profile failed, message: " + ex.getMessage(), ex);
        } catch (JAXBException ex) {
            throw new InvalidConfException(
                    "parse profile failed, message: " + XmlUtil.getMessage(ex), ex);
        }
    }

}
