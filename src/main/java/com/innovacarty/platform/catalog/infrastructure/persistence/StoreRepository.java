package com.innovacarty.platform.catalog.infrastructure.persistence;

import com.innovacarty.platform.catalog.domain.model.Store;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StoreRepository extends JpaRepository<Store, Long> {
}
