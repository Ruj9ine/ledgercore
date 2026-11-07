package com.ledgercore.admin;

import com.ledgercore.admin.dto.AdminTransactionResponse;
import com.ledgercore.common.PageResponse;
import com.ledgercore.transaction.WalletTransaction;
import com.ledgercore.transaction.WalletTransactionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminTransactionService {

    private final WalletTransactionRepository transactionRepository;

    public AdminTransactionService(WalletTransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminTransactionResponse> list(AdminTransactionFilter filter) {
        Pageable pageable = PageRequest.of(filter.page(), filter.size(),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<WalletTransaction> transactions = transactionRepository.findAll(
                TransactionSpecifications.matching(filter), pageable);
        return PageResponse.from(transactions.map(AdminTransactionResponse::from));
    }
}
