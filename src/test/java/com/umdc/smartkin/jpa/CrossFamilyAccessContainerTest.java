package com.umdc.smartkin.jpa;

import com.umdc.smartkin.api.v1.service.ChildServiceImpl;
import com.umdc.smartkin.api.v1.service.ParentServiceImpl;
import com.umdc.smartkin.api.v1.to.ChildRequest;
import com.umdc.smartkin.api.v1.to.ChildUpdateRequest;
import com.umdc.smartkin.api.v1.to.ParentProfileRequest;
import com.umdc.smartkin.client.backbone.BackboneClient;
import com.umdc.smartkin.config.DataBaseConfig;
import com.umdc.smartkin.jpa.domain.ChildEntity;
import com.umdc.smartkin.jpa.domain.ParentEntity;
import com.umdc.smartkin.jpa.repository.ChildRepository;
import com.umdc.smartkin.jpa.repository.ParentRepository;
import com.umdc.smartkin.mapper.ChildMapperImpl;
import com.umdc.smartkin.mapper.ParentMapperImpl;
import com.umdc.smartkin.security.access.AccessPolicy;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.UUID;

import static com.umdc.smartkin.support.TestData.tokenFor;
import static org.junit.jupiter.api.Assertions.assertEquals;

/// Two families on a real PostgreSQL: whatever one family does through the services never reaches the other.
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = CrossFamilyAccessContainerTest.AccessTestApplication.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.default-schema=smartkin",
        "spring.flyway.schemas=smartkin",
        "spring.flyway.create-schemas=true",
        "spring.flyway.url=",
        "spring.flyway.user=",
        "spring.flyway.password="
})
class CrossFamilyAccessContainerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({DataBaseConfig.class, AccessPolicy.class, ChildServiceImpl.class, ParentServiceImpl.class,
            ChildMapperImpl.class, ParentMapperImpl.class})
    static class AccessTestApplication {
    }

    @Autowired
    private ChildServiceImpl children;
    @Autowired
    private ParentServiceImpl parents;
    @Autowired
    private ParentRepository parentRepository;
    @Autowired
    private ChildRepository childRepository;
    @Autowired
    private EntityManager entityManager;
    @MockitoBean
    private BackboneClient backboneClient;

    private UUID parentA;
    private UUID parentB;
    private UUID childA1;
    private UUID childA2;
    private UUID childB1;
    private final UUID stranger = UUID.randomUUID();

    private UUID newParent() {
        var parent = new ParentEntity();
        parent.setId(UUID.randomUUID());
        return parentRepository.save(parent).getId();
    }

    private UUID newChild(UUID parentId) {
        var child = new ChildEntity();
        child.setId(UUID.randomUUID());
        child.setParent(parentRepository.getReferenceById(parentId));
        child.setTotalPoints(0);
        child.setGradeLevel("3");
        return childRepository.save(child).getId();
    }

    @BeforeEach
    void families() {
        parentA = newParent();
        parentB = newParent();
        childA1 = newChild(parentA);
        childA2 = newChild(parentA);
        childB1 = newChild(parentB);
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void aParentListsOnlyTheirOwnChildren() {
        var listedA = children.list(tokenFor(parentA)).getBody();
        var listedB = children.list(tokenFor(parentB)).getBody();

        assertEquals(List.of(childA1, childA2).stream().sorted().toList(), listedA.stream().map(c -> c.childId()).sorted().toList());
        assertEquals(List.of(childB1), listedB.stream().map(c -> c.childId()).toList());
    }

    @Test
    void aParentCannotReadAnotherFamilysChild() {
        assertEquals(HttpStatus.OK, children.get(tokenFor(parentA), childA1).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, children.get(tokenFor(parentA), childB1).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, children.get(tokenFor(parentB), childA1).getStatusCode());
    }

    @Test
    void aParentCannotChangeAnotherFamilysChild() {
        var attempt = children.update(tokenFor(parentA), childB1, new ChildUpdateRequest("hacked", null));
        entityManager.flush();
        entityManager.clear();

        assertEquals(HttpStatus.NOT_FOUND, attempt.getStatusCode());
        assertEquals("3", childRepository.findById(childB1).orElseThrow().getGradeLevel());
        assertEquals(HttpStatus.OK, children.update(tokenFor(parentB), childB1, new ChildUpdateRequest("4", null)).getStatusCode());
    }

    @Test
    void aChildSeesOnlyThemselvesAndChangesNobody() {
        assertEquals(HttpStatus.OK, children.get(tokenFor(childA1), childA1).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, children.get(tokenFor(childA1), childA2).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, children.get(tokenFor(childA1), childB1).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, children.update(tokenFor(childA1), childA1, new ChildUpdateRequest("9", null)).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, children.list(tokenFor(childA1)).getStatusCode());
    }

    @Test
    void aUserWithoutProfileReachesNothing() {
        assertEquals(HttpStatus.FORBIDDEN, children.get(tokenFor(stranger), childA1).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, children.update(tokenFor(stranger), childA1, new ChildUpdateRequest("9", null)).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, children.list(tokenFor(stranger)).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, children.link(tokenFor(stranger), new ChildRequest(UUID.randomUUID(), null, null)).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, parents.getMe(tokenFor(stranger)).getStatusCode());
    }

    @Test
    void aFamilysChildCannotBeLinkedByAnotherParent() {
        var response = children.link(tokenFor(parentB), new ChildRequest(childA1, null, null));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(parentA, childRepository.findById(childA1).orElseThrow().getParent().getId());
    }

    @Test
    void aChildCannotBecomeAParentAndAParentCannotBecomeAChild() {
        assertEquals(HttpStatus.FORBIDDEN, parents.putMe(tokenFor(childA1), new ParentProfileRequest(null, null, null)).getStatusCode());
        assertEquals(HttpStatus.CONFLICT, children.link(tokenFor(parentA), new ChildRequest(parentB, null, null)).getStatusCode());
    }
}
