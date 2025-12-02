package fr.tp.inf112.projects.robotsim.model;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;

import fr.tp.inf112.projects.robotsim.model.shapes.PositionedShape;
import fr.tp.inf112.projects.robotsim.model.shapes.RectangularShape;

public class TestRobotSimSerializationJSON {

    private static final Logger logger = LoggerFactory.getLogger(TestRobotSimSerializationJSON.class);

    private ObjectMapper objectMapper;
    private Factory factory;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        PolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType(PositionedShape.class.getPackageName())
                .allowIfSubType(Component.class.getPackageName())
                .allowIfSubType("org.jgrapht.graph") // Assuming BasicVertex is in this package
                .allowIfSubType(ArrayList.class.getName())
                .allowIfSubType(LinkedHashSet.class.getName())
                .build();
        objectMapper.activateDefaultTyping(typeValidator, ObjectMapper.DefaultTyping.NON_FINAL);

        factory = new Factory(800, 600, "Test Factory");
        factory.setId("factory-01");

        Robot robot1 = new Robot(factory, new RectangularShape(10, 10, 20, 20), "Robot-1", 10);
        robot1.setId("robot-01");

        Robot robot2 = new Robot(factory, new RectangularShape(50, 50, 20, 20), "Robot-2", 10);
        robot2.setId("robot-02");
    }

    @Test
    void testSerialization() {
        try {
            String jsonString = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(factory);
            logger.info("Serialized Factory:\n{}", jsonString);

            Factory deserializedFactory = objectMapper.readValue(jsonString, Factory.class);
            assertNotNull(deserializedFactory);
            logger.info("Deserialized Factory toString():\n{}", deserializedFactory.toString());

            assertNotNull(deserializedFactory.getComponents());

        } catch (Exception e) {
            logger.error("Error during serialization/deserialization test", e);
        }
    }
}
