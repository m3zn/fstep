package com.cgi.eoss.fstep.persistence.service;

import com.cgi.eoss.fstep.model.QWallet;
import com.cgi.eoss.fstep.model.User;
import com.cgi.eoss.fstep.model.Wallet;
import com.cgi.eoss.fstep.model.WalletTransaction;
import com.cgi.eoss.fstep.persistence.dao.FstepEntityDao;
import com.cgi.eoss.fstep.persistence.dao.WalletDao;
import com.cgi.eoss.fstep.persistence.dao.WalletTransactionDao;
import com.querydsl.core.types.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional(readOnly = true)
public class JpaWalletDataService extends AbstractJpaDataService<Wallet> implements WalletDataService {

    private final WalletDao dao;

    private final WalletTransactionDao transactionDao;

    @Autowired
    public JpaWalletDataService(WalletDao WalletDao, WalletTransactionDao transactionDao) {
        this.dao = WalletDao;
        this.transactionDao = transactionDao;
    }

    @Override
    FstepEntityDao<Wallet> getDao() {
        return dao;
    }

    @Override
    Predicate getUniquePredicate(Wallet entity) {
        return QWallet.wallet.owner.eq(entity.getOwner());
    }

    @Override
    public Wallet findByOwner(User user) {
        return dao.findOneByOwner(user);
    }

    @Override
    @Transactional
    public void transact(WalletTransaction transaction) {
        Wallet wallet = transaction.getWallet();
        wallet.getTransactions().add(transactionDao.save(transaction));
        int balanceChange = transaction.getBalanceChange();
        wallet.setBalance(wallet.getBalance() + balanceChange);
    }

    @Override
    @Transactional
    public void creditBalance(Wallet wallet, int amount) {
        transact(WalletTransaction.builder()
                .wallet(wallet)
                .balanceChange(amount)
                .transactionTime(LocalDateTime.now())
                .type(WalletTransaction.Type.CREDIT)
                .associatedId(null)
                .build());
    }

}
