package com.structure.springboot.features.product.entity;

import com.structure.springboot.features.collection.entity.CollectionEntity;
import com.structure.springboot.features.profile.entity.ProfileEntity;
import jakarta.persistence.Column;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManagedImageColumnsTest {

    @Test
    void eachExistingImageUrlHasOptionalBackendOnlyPublicId() throws NoSuchFieldException {
        assertOptionalColumn(ProductEntity.class, "thumbnailPublicId", "thumbnail_public_id");
        assertOptionalColumn(ProductImageEntity.class, "publicId", "public_id");
        assertOptionalColumn(CollectionEntity.class, "coverImagePublicId", "cover_image_public_id");
        assertOptionalColumn(ProfileEntity.class, "avatarPublicId", "avatar_public_id");
    }

    private void assertOptionalColumn(Class<?> entity, String field, String columnName) throws NoSuchFieldException {
        Column column = entity.getDeclaredField(field).getAnnotation(Column.class);
        assertEquals(columnName, column.name());
        assertEquals(500, column.length());
        assertTrue(column.nullable()); // Existing seed/Google/external rows are not backfilled/deleted.
    }
}
