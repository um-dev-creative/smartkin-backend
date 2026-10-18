package com.umdc.smartkin.jpa;

import com.umdc.smartkin.config.DataBaseConfig;
import com.umdc.smartkin.jpa.repository.ChildRepository;
import com.umdc.smartkin.jpa.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.EntityType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/// Boots Hibernate against a real PostgreSQL to prove the JPA model and the repositories wire up.
/// The schema is not created here: its DDL belongs to the Flyway baseline (task DB-3), which will add CRUD round trips.
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = JpaMappingContainerTest.JpaTestApplication.class)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=none")
class JpaMappingContainerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(DataBaseConfig.class)
    static class JpaTestApplication {
    }

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ChildRepository childRepository;

    @Test
    void registersTheFourteenEntitiesOfTheDomainModel() {
        Set<String> names = entityManager.getMetamodel().getEntities().stream()
                .map(EntityType::getName).collect(Collectors.toSet());

        assertEquals(14, names.size());
        assertEquals(Set.of("AchievementEntity", "ActivityEntity", "ActivityProgressEntity", "AssignedActivityEntity",
                "ChildAchievementEntity", "ChildEntity", "ChildRewardEntity", "GoalEntity", "NotificationEntity",
                "ParentEntity", "PointEntity", "RewardEntity", "TopicEntity", "UserEntity"), names);
    }

    @Test
    void wiresTheRepositories() {
        assertNotNull(userRepository);
        assertNotNull(childRepository);
    }
}
