// Copyright 2022 Intel Corporation
// SPDX-License-Identifier: Apache 2.0

package org.fidoalliance.fdo.protocol.db;

import java.io.IOException;
import java.security.PublicKey;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import org.bouncycastle.util.encoders.Hex;
import org.fidoalliance.fdo.protocol.Config;
import org.fidoalliance.fdo.protocol.Mapper;
import org.fidoalliance.fdo.protocol.dispatch.CryptoService;
import org.fidoalliance.fdo.protocol.dispatch.RendezvousAcceptFunction;
import org.fidoalliance.fdo.protocol.entity.AllowDenyList;
import org.fidoalliance.fdo.protocol.entity.ProtocolSession;
import org.fidoalliance.fdo.protocol.message.CoseSign1;
import org.fidoalliance.fdo.protocol.message.Hash;
import org.fidoalliance.fdo.protocol.message.HashType;
import org.fidoalliance.fdo.protocol.message.OwnershipVoucher;
import org.fidoalliance.fdo.protocol.message.OwnershipVoucherEntries;
import org.fidoalliance.fdo.protocol.message.OwnershipVoucherEntryPayload;
import org.fidoalliance.fdo.protocol.message.OwnershipVoucherHeader;
import org.fidoalliance.fdo.protocol.message.To0OwnerSign;
import org.hibernate.Session;
import org.hibernate.Transaction;

public class TrustedRendezvousAcceptFunction implements RendezvousAcceptFunction {

  private List<String> getKeyHashes(OwnershipVoucher voucher) throws IOException {

    List<String> hashList = new ArrayList<>();
    CryptoService cs = Config.getWorker(CryptoService.class);

    OwnershipVoucherEntries entries = voucher.getEntries();
    for (CoseSign1 sign1 : entries) {
      OwnershipVoucherEntryPayload payload =
          Mapper.INSTANCE.readValue(sign1.getPayload(), OwnershipVoucherEntryPayload.class);
      PublicKey publicKey = cs.decodeKey(payload.getOwnerPublicKey());
      Hash hash = cs.hash(HashType.SHA384,publicKey.getEncoded());
      hashList.add(Hex.toHexString(hash.getHashValue()));
    }

    return hashList;
  }

  @Override
  public Boolean apply(To0OwnerSign to0OwnerSign) throws IOException {

    boolean trusted = false;
    Session session = HibernateUtil.getSessionFactory().openSession();
    try {
      Transaction trans = session.beginTransaction();
      CriteriaBuilder cb = session.getCriteriaBuilder();
      CriteriaQuery<AllowDenyList> cq = cb.createQuery(AllowDenyList.class);
      Root<AllowDenyList> rootEntry = cq.from(AllowDenyList.class);
      CriteriaQuery<AllowDenyList> all = cq.select(rootEntry);

      TypedQuery<AllowDenyList> allQuery = session.createQuery(all);

      OwnershipVoucherHeader header = Mapper.INSTANCE.readValue(
          to0OwnerSign.getTo0d().getVoucher().getHeader(), OwnershipVoucherHeader.class);
      String uuid = header.getGuid().toString();
      List<String> hashes = getKeyHashes(to0OwnerSign.getTo0d().getVoucher());

      //look for allowed
      for (AllowDenyList adList : allQuery.getResultList()) {

        for (String hash : hashes) {
          if (adList.getHash().equals(hash) && adList.isAllowed()) {
            trusted = true;
            break;
          }
        }
      }

      //look for denied
      for (AllowDenyList adList : allQuery.getResultList()) {
        if (adList.getHash().equals(uuid) && !adList.isAllowed()) {
          trusted = false;
          break;
        }


        for (String hash : hashes) {
          if (adList.getHash().equals(hash) && !adList.isAllowed()) {
            trusted = false;
            break;
          }
        }
      }
      trans.commit();
    } finally {
      session.close();
    }

    return trusted;
  }
}
