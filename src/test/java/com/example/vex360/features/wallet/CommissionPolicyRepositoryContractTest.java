package com.example.vex360.features.wallet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.EntityGraph;

import com.example.vex360.features.wallet.repositories.CommissionPolicyRepository;

class CommissionPolicyRepositoryContractTest {

    @Test
    void findAllFetchesCreatedByForDetachedResponseMapping() throws NoSuchMethodException {
        Method findAll = CommissionPolicyRepository.class.getMethod("findAll");
        EntityGraph entityGraph = findAll.getAnnotation(EntityGraph.class);

        assertNotNull(entityGraph);
        assertArrayEquals(new String[] { "createdBy" }, entityGraph.attributePaths());
    }
}
