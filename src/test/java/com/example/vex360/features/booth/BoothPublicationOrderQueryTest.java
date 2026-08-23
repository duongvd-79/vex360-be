package com.example.vex360.features.booth;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import com.example.vex360.features.booth.repositories.BoothRepository;

class BoothPublicationOrderQueryTest {

    @Test
    void hallSlotQueryUsesFirstApprovalAndCurrentVisibility() throws NoSuchMethodException {
        Query query = BoothRepository.class
                .getMethod("findPublishedBoothsByFirstApproval", UUID.class)
                .getAnnotation(Query.class);
        String sql = query.value();

        assertTrue(sql.contains("MIN(reviewed_at)"), sql);
        assertTrue(sql.contains("exh.uuid = :exhibitionUuid"), sql);
        assertTrue(sql.contains("b.status = 'PUBLISHED'"), sql);
        assertTrue(sql.contains("b.is_template = false"), sql);
        assertTrue(sql.contains("ORDER BY approval.first_approved_at ASC, b.id ASC"), sql);
    }
}
