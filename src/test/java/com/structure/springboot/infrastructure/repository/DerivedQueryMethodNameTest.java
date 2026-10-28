package com.structure.springboot.infrastructure.repository;

import com.structure.springboot.features.account.repository.AccountRepository;
import com.structure.springboot.features.category.repository.CategoryRepository;
import com.structure.springboot.features.chat.repository.ChatMessageRepository;
import com.structure.springboot.features.chat.repository.ConversationRepository;
import com.structure.springboot.features.collection.repository.CollectionProductRepository;
import com.structure.springboot.features.collection.repository.CollectionRepository;
import com.structure.springboot.features.notification.repository.NotificationRepository;
import com.structure.springboot.features.order.repository.OrderItemRepository;
import com.structure.springboot.features.order.repository.OrderRepository;
import com.structure.springboot.features.payment.repository.PaymentRepository;
import com.structure.springboot.features.product.repository.ProductRepository;
import com.structure.springboot.features.product.repository.ProductVariantRepository;
import com.structure.springboot.features.profile.repository.ProfileRepository;
import com.structure.springboot.features.role.repository.RoleRepository;
import com.structure.springboot.features.store.repository.StoreRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.parser.PartTree;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Guards every Spring Data derived query method in this project against the class of bug
 * that once prevented the whole application from starting:
 *
 * <pre>
 * OrderRepository.findByOrderNumberAndAccountIdForUpdate(String, UUID)
 *   -&gt; PropertyReferenceException: No property 'forUpdate' found for type 'UUID';
 *      Traversed path: OrderEntity.account.id
 * </pre>
 *
 * <p>Spring Data only skips the descriptive text sitting <em>between</em> the subject keyword
 * ({@code find}/{@code read}/{@code query}/{@code get}/{@code count}/{@code exists}/{@code delete})
 * and the {@code By} keyword. Everything after {@code By} is parsed as a property path, so any
 * discriminating word must come <strong>before</strong> {@code By}: {@code findForUpdateById}
 * is valid, {@code findByIdForUpdate} is not.</p>
 *
 * <p>This test runs with no database and no Spring context: it asks the very same parser Spring
 * Data Boot uses ({@link PartTree}) to resolve each method name against its domain entity. A
 * method that the parser cannot resolve fails here at build time instead of at application
 * start-up. The Postgres integration tests are skipped in this sandbox, which is exactly why the
 * original bug slipped through a green build.</p>
 */
class DerivedQueryMethodNameTest {

    private static final List<Class<?>> REPOSITORIES = List.of(
            AccountRepository.class,
            CategoryRepository.class,
            ChatMessageRepository.class,
            ConversationRepository.class,
            CollectionProductRepository.class,
            CollectionRepository.class,
            NotificationRepository.class,
            OrderItemRepository.class,
            OrderRepository.class,
            PaymentRepository.class,
            ProductRepository.class,
            ProductVariantRepository.class,
            ProfileRepository.class,
            RoleRepository.class,
            StoreRepository.class
    );

    @Test
    void everyDerivedQueryMethodNameResolvesAgainstItsEntity() {
        List<String> broken = new ArrayList<>();
        int checked = 0;

        for (Class<?> repository : REPOSITORIES) {
            Class<?> domain = domainTypeOf(repository);
            for (Method method : repository.getDeclaredMethods()) {
                if (Modifier.isStatic(method.getModifiers())
                        || method.isDefault()
                        || method.isSynthetic()
                        || method.isBridge()
                        || method.isAnnotationPresent(Query.class)
                        || overridesFrameworkMethod(repository, method)) {
                    continue;
                }
                checked++;
                try {
                    new PartTree(method.getName(), domain);
                } catch (RuntimeException ex) {
                    broken.add(repository.getSimpleName() + "." + method.getName()
                            + " -> " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
                }
            }
        }

        assertTrue(checked > 0, "No derived query methods were inspected; the guard is not working");
        if (!broken.isEmpty()) {
            fail("Spring Data cannot resolve these derived query method names, "
                    + "which makes the application fail at start-up:\n  "
                    + String.join("\n  ", broken));
        }
    }

    /**
     * Methods re-declared only to attach annotations (e.g. {@code findAll} with an
     * {@code @EntityGraph}) already exist on a Spring Data base interface and are implemented by
     * the framework, so they are not derived queries and carry no property path to resolve.
     */
    private static boolean overridesFrameworkMethod(Class<?> repository, Method method) {
        for (Class<?> base : new Class<?>[]{Repository.class, CrudRepository.class, PagingAndSortingRepository.class}) {
            try {
                base.getMethod(method.getName(), method.getParameterTypes());
                return true;
            } catch (NoSuchMethodException ignored) {
                // keep looking
            }
        }
        return false;
    }

    /**
     * Walks up the interface hierarchy of a repository and returns its {@code JpaRepository}
     * domain type, i.e. the second type argument of {@code JpaRepository<T, ID>}.
     */
    private static Class<?> domainTypeOf(Class<?> repository) {
        return domainTypeOf(repository, new ArrayList<>());
    }

    private static Class<?> domainTypeOf(Class<?> type, List<Class<?>> visited) {
        if (type == null || visited.contains(type)) {
            return null;
        }
        visited.add(type);
        for (java.lang.reflect.Type iface : type.getGenericInterfaces()) {
            java.lang.reflect.ParameterizedType parameterized =
                    (java.lang.reflect.ParameterizedType) iface;
            Class<?> raw = (Class<?>) parameterized.getRawType();
            if ("org.springframework.data.jpa.repository.JpaRepository".equals(raw.getName())) {
                return (Class<?>) parameterized.getActualTypeArguments()[0];
            }
            Class<?> found = domainTypeOf(raw, visited);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
