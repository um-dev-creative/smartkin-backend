package com.umdc.smartkin.constant;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SmartKinAppConstantsTest {

    @Test
    void exposesThePackagesScannedByJpa() {
        assertEquals("com.umdc.smartkin.jpa.domain", SmartKinAppConstants.ENTITY_PACKAGE);
        assertEquals("com.umdc.smartkin.jpa.repository", SmartKinAppConstants.REPOSITORY_PACKAGE);
    }

    @Test
    void refusesInstantiation() throws NoSuchMethodException {
        var constructor = SmartKinAppConstants.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        var thrown = assertThrows(InvocationTargetException.class, constructor::newInstance);

        assertInstanceOf(IllegalStateException.class, thrown.getCause());
    }
}
