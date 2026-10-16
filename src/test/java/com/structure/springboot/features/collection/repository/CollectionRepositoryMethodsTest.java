package com.structure.springboot.features.collection.repository;

import com.structure.springboot.features.collection.entity.CollectionEntity;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.parser.PartTree;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CollectionRepositoryMethodsTest {

    @Test
    void writeQueryUsesRowLockAndResolvableDerivedMethod() throws NoSuchMethodException {
        assertDoesNotThrow(() -> new PartTree("findWithLockById", CollectionEntity.class));
        Lock lock = CollectionRepository.class
                .getMethod("findWithLockById", UUID.class)
                .getAnnotation(Lock.class);
        assertEquals(LockModeType.PESSIMISTIC_WRITE, lock.value());
    }
}
