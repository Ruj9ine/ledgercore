package com.ledgercore.reconciliation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/**
 * Re-derives the books from scratch and reports every disagreement. It only reads, and it sees one consistent
 * snapshot, so it is safe to run while money is moving. All SQL is fixed text with no user input.
 */
@Service
public class ReconciliationService {

    static final int MAX_SAMPLES = 50;

    private static final String SIGNED_AMOUNT = "case direction when 'CREDIT' then amount else -amount end";

    private static final String WALLET_BALANCE_PROBLEMS = """
            select 'wallet ' || w.id || ': stored balance ' || w.balance || ', ledger total ' || coalesce(l.net, 0) as problem
            from wallets w
            left join (
                select wallet_id, sum(%s) as net
                from ledger_entries
                group by wallet_id
            ) l on l.wallet_id = w.id
            where w.balance <> coalesce(l.net, 0)
            order by w.id
            """.formatted(SIGNED_AMOUNT);

    private static final String CHAIN_PROBLEMS = """
            select 'wallet ' || t.wallet_id || ', entry ' || t.id || ': recorded balance ' || t.balance_after
                   || ', ledger running total ' || t.expected as problem
            from (
                select id, wallet_id, balance_after,
                       sum(%s) over (partition by wallet_id order by created_at, id) as expected
                from ledger_entries
            ) t
            where t.balance_after <> t.expected
            order by t.wallet_id, t.id
            """.formatted(SIGNED_AMOUNT);

    private static final String SHAPE_PROBLEMS = """
            select 'transaction ' || t.reference || ' (' || t.type || '): its ledger entries do not match what it claims' as problem
            from transactions t
            where t.status = 'COMPLETED'
              and not (
                case t.type
                    when 'DEPOSIT' then
                        (select count(*) from ledger_entries e where e.transaction_id = t.id) = 1
                        and exists (select 1 from ledger_entries e where e.transaction_id = t.id
                                    and e.direction = 'CREDIT' and e.wallet_id = t.destination_wallet_id
                                    and e.amount = t.amount)
                    when 'WITHDRAWAL' then
                        (select count(*) from ledger_entries e where e.transaction_id = t.id) = 1
                        and exists (select 1 from ledger_entries e where e.transaction_id = t.id
                                    and e.direction = 'DEBIT' and e.wallet_id = t.source_wallet_id
                                    and e.amount = t.amount)
                    when 'TRANSFER' then
                        (select count(*) from ledger_entries e where e.transaction_id = t.id) = 2
                        and exists (select 1 from ledger_entries e where e.transaction_id = t.id
                                    and e.direction = 'DEBIT' and e.wallet_id = t.source_wallet_id
                                    and e.amount = t.amount)
                        and exists (select 1 from ledger_entries e where e.transaction_id = t.id
                                    and e.direction = 'CREDIT' and e.wallet_id = t.destination_wallet_id
                                    and e.amount = t.amount)
                    else false
                end)
            order by t.created_at, t.id
            """;

    private static final String SUPPLY_PROBLEMS = """
            select 'currency ' || c.currency || ': wallets hold ' || c.held
                   || ', deposits minus withdrawals is ' || coalesce(f.flow, 0) as problem
            from (select currency, sum(balance) as held from wallets group by currency) c
            left join (
                select currency,
                       sum(case type when 'DEPOSIT' then amount when 'WITHDRAWAL' then -amount else 0 end) as flow
                from transactions
                where status = 'COMPLETED'
                group by currency
            ) f on f.currency = c.currency
            where c.held <> coalesce(f.flow, 0)
            order by c.currency
            """;

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public ReconciliationService(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    // REPEATABLE_READ gives every query below the same snapshot of the database, so a transfer committing
    // halfway through cannot make one check disagree with another.
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ReconciliationReport run() {
        List<CheckResult> checks = List.of(
                check("wallet-balance-matches-ledger",
                        "Every wallet's stored balance equals the sum of its ledger entries.",
                        WALLET_BALANCE_PROBLEMS),
                check("ledger-balance-chain",
                        "Every ledger entry's recorded balance equals the running total up to that entry.",
                        CHAIN_PROBLEMS),
                check("transaction-ledger-shape",
                        "Every transaction has exactly the ledger entries its type requires.",
                        SHAPE_PROBLEMS),
                check("money-supply-per-currency",
                        "In each currency, wallets hold exactly deposits minus withdrawals.",
                        SUPPLY_PROBLEMS));
        boolean balanced = checks.stream().allMatch(CheckResult::passed);
        return new ReconciliationReport(clock.instant(), balanced, checks);
    }

    private CheckResult check(String name, String description, String problemQuery) {
        Long count = jdbcTemplate.queryForObject("select count(*) from (" + problemQuery + ") q", Long.class);
        List<String> samples = jdbcTemplate.queryForList(problemQuery + " limit " + MAX_SAMPLES, String.class);
        long problems = count == null ? 0 : count;
        return new CheckResult(name, description, problems == 0, problems, samples);
    }
}