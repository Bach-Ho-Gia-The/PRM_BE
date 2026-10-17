package com.structure.springboot.features.product;

import com.structure.springboot.features.collection.repository.CollectionProductRepository;
import com.structure.springboot.features.collection.repository.CollectionRepository;
import com.structure.springboot.infrastructure.initializer.DataInitializer;
import com.structure.springboot.infrastructure.persistence.RecordStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Chỉ chạy trên DB test riêng; DataInit không được bật trên production. */
@EnabledIfEnvironmentVariable(named = "RUN_PRODUCT_DB_TESTS", matches = "true")
@SpringBootTest
@TestPropertySource(properties = {
        "app.data-init.enabled=true",
        "spring.docker.compose.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration"
})
class SunsetSeedPostgresIntegrationTest {

    @Autowired private DataInitializer initializer;
    @Autowired private CollectionRepository collections;
    @Autowired private CollectionProductRepository links;

    @Test
    void startupCreatesPublishedRealSunsetCollectionOnlyOnce() {
        var sunset = collections.findBySlug("sunset").orElseThrow();
        assertEquals(RecordStatus.ACTIVE, sunset.getStatus());
        assertEquals("La Boutique Sunset", sunset.getCollectionName());
        var members = links.findByCollectionIdOrderBySortOrderAscIdAsc(sunset.getId());
        assertEquals(List.of("LB-001", "LB-002", "LB-003"),
                members.stream().map(link -> link.getProduct().getSku()).toList());
        assertEquals(List.of(0, 1, 2), members.stream().map(link -> link.getSortOrder()).toList());
        long version = sunset.getVersion();

        initializer.run();
        var after = collections.findBySlug("sunset").orElseThrow();
        assertEquals(sunset.getId(), after.getId());
        assertEquals(version, after.getVersion());
        assertEquals(3L, links.countByCollectionId(after.getId()));
    }
}
