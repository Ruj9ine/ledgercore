package com.ledgercore.wallet;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WalletRepository extends JpaRepository<Wallet, UUID>, JpaSpecificationExecutor<Wallet> {

    List<Wallet> findByUserIdOrderByCreatedAtAsc(UUID userId);

    Optional<Wallet> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserIdAndCurrency(UUID userId, SupportedCurrency currency);

    /**
     * Loads the caller's wallet and takes a database row lock on it until the surrounding transaction ends.
     * Any other transaction that asks for the same wallet this way waits here, so balance changes on one
     * wallet happen strictly one after another.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.id = :id and w.userId = :userId")
    Optional<Wallet> findByIdAndUserIdForUpdate(@Param("id") UUID id, @Param("userId") UUID userId);

     /** A cheap ownership check that does not load the wallet into the persistence context. */
    boolean existsByIdAndUserId(UUID id, UUID userId);

     /** Loads any wallet by id and takes a row lock on it. Callers must verify ownership themselves. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.id = :id")
    Optional<Wallet> findByIdForUpdate(@Param("id") UUID id);


}
