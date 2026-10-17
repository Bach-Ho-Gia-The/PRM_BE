package com.structure.springboot.features.category.specification;

import com.structure.springboot.features.category.entity.CategoryEntity;
import com.structure.springboot.infrastructure.persistence.RecordStatus;
import org.springframework.data.jpa.domain.Specification;

/** Category public chỉ gồm ACTIVE; khác Product legacy null và không dùng visible() chung. */
public final class CategorySpecifications {

    private CategorySpecifications() {
    }

    public static Specification<CategoryEntity> active() {
        return (root, query, cb) -> cb.equal(root.get("status"), RecordStatus.ACTIVE);
    }
}
